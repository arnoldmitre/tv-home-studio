package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;

public class HomeWatcherService extends Service {
    private static final String TAG = "HomeWatcher";
    private static final String CHANNEL_ID = "tvhome_background";
    private static final int NOTIFICATION_ID = 4281;
    private static final long SETTINGS_GRACE_MS = 2000;
    private static final long EXTERNAL_LAUNCH_TIMEOUT_MS = 8000;

    private static volatile HomeWatcherService instance;
    private static volatile long connectionEpoch;
    private static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private HomePressWatcher homeWatcher;
    private HomeSurface overlay;
    private volatile boolean attached;
    private boolean windowAdded;
    private boolean receiversRegistered;
    private boolean screenOff;
    private boolean dreaming;

    private long lastRedirectAt;
    private long settingsSeenAt = -SETTINGS_GRACE_MS;
    private long externalLaunchUntil;
    private long anchorDeadline;
    private boolean launcherRequested;
    private int redirectGeneration;

    private final Runnable anchorRetry = new Runnable() {
        @Override
        public void run() {
            if (!attached || screenOff || dreaming) {
                launcherRequested = false;
                return;
            }
            MainActivity current = MainActivity.currentHome();
            if (current != null && current.isHomeResumed()) {
                launcherRequested = false;
                return;
            }
            if (SystemClock.uptimeMillis() >= anchorDeadline) {
                launcherRequested = false;
                return;
            }
            launchHomeActivity();
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String action = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOff = true;
                hideOverlay();
            } else if (Intent.ACTION_DREAMING_STARTED.equals(action)) {
                dreaming = true;
                hideOverlay();
            } else if (Intent.ACTION_DREAMING_STOPPED.equals(action)) {
                if (dreaming) {
                    dreaming = false;
                    wakeHome();
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)
                    || Intent.ACTION_USER_UNLOCKED.equals(action)) {
                wakeHome();
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            HomeSurface.invalidateApps();
            if (attached && overlay != null) {
                overlay.refresh();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        connectionEpoch = SystemClock.elapsedRealtime();
        running = true;
        screenOff = !interactive();
        promoteToForeground();

        if (!receiversRegistered) {
            IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            screen.addAction(Intent.ACTION_SCREEN_ON);
            screen.addAction(Intent.ACTION_USER_PRESENT);
            screen.addAction(Intent.ACTION_USER_UNLOCKED);
            screen.addAction(Intent.ACTION_DREAMING_STARTED);
            screen.addAction(Intent.ACTION_DREAMING_STOPPED);
            registerReceiver(screenReceiver, screen);

            IntentFilter packages = new IntentFilter();
            packages.addAction(Intent.ACTION_PACKAGE_ADDED);
            packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
            packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
            packages.addDataScheme("package");
            registerReceiver(packageReceiver, packages);
            receiversRegistered = true;
        }

        startWatcher();
        Log.i(TAG, "HomeWatcherService started with instant overlay support");

        if (interactive() && HomeRedirectService.isEnabled(this)) {
            MainActivity current = MainActivity.currentHome();
            if (current != null && current.isHomeResumed()) {
                onActivityReady(current, current.surface());
            }
        }
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
            destroyOverlay();
            stopSelf();
            return;
        }
        if (homeWatcher != null) homeWatcher.close();
        homeWatcher = new HomePressWatcher(this, handler, this::onHomeSignal);
    }

    private boolean interactive() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    private void wakeHome() {
        if (!interactive() || !HomeRedirectService.isEnabled(this)) return;
        screenOff = false;
        dreaming = false;
        externalLaunchUntil = 0;
        settingsSeenAt = -SETTINGS_GRACE_MS;
        showOverlay();
        requestHomeActivity();
    }

    private void onHomeSignal(int kind) {
        if (HomeRedirectService.isBound()) {
            destroyOverlay();
            stopSelf();
            return;
        }
        if (kind == HomeSignal.NONE) return;
        long now = SystemClock.uptimeMillis();

        if (kind == HomeSignal.SETTINGS_RESUME) {
            settingsSeenAt = now;
            hideOverlay();
            return;
        }
        if (kind == HomeSignal.EXTERNAL_RESUME || kind == HomeSignal.EXTERNAL_START) {
            if (now >= externalLaunchUntil && attached) {
                hideOverlay();
            }
            return;
        }
        if (kind == HomeSignal.TVHOME_RESUME) {
            launcherRequested = false;
            handler.removeCallbacks(anchorRetry);
            return;
        }

        if (now - settingsSeenAt < SETTINGS_GRACE_MS) {
            Log.i(TAG, "Home screen on the way into Settings: left alone");
            return;
        }
        if (now < externalLaunchUntil) {
            return;
        }

        externalLaunchUntil = 0;
        settingsSeenAt = -SETTINGS_GRACE_MS;

        if (attached && overlay != null) {
            overlay.goHome();
            overlayPassThrough(false);
        } else {
            showOverlay();
        }

        if (now - lastRedirectAt < 400) return;
        lastRedirectAt = now;

        Log.i(TAG, "Home signal detected (kind=" + kind + "), requesting MainActivity anchor");
        requestHomeActivity();
    }

    private void requestHomeActivity() {
        MainActivity current = MainActivity.currentHome();
        if (current != null && current.isHomeResumed()) {
            launcherRequested = false;
            return;
        }
        launcherRequested = true;
        anchorDeadline = SystemClock.uptimeMillis() + 6000;
        launchHomeActivity();
        handler.removeCallbacks(anchorRetry);
        handler.postDelayed(anchorRetry, 1000);
    }

    private void launchHomeActivity() {
        MainActivity current = MainActivity.currentHome();
        if (current != null && current.isHomeResumed()) {
            launcherRequested = false;
            return;
        }
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("com.gathin.tvhome.REDIRECT_REQUEST", ++redirectGeneration);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot launch MainActivity", e);
        }
    }

