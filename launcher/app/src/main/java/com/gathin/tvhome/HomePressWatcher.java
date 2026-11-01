package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Low-priority local log stream, limited at source to activity-start and activity-resume tags. */
final class HomePressWatcher implements AutoCloseable {
    private volatile boolean closed;
    private volatile Process process;
    private Thread worker;
    private final Handler main;
    private final java.util.function.IntConsumer onHome;

    HomePressWatcher(Context context, Handler main, java.util.function.IntConsumer onHome) {
        this.main = main;
        this.onHome = onHome;
        if (context.checkSelfPermission("android.permission.READ_LOGS") != PackageManager.PERMISSION_GRANTED)
            return;
        worker = new Thread(this::watch, "home-signal");
        worker.start();
    }

    private void watch() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        while (!closed) {
            // Start at the current device time, so old Home events cannot bring us forward.
            String since = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            long lastSignal = -1000;
            try {
                Process child = new ProcessBuilder("logcat", "-b", "system", "-b", "events", "-v", "brief",
                        "-T", since, "ActivityManager:I", "ActivityTaskManager:I", "am_resume_activity:I", "wm_resume_activity:I", "*:S")
                        .redirectErrorStream(true).start();
                process = child;
                if (closed) { child.destroy(); return; }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        child.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (!closed && (line = reader.readLine()) != null) {
                        long now = SystemClock.uptimeMillis();
                        int kind=HomeSignal.kind(line);
                        if (kind != HomeSignal.NONE) {
                            if (HomeSignal.isHome(kind)) {
                                if (now - lastSignal < 200) continue;
                                lastSignal = now;
                            }
                            final int k = kind;
                            main.post(() -> { if (!closed) onHome.accept(k); });
                        }
                    }
                } finally { child.destroy(); process = null; }
            } catch (Exception ignored) {
                // Accessibility remains the fallback. No raw log content is persisted.
            }
            if (!closed) try { Thread.sleep(15000); } catch (InterruptedException ignored) { return; }
        }
    }

    @Override public void close() {
        closed = true;
        Process child = process;
        if (child != null) child.destroy();
        if (worker != null) worker.interrupt();
    }
}
