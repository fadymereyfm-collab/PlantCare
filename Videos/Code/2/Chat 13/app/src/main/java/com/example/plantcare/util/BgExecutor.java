package com.example.plantcare.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java-friendly background executor — replacement for `new Thread(r).start()` calls
 * sprinkled across legacy Activities/Adapters. Bounded pool prevents thread explosion
 * when adapters fire many parallel IO tasks.
 *
 * For Fragment-scoped IO use {@link com.example.plantcare.ui.util.FragmentBg} instead,
 * which ties the work to the fragment's lifecycle.
 */
public final class BgExecutor {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "plantcare-bg-" + COUNTER.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    };

    private static final ExecutorService IO_POOL =
            Executors.newFixedThreadPool(4, FACTORY);

    private BgExecutor() {}

    /** Run {@code task} on a background thread (no UI callback). */
    public static void io(Runnable task) {
        IO_POOL.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                com.example.plantcare.CrashReporter.INSTANCE.log(t);
            }
        });
    }
}
