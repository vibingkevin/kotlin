/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.native.executors

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [Executor] that runs a Linux kexe in a persistent Docker container.
 *
 * - **Image.** Ubuntu 24.04 ([DEFAULT_IMAGE]). `linux_x64` → `--platform linux/amd64`,
 *       `linux_arm64` → `linux/arm64`.
 * - **Lifecycle.** First [execute] starts `docker run -d --rm --init … sleep` for
 *       [CONTAINER_TTL_SECONDS] (16h); later cases reuse it via `docker exec`.
 *       One container per target.
 * - **JVM crash.** Shutdown hook runs `docker rm -f`. SIGKILL skips the hook;
 *      `--rm` + TTL still drops the container.
 * - **Mount.** Test worker `user.dir` and `java.io.tmpdir`. On macOS the container
 *      path is the host path. On Windows, `C:\...` is mounted at `/c/...`.
 * - **Permissions.** macOS relies on host bind execute bits. Windows runs
 *      `chmod +x` in the container; NTFS mounts may not expose that bit.
 * - **Exec.** Each case is `docker exec -i`. stdin / stdout / stderr / exit code
 *      pass through [HostExecutor] unchanged. Test env via `-e`. Windows paths
 *      under a mounted directory are rewritten to the container path.
 * - **Timeout.** Outer: [HostExecutor] kills the CLI at [ExecuteRequest.timeout]
 *      (`exitCode == null`), which does not kill the in-container process.
 *   Inner: `timeout -s KILL` at `request.timeout + 30s` so happy path timeout is still
 *      visible to executor.
 * - **Availability.** [availableFor]: mapped Linux target, host ≠ target,
 *      `docker` on PATH. Daemon is not probed.
 */
