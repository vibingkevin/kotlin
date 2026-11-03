/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CommandCancellationProbe {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("tool")) {
            Path work = Paths.get(args[1]);
            Files.writeString(work.resolve("tool.pid"), Long.toString(ProcessHandle.current().pid()));
            Files.createFile(work.resolve("ready"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!Files.exists(work.resolve("release"))) {
                if (System.nanoTime() > deadline) throw new AssertionError("Tool was never released");
                Thread.sleep(5);
            }
            Files.writeString(work.resolve("finished.txt"), "tool-finished");
            System.out.println("tool-finished");
            return;
        }

        boolean interrupt = Boolean.parseBoolean(args[0]);
        Path work = Paths.get(args[1]);
        Files.createDirectories(work);
        Path pidFile = work.resolve("tool.pid");
        Path finishedFile = work.resolve("finished.txt");
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String[] command = {
            java, "-cp", System.getProperty("java.class.path"),
            CommandCancellationProbe.class.getName(), "tool", work.toString()
        };
        Object realCommand = Class.forName("org.jetbrains.kotlin.konan.exec.Command")
            .getConstructor(String[].class).newInstance((Object) command);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Integer> exitCode = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                Object result = realCommand.getClass().getMethod("getResult", boolean.class, boolean.class)
                    .invoke(realCommand, true, false);
                exitCode.set((Integer) result.getClass().getMethod("getExitCode").invoke(result));
            } catch (InvocationTargetException exception) {
                failure.set(exception.getCause());
            } catch (Throwable exception) {
                failure.set(exception);
            }
        }, "command-caller");
        ProcessHandle tool = null;
        try {
            caller.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!Files.exists(work.resolve("ready")) && caller.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            if (!Files.exists(work.resolve("ready"))) throw new AssertionError("Tool never started", failure.get());
            tool = ProcessHandle.of(Long.parseLong(Files.readString(pidFile))).orElseThrow();
            if (interrupt) {
                caller.interrupt();
            } else {
                Files.createFile(work.resolve("release"));
            }
            caller.join(10000);
            boolean callerReturned = !caller.isAlive();
            boolean toolAliveAtReturn = tool.isAlive();
            boolean finishedAtReturn = Files.exists(finishedFile);
            if (interrupt) Files.createFile(work.resolve("release"));
            tool.onExit().get(5, TimeUnit.SECONDS);
            boolean wroteAfterReturn = !finishedAtReturn && Files.exists(finishedFile);

            System.out.println("INTERRUPTED=" + interrupt + " CALLER_RETURNED=" + callerReturned
                + " EXTERNAL_TOOL_ALIVE_AT_RETURN=" + toolAliveAtReturn
                + " TOOL_WROTE_OUTPUT_AFTER_RETURN=" + wroteAfterReturn
                + " RESULT=" + exitCode.get()
                + " FAILURE=" + (failure.get() == null ? "none" : failure.get().getClass().getName()));
            if (!callerReturned || toolAliveAtReturn || wroteAfterReturn) {
                throw new AssertionError("Command returned before its external tool stopped");
            }
            if (interrupt) {
                if (!(failure.get() instanceof InterruptedException) || finishedAtReturn) {
                    throw new AssertionError("Expected interruption before the tool produced output", failure.get());
                }
            } else if (failure.get() != null || !Objects.equals(exitCode.get(), 0) || !finishedAtReturn) {
                throw new AssertionError("Normal command failed", failure.get());
            }
        } finally {
            // The PID file belongs exclusively to this probe, including a failure before ready was written.
            if (tool == null && Files.exists(pidFile)) {
                tool = ProcessHandle.of(Long.parseLong(Files.readString(pidFile))).orElse(null);
            }
            if (tool != null && tool.isAlive()) {
                tool.destroyForcibly();
                tool.onExit().get(5, TimeUnit.SECONDS);
            }
            if (caller.isAlive()) {
                caller.interrupt();
                caller.join(5000);
            }
        }
    }
}