    void showOverlay() {
        if (screenOff || dreaming) return;
        if (attached) {
            if (overlay != null) {
                overlay.setSurfaceActive(true);
                overlayPassThrough(false);
            }
            return;
        }
        try {
            if (overlay == null) {
                overlay = new HomeSurface(new ContextThemeWrapper(this, R.style.Theme_TvHome));
            }
            boolean retained = windowAdded;
            if (!windowAdded) {
                overlay.refresh();
                int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        windowType,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.OPAQUE);
                params.setTitle("TV Home instant surface");
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm != null) {
                    wm.addView(overlay, params);
                    windowAdded = true;
                }
            }
            overlay.setVisibility(android.view.View.VISIBLE);
            attached = true;
            overlay.setSurfaceActive(true);
            overlay.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            overlayPassThrough(false);
            if (retained) {
                handler.postDelayed(() -> {
                    if (attached && overlay != null) overlay.refresh();
                }, 150);
            }
            Log.i(TAG, "Instant Home overlay displayed");
        } catch (RuntimeException e) {
            Log.w(TAG, "Overlay unavailable; falling back to activity only", e);
        }
    }

    void hideOverlay() {
        if (!attached || overlay == null) return;
        overlay.dismissMenu();
        overlay.setSurfaceActive(false);
        overlay.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        attached = false;
        overlayPassThrough(true);
        overlay.setVisibility(android.view.View.INVISIBLE);
        Log.i(TAG, "Instant Home overlay hidden");
    }

    private void destroyOverlay() {
        hideOverlay();
        if (!windowAdded || overlay == null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.removeViewImmediate(overlay);
        } catch (Exception e) {
            Log.d(TAG, "Overlay was already removed", e);
        }
        windowAdded = false;
        overlay = null;
    }

    private void overlayPassThrough(boolean value) {
        if (!windowAdded || overlay == null) return;
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlay.getLayoutParams();
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        int updated = value ? params.flags | flags : params.flags & ~flags;
        if (params.flags == updated) return;
        params.flags = updated;
        wm.updateViewLayout(overlay, params);
    }

    void launchFromSurface(HomeSurface source, Intent request) {
        externalLaunchUntil = SystemClock.uptimeMillis() + EXTERNAL_LAUNCH_TIMEOUT_MS;
        overlayPassThrough(true);
        handler.removeCallbacks(this::hideOverlay);
        handler.postDelayed(this::hideOverlay, 250);

        if (request.getAction() != null && request.getAction().startsWith("android.settings.")) {
            settingsSeenAt = SystemClock.uptimeMillis();
        } else if (request.getComponent() != null) {
            String pkg = request.getComponent().getPackageName();
            if ("com.amazon.tv.settings".equals(pkg) || "com.amazon.tv.settings.v2".equals(pkg) || "com.amazon.tv.quicksettings".equals(pkg)) {
                settingsSeenAt = SystemClock.uptimeMillis();
            }
        }
        try {
            startActivity(request);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot launch app from surface", e);
            overlayPassThrough(false);
        }
    }

    void onActivityReady(MainActivity activity, HomeSurface surface) {
        launcherRequested = false;
        handler.removeCallbacks(anchorRetry);
        if (attached && overlay != null) {
            surface.setSurfaceActive(false);
            surface.setVisibility(android.view.View.INVISIBLE);
            surface.refresh();
            surface.restorePage(overlay.page());
            surface.restoreSelection(overlay.selection(), overlay.scrollPosition());
        }
    }

    void onActivityPaused(MainActivity activity) {
        // MainActivity paused
    }

    void refreshOverlay() {
        if (attached && overlay != null) overlay.refresh();
    }

    @Override
    public void onDestroy() {
        running = false;
        destroyOverlay();
        if (receiversRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
            try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
            receiversRegistered = false;
        }
        if (homeWatcher != null) {
            homeWatcher.close();
            homeWatcher = null;
        }
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    static HomeWatcherService getInstance() {
        return instance;
    }

    static boolean isRunning() {
        return running;
    }

    static boolean isOverlayAttached() {
        HomeWatcherService s = instance;
        return s != null && s.attached;
    }

    static long getConnectionEpoch() {
        return connectionEpoch;
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
