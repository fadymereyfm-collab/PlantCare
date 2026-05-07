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

    // PERF3: pool sized to match the cloud-import fan-out (9 streams)
    // plus a small headroom for in-flight UI-driven IO. Pre-fix the
    // pool was hard-coded to 4, so during sign-in restore 5 of the
    // 9 import streams sat queued behind the first 4 — measurably
    // delayed the onCloudImportFinished callback that lifts the
    // CLOUD_IMPORT_IN_PROGRESS barrier (each stream blocks on a
    // Firestore round-trip), which in turn delays first paint of
    // restored data. 12 is generous without being a CPU-core
    // free-for-all (typical phones are 4-8 cores; the IO threads
    // mostly sleep on disk + network, so the over-provisioning is
    // cheap).
    private static final ExecutorService IO_POOL =
            Executors.newFixedThreadPool(12, FACTORY);

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