class DockerExecutor(
    private val target: KonanTarget,
    private val image: String = defaultImage(),
    private val platform: String = platformFor(target) ?: error("DockerExecutor does not support $target"),
    private val hostExecutor: Executor = HostExecutor(),
) : Executor {
    private val logger = Logger.getLogger(DockerExecutor::class.java.name)
    private val lock = Any()
    private val dockerAbsolutePath: String
        get() = dockerOnPath ?: error("docker not found on PATH")

    @Volatile
    private var containerId: String? = null

    override fun execute(request: ExecuteRequest): ExecuteResponse {
        val exeFile = File(request.executableAbsolutePath).canonicalFile
        val workDir = (request.workingDirectory ?: exeFile.parentFile).canonicalFile
        val mounts = bindMountRoots()
        val containerWorkDir = guestPath(workDir, mounts)
        val containerExe = guestPath(exeFile, mounts)
        val cid = ensureContainer()
        if (windowsHost) {
            // NTFS bind mounts often hide the Unix execute bit from Linux.
            hostExecutor.runProcess(dockerAbsolutePath, "exec", cid, "chmod", "+x", containerExe) {
                timeout = CONTAINER_START_TIMEOUT
            }
        }

        val dockerArgs = mutableListOf(
            "exec",
            "-i",
            "-w", containerWorkDir,
        )
        for ((key, value) in request.environment) {
            dockerArgs += listOf("-e", "$key=${mapGuestText(value, mounts)}")
        }
        dockerArgs += cid
        // Coreutils `timeout` is a safety net: HostExecutor killing the CLI leaves the
        // in-container process running. Fire after [request.timeout] plus grace so the
        // outer wait still observes a timeout (`exitCode == null`) on the happy path.
        if (!request.timeout.isInfinite()) {
            val innerSeconds = (request.timeout + INNER_TIMEOUT_GRACE)
                .inWholeSeconds
                .coerceAtLeast(1)
            dockerArgs += listOf("timeout", "-s", "KILL", innerSeconds.toString())
        }
        dockerArgs += containerExe
        dockerArgs += request.args.map { mapGuestText(it, mounts) }

        logger.info(
            "DockerExecutor $target ($platform): $dockerAbsolutePath ${dockerArgs.joinToString(" ")}"
        )

        // Fresh request: do not mutate [request.args] (data-class copy shares the MutableList).
        // Host env for the docker CLI stays inherited; test env is passed into the container via -e.
        return hostExecutor.execute(
            ExecuteRequest(
                executableAbsolutePath = dockerAbsolutePath,
                args = dockerArgs,
                workingDirectory = workDir,
                stdin = request.stdin,
                stdout = request.stdout,
                stderr = request.stderr,
                timeout = request.timeout,
            )
        )
    }

    private fun ensureContainer(): String {
        containerId?.let { return it }
        synchronized(lock) {
            containerId?.let { return it }
            val id = startContainer()
            containerId = id
            Runtime.getRuntime().addShutdownHook(Thread({ destroyContainer(id) }, "DockerExecutor-rm-$id"))
            return id
        }
    }

    private fun startContainer(): String {
        val mounts = bindMountRoots()
        val name = "kn-exec-${System.nanoTime().toString(36)}"
        val args = mutableListOf(
            "run", "-d", "--rm", "--init",
            "--platform", platform,
            "--label", CONTAINER_LABEL,
            "--name", name,
        )
        for (mount in mounts) {
            val guest = if (windowsHost) containerPath(mount.absolutePath) else mount.absolutePath
            args += listOf("-v", "${mount.absolutePath}:$guest")
        }
        args += listOf(image, "sleep", CONTAINER_TTL_SECONDS.toString())

        logger.info("DockerExecutor starting persistent container for $target ($platform): $dockerAbsolutePath ${args.joinToString(" ")}")
        val result = hostExecutor.runProcess(dockerAbsolutePath, *args.toTypedArray()) {
            timeout = CONTAINER_START_TIMEOUT
        }
        val id = result.stdout.trim()
        require(id.isNotEmpty()) { "docker run -d produced empty container id. stderr: ${result.stderr}" }
        logger.info("DockerExecutor container $id ($name) ready, mounts=$mounts")
        return id
    }

    private fun destroyContainer(id: String) {
        try {
            val nullFile = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) File("NUL") else File("/dev/null")
            val process = ProcessBuilder(dockerAbsolutePath, "rm", "-f", id)
                .redirectOutput(ProcessBuilder.Redirect.to(nullFile))
                .redirectError(ProcessBuilder.Redirect.to(nullFile))
                .start()
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        } catch (_: Exception) {
            // Best-effort: `--rm` + sleep TTL still bounds a leaked container.
        }
    }

    companion object {
        const val DEFAULT_IMAGE = "ubuntu:24.04"
        const val CONTAINER_LABEL = "kn-test-executor=1"
        const val CONTAINER_TTL_SECONDS = 16 * 3600L

        private val INNER_TIMEOUT_GRACE = 30.seconds
        private val CONTAINER_START_TIMEOUT = 2.minutes

        fun defaultImage(): String =
            System.getProperty("kotlin.internal.native.test.dockerImage") ?: DEFAULT_IMAGE

        /**
         * `--platform` for [target], or `null` if unsupported.
         * `linux_x64` → `linux/amd64`, `linux_arm64` → `linux/arm64`.
         */
        fun platformFor(target: KonanTarget): String? = when (target) {
            KonanTarget.LINUX_X64 -> "linux/amd64"
            KonanTarget.LINUX_ARM64 -> "linux/arm64"
            else -> null
        }

        /**
         * Gradle Test worker cwd and JVM temporary directory. The latter contains
         * executables produced by compiler tests using temporary directories.
         */
        fun bindMountRoots(): List<File> = listOf("user.dir", "java.io.tmpdir")
            .map { File(System.getProperty(it)).canonicalFile }
            .distinct()

        /**
         * Supports [linux_x64][KonanTarget.LINUX_X64] or [linux_arm64][KonanTarget.LINUX_ARM64]
         * and when `docker` is on PATH. Does not probe the docker daemon.
         */
        fun availableFor(target: KonanTarget): Boolean {
            if (platformFor(target) == null) return false
            if (target == HostManager.host) return false
            return dockerOnPath != null
        }

        internal val windowsHost: Boolean =
            System.getProperty("os.name").orEmpty().startsWith("Windows")

        /** `C:\a\b` → `/c/a/b`. The Linux container has no Windows drive path. */
        internal fun containerPath(hostPath: String): String {
            val trimmed = hostPath.removePrefix("\\\\?\\")
            val match = Regex("^([A-Za-z]):[\\\\/](.*)").matchEntire(trimmed)
                ?: error("DockerExecutor cannot map Windows path: $hostPath")
            val rest = match.groupValues[2].replace('\\', '/').trim('/')
            val root = "/" + match.groupValues[1].lowercase()
            return if (rest.isEmpty()) root else "$root/$rest"
        }

        /**
         * On Windows, rewrite [value] when it is under a mounted host directory.
         * Returns null on other hosts, and when [value] is outside every mount.
         */
        internal fun mapUnderMount(value: String, mounts: List<File>): String? {
            if (!windowsHost) return null
            val normalized = value.removePrefix("\\\\?\\").replace('\\', '/')
            var best: File? = null
            var bestLength = -1
            for (mount in mounts) {
                val host = mount.absolutePath.removePrefix("\\\\?\\").replace('\\', '/')
                val exact = normalized.equals(host, ignoreCase = true)
                val nested = normalized.startsWith(host.trimEnd('/') + "/", ignoreCase = true)
                if ((exact || nested) && host.length > bestLength) {
                    best = mount
                    bestLength = host.length
                }
            }
            val mount = best ?: return null
            val host = mount.absolutePath.removePrefix("\\\\?\\").replace('\\', '/')
            val guestRoot = containerPath(mount.absolutePath).trimEnd('/')
            if (normalized.equals(host, ignoreCase = true)) return guestRoot
            return guestRoot + "/" + normalized.substring(host.trimEnd('/').length + 1)
        }

        private fun guestPath(file: File, mounts: List<File>): String {
            if (!windowsHost) return file.absolutePath
            return mapUnderMount(file.absolutePath, mounts)
                ?: error("DockerExecutor on Windows requires $file to be under a mounted directory: $mounts")
        }

        private fun mapGuestText(value: String, mounts: List<File>): String =
            mapUnderMount(value, mounts) ?: value

        private val dockerOnPath: String? by lazy {
            val names = if (windowsHost) listOf("docker.exe", "docker") else listOf("docker")
            for (dir in (System.getenv("PATH") ?: "").split(File.pathSeparatorChar)) {
                if (dir.isEmpty()) continue
                for (name in names) {
                    val candidate = File(dir, name)
                    try {
                        if (candidate.isFile && candidate.canExecute()) return@lazy candidate.absolutePath
                    } catch (_: SecurityException) {
                        // Skip PATH entries the SecurityManager does not allow reading.
                    }
                }
            }
            null
        }
    }
}
