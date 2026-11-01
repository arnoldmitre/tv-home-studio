package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

public class HomeWatcherService extends Service {
    private static final String TAG = "HomeWatcher";
    private static final String CHANNEL_ID = "tvhome_background";
    private static final int NOTIFICATION_ID = 4281;

    private static volatile boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private HomePressWatcher homeWatcher;
    private long lastRedirectAt;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        promoteToForeground();
        startWatcher();
        Log.i(TAG, "HomeWatcherService started");
    }

    private void promoteToForeground() {
        try {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel chan = new NotificationChannel(
                            CHANNEL_ID, "TV Home Service", NotificationManager.IMPORTANCE_LOW);
                    chan.setShowBadge(false);
                    nm.createNotificationChannel(chan);
                }
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }
            Notification notification = builder
                    .setContentTitle("TV Home")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(Notification.PRIORITY_MIN)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.w(TAG, "Cannot promote to foreground service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        if (homeWatcher == null) {
            startWatcher();
        }
        return START_STICKY;
    }

    private void startWatcher() {
        if (HomeRedirectService.isBound()) {
            stopSelf();
            return;
        }
        if (homeWatcher != null) homeWatcher.close();
        homeWatcher = new HomePressWatcher(this, handler, this::onHomeSignal);
    }

    private void onHomeSignal(int kind) {
        if (HomeRedirectService.isBound()) {
            stopSelf();
            return;
        }
        if (kind == HomeSignal.NONE) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastRedirectAt < 400) return;
        lastRedirectAt = now;

        MainActivity current = MainActivity.currentHome();
        if (current != null && current.isHomeResumed()) {
            return;
        }

        Log.i(TAG, "Redirecting Home button press to TV Home Studio");
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("com.gathin.tvhome.REDIRECT_REQUEST", true);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot launch MainActivity", e);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (homeWatcher != null) {
            homeWatcher.close();
            homeWatcher = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static boolean isRunning() {
        return running;
    }

    static void startIfNecessary(Context context) {
        if (!HomeRedirectService.isBound() && HomeRedirectService.isEnabled(context)) {
            try {
                context.startService(new Intent(context, HomeWatcherService.class));
            } catch (Exception e) {
                Log.w(TAG, "Cannot start HomeWatcherService", e);
            }
        }
    }
}
