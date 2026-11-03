/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class InterruptCompiler {
    public static void main(String[] args) throws Exception {
        boolean interrupt = Boolean.parseBoolean(args[0]);
        String[] compilerArgs = Arrays.copyOfRange(args, 1, args.length);
        PrintStream original = System.out;
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Set<Thread> workers = ConcurrentHashMap.newKeySet();
        ByteArrayOutputStream compilerOutput = new ByteArrayOutputStream();
        PrintStream gate = new PrintStream(compilerOutput) {
            @Override public void println(Object text) { println(String.valueOf(text)); }
            @Override public void println(String text) {
                Thread thread = Thread.currentThread();
                if (thread.getName().startsWith("pool-") && workers.add(thread)) {
                    entered.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try { release.await(); break; }
                        catch (InterruptedException expected) { interrupted = true; }
                    }
                    if (interrupted) thread.interrupt();
                }
            }
        };
        System.setOut(gate);
        AtomicReference<Object> resultCode = new AtomicReference<>();
        AtomicReference<Throwable> driverFailure = new AtomicReference<>();
        Thread compiler = new Thread(() -> {
            try {
                Class<?> klass = Class.forName("org.jetbrains.kotlin.cli.bc.K2Native");
                Object result = klass.getMethod("exec", PrintStream.class, String[].class)
                    .invoke(klass.getConstructor().newInstance(), gate, compilerArgs);
                resultCode.set(result);
                original.println("COMPILER_RESULT=" + result);
            } catch (Throwable failure) {
                driverFailure.set(failure);
                failure.printStackTrace(original);
            }
        }, "compiler-invocation");
        compiler.start();
        if (!entered.await(60, TimeUnit.SECONDS)) {
            release.countDown();
            original.println("FAILED_TO_REACH_PARALLEL_CODEGEN");
            original.println(compilerOutput.toString());
            for (StackTraceElement frame : compiler.getStackTrace()) original.println(frame);
            System.exit(2);
        }
        boolean returnedBeforeWorkers = false;
        if (interrupt) {
            compiler.interrupt();
            compiler.join(2000);
            returnedBeforeWorkers = !compiler.isAlive();
        }
        release.countDown();
        compiler.join(30000);
        for (Thread worker : workers) worker.join(1000);
        long alive = workers.stream().filter(Thread::isAlive).count();
        original.println("INTERRUPTED=" + interrupt + " RETURNED_WITH_ACTIVE_WORKERS=" + returnedBeforeWorkers
            + " LIVE_POOL_THREADS=" + alive + " COMPILER_ALIVE=" + compiler.isAlive());
        boolean compilationFailed = driverFailure.get() != null || resultCode.get() == null
            || (!interrupt && !"OK".equals(resultCode.get().toString()));
        System.exit(returnedBeforeWorkers || alive != 0 || compiler.isAlive() || compilationFailed ? 42 : 0);
    }
}
