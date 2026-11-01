package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.WindowManager;
import android.view.ContextThemeWrapper;
import android.graphics.PixelFormat;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.widget.Toast;

/**
 * Makes this launcher the home screen on Fire OS, which ignores the default-home setting.
 *
 * Fire OS never passes the Home key to accessibility services, so this watches windows instead: when Amazon's
 * home screen comes up, this launcher opens over it straight away. One exception: Amazon's home screen also
 * flashes up on the way into Settings, right after a window from Amazon's settings app, so a home screen that
 * follows a settings window within SETTINGS_GRACE_MS is left alone. One hardware-accelerated Home
 * window remains visible and interactive until a foreground app, system screen, or sleep takes over.
 *
 * Turned on and off from the launcher (Menu on the Settings button) when the app holds WRITE_SECURE_SETTINGS
 * (granted over adb: pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS), or over adb:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService
 *   settings put secure accessibility_enabled 1
 */
public class HomeRedirectService extends AccessibilityService {

    private static final String TAG = "HomeRedirect";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "HomeActivity";
    private static final String EXTRA_REDIRECT = "com.gathin.tvhome.REDIRECT_REQUEST";
    private static final String[] SETTINGS_APPS = {"com.amazon.tv.settings", "com.amazon.tv.settings.v2", "com.amazon.tv.quicksettings"};
    private static final long SETTINGS_GRACE_MS = 2000;     // measured: settings window -> home flash ~200 ms
    private static final long EXTERNAL_LAUNCH_TIMEOUT_MS = 8000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redirect = this::openLauncher;
    private long settingsSeenAt = -SETTINGS_GRACE_MS;
    private static volatile HomeRedirectService instance;
    private static volatile long connectionEpoch;
    private boolean recoverUnknownHome;
    private HomeSurface overlay;
    private volatile boolean attached;
    private boolean windowAdded;
    private android.widget.FrameLayout startupCover;
    private final Runnable refreshVisibleHome=()->{if(attached&&overlay!=null)overlay.refresh();};
    private long bypassUntil;
    private long returnStartedAt;
    private long externalLaunchUntil;
    private boolean launcherRequested;
    private long anchorDeadline;
    private final Runnable anchorRetry=new Runnable(){public void run(){
        if(!launcherRequested||!attached||screenOff||yieldToOtherScreen)return;
        if(SystemClock.uptimeMillis()>=anchorDeadline){launcherRequested=false;return;}
        requestHomeActivity();
    }};
    private boolean yieldToOtherScreen;
    private int redirectGeneration;
    private boolean receiversRegistered;
    private MainActivity resumedActivity;
    private HomePressWatcher homeWatcher;
    private PowerActions powerActions;
    private Runnable transitionRelease;
    private boolean screenOff;
    private boolean dreaming;
    private long lastWakeAt = -2000;
    private String lastForegroundPackage="", lastForegroundClass="";
    private long recoveryUntil;
    private final Runnable recoveryCheck=this::checkWakeRecovery;
    private String pendingExternalPackage;
    private Intent queuedExternalIntent;
    private final Runnable wakeRedirect = () -> { if (!screenOff) earlyHomePressed(); };
    private final Runnable externalLaunchTimeout = () -> {
        // A failed/deferred launch must never leave an invisible Home suppression window behind.
        boolean wasQueued = queuedExternalIntent != null;
        clearExternalLaunch();
        if (wasQueued) {
            launcherRequested = false;
            Toast.makeText(this, "Please select the app again", Toast.LENGTH_SHORT).show();
            earlyHomePressed();
        } else if (attached && resumedActivity != null) {
            activityReady(resumedActivity, resumedActivity.surface());
        } else {
            hideOverlay();
        }
    };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                screenOff = true;
                getSharedPreferences("tvhome", MODE_PRIVATE).edit().putBoolean("resume_home", true).apply();
                stopRecovery();handler.removeCallbacks(wakeRedirect);
                cancelRedirect();clearExternalLaunch();
                // Prepare Home while the display is off, before Fire OS paints its wake screen.
                showOverlay();overlayPassThrough(true);if(overlay!=null)overlay.setSurfaceActive(false);
            } else if (Intent.ACTION_DREAMING_STARTED.equals(i.getAction())) {
                dreaming = true;
                stopRecovery();cancelRedirect();clearExternalLaunch();hideOverlay();
            } else if (Intent.ACTION_DREAMING_STOPPED.equals(i.getAction())) {
                if (dreaming) { dreaming = false; wakeHome(); }
            } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction()) || Intent.ACTION_USER_PRESENT.equals(i.getAction()) || Intent.ACTION_USER_UNLOCKED.equals(i.getAction())) wakeHome();
        }
    };
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { HomeSurface.invalidateApps(); }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;connectionEpoch=SystemClock.elapsedRealtime();
        screenOff = !((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
        powerActions = new PowerActions(this, handler, () -> {
            if(resumedActivity!=null&&resumedActivity.isHomeResumed())showOverlay();else hideOverlay();
            Toast.makeText(this,"Power action unavailable. Please use Settings → My Fire TV → Restart.",Toast.LENGTH_LONG).show();
        });
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
        Log.i(TAG, "interactive Home overlay ready");
        if (homeWatcher != null) homeWatcher.close();
        homeWatcher = new HomePressWatcher(this, handler, this::earlyHomeSignal);
        android.content.SharedPreferences state=getSharedPreferences("tvhome",MODE_PRIVATE);
        int boot=Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        boolean freshBoot=BootSession.fresh(state.getInt("connected_boot",-1),boot,SystemClock.elapsedRealtime());
        if(boot>=0)state.edit().putInt("connected_boot",boot).apply();
        // Attach a cheap first frame before loading app artwork or decoding the wallpaper.
        // BOOT_COMPLETED is delivered much later on Fire OS; do not wait for it.
        if(freshBoot&&interactive()&&isEnabled(this)){
            showStartupCover();
            return;
        }
        MainActivity currentHome=MainActivity.currentHome();
        if(currentHome!=null&&currentHome.isHomeResumed()&&!screenOff)
            activityReady(currentHome,currentHome.surface());
        if (getSharedPreferences("tvhome", MODE_PRIVATE).getBoolean("resume_home", false)
                && interactive()) wakeHome();
        else if(interactive()){recoveryUntil=SystemClock.uptimeMillis()+6000;handler.post(recoveryCheck);}
    }

    private void showStartupCover(){
        startupCover=new android.widget.FrameLayout(this);
        startupCover.setBackgroundColor(0xff080f1b);
        android.widget.TextView title=new android.widget.TextView(this);
        title.setText(StudioConfig.read(this).optString("homeName","HOME"));title.setTextColor(0xfff4f6fb);
        title.setTextSize(24);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);title.setGravity(android.view.Gravity.CENTER);
        startupCover.addView(title,new android.widget.FrameLayout.LayoutParams(-1,-1));
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_FULLSCREEN
                        |WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.OPAQUE);
        params.setTitle("TV Home startup");
        android.widget.FrameLayout cover=startupCover;
        cover.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener(){
            private boolean scheduled;
            @Override public void onDraw(){if(scheduled)return;scheduled=true;handler.post(()->{
                if(cover.getViewTreeObserver().isAlive())cover.getViewTreeObserver().removeOnDrawListener(this);
                if(startupCover!=cover)return;
                wakeHome();
                // Keep the cover until the complete Home has drawn, never for ongoing playback.
                if(overlay!=null&&attached)overlay.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener(){
                    private boolean drawn;
                    @Override public void onDraw(){if(drawn)return;drawn=true;handler.post(()->{
                        if(overlay!=null&&overlay.getViewTreeObserver().isAlive())overlay.getViewTreeObserver().removeOnDrawListener(this);
                        removeStartupCover();
                    });}
                });else removeStartupCover();
            });}
        });
        try{((WindowManager)getSystemService(WINDOW_SERVICE)).addView(cover,params);}
        catch(RuntimeException e){startupCover=null;Log.w(TAG,"Startup cover unavailable",e);wakeHome();}
        handler.postDelayed(this::removeStartupCover,6000);
    }
    private void removeStartupCover(){
        if(startupCover==null)return;
        try{((WindowManager)getSystemService(WINDOW_SERVICE)).removeViewImmediate(startupCover);}
        catch(IllegalArgumentException ignored){}
        startupCover=null;
    }
    static boolean bootCompleted(){
        HomeRedirectService s=instance;if(s==null)return false;
        // A delayed boot broadcast must not drag someone back out of an app or Settings.
        if(s.interactive()&&!s.attached&&SystemClock.uptimeMillis()>=s.externalLaunchUntil){
            s.recoverUnknownHome=false;s.recoveryUntil=SystemClock.uptimeMillis()+6000;
            s.handler.removeCallbacks(s.recoveryCheck);s.handler.post(s.recoveryCheck);
        }
        return true;
    }

    private boolean interactive(){return ((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive();}
    private void stopRecovery(){recoveryUntil=0;handler.removeCallbacks(recoveryCheck);}
    private void wakeHome() {
        if(!interactive()||!isEnabled(this))return;
        screenOff=false;dreaming=false;
        long now=SystemClock.uptimeMillis();
        if(now-lastWakeAt<1500&&recoveryUntil>now)return;
        lastWakeAt=now;recoveryUntil=now+6000;recoverUnknownHome=true;
        // Sleep pauses uptime timers. A launch/Settings bypass from before sleep must not
        // suppress the only wake request or replay an old queued app after wake.
        cancelRedirect();clearExternalLaunch();bypassUntil=0;settingsSeenAt=-SETTINGS_GRACE_MS;yieldToOtherScreen=false;
        getSharedPreferences("tvhome",MODE_PRIVATE).edit().putBoolean("resume_home",true).apply();
        showOverlay();
        handler.removeCallbacks(wakeRedirect);handler.post(wakeRedirect);
        handler.removeCallbacks(recoveryCheck);handler.postDelayed(recoveryCheck,350);
    }
    private void checkWakeRecovery(){
        long now=SystemClock.uptimeMillis();if(now>=recoveryUntil||!interactive()||!isEnabled(this)){stopRecovery();return;}
        if(now<externalLaunchUntil||now<bypassUntil||(powerActions!=null&&powerActions.pending())){stopRecovery();return;}
        String pkg="",cls="";int layer=Integer.MIN_VALUE;android.view.accessibility.AccessibilityWindowInfo top=null;
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows=getWindows();
        try{for(android.view.accessibility.AccessibilityWindowInfo w:windows)if(w.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION&&w.getLayer()>layer){top=w;layer=w.getLayer();}
            if(top!=null){android.view.accessibility.AccessibilityNodeInfo root=top.getRoot();if(root!=null)try{pkg=String.valueOf(root.getPackageName());cls=String.valueOf(root.getClassName());}finally{root.recycle();}}}
        finally{for(android.view.accessibility.AccessibilityWindowInfo w:windows)w.recycle();}
        if(pkg.equals(lastForegroundPackage)&&!lastForegroundClass.isEmpty())cls=lastForegroundClass;
        int action=WakeRecovery.decide(pkg,cls,getPackageName(),now-settingsSeenAt<SETTINGS_GRACE_MS,recoverUnknownHome);
        if(action==WakeRecovery.HOME){screenOff=false;yieldToOtherScreen=false;showOverlay();if(resumedActivity==null||!resumedActivity.isHomeResumed()){cancelRedirect();handler.post(redirect);}}
        else if(action==WakeRecovery.STOP){stopRecovery();return;}
        handler.postDelayed(recoveryCheck,650);
    }
    private void earlyHomeSignal(int kind) {
        long now=SystemClock.uptimeMillis();
        // App exits and task resumes are Home returns, not physical Home key presses.
        // Preserve explicit Settings and launch bypasses for those indirect requests.
        if(!HomeSignal.shouldRedirect(kind,now<externalLaunchUntil,
                now-settingsSeenAt<SETTINGS_GRACE_MS,now<bypassUntil))return;
        Log.i(TAG,"early Home return kind="+kind);
        earlyHomePressed();
    }
    private void earlyHomePressed() {
        if(!isEnabled(this))return;
        if (!interactive()) {screenOff=true;return;}
        screenOff=false;
        if (powerActions != null && powerActions.pending()) return;
        // Framework Home launches also occur inside app/Settings navigation. They are not
        // necessarily physical Home presses, so never undo an explicit in-flight selection.
        if (SystemClock.uptimeMillis() < externalLaunchUntil) return;
        if (launcherRequested) { showOverlay(); return; }
        yieldToOtherScreen = false;
        // A physical Home press is explicit navigation, including from Amazon Home itself.
        bypassUntil = 0;
        settingsSeenAt = -SETTINGS_GRACE_MS;
        clearExternalLaunch();
        cancelRedirect();
        returnStartedAt=SystemClock.uptimeMillis();
        Log.i(TAG, "early Home signal");
        showOverlay();
        handler.post(redirect);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int type=event.getEventType();
        if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&type!=AccessibilityEvent.TYPE_WINDOWS_CHANGED)return;
        boolean awake=interactive();if(!awake){screenOff=true;return;}if(screenOff)wakeHome();
        if(type==AccessibilityEvent.TYPE_WINDOWS_CHANGED){if(recoveryUntil>SystemClock.uptimeMillis()){handler.removeCallbacks(recoveryCheck);handler.postDelayed(recoveryCheck,80);}return;}
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null || cls == null) return;
        if (powerActions != null && powerActions.pending()) {
            if ("android".contentEquals(pkg)) powerActions.windowChanged();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (screenOff) return;
        // An old app-window event may have been queued before the early Home request.
        // It must not remove the cover or cancel a newer navigation decision.
        if(attached && now>=externalLaunchUntil && event.getEventTime()<returnStartedAt)return;
        // Our overlay and its popup also emit window events. They must not cancel their own redirect.
        if (getPackageName().contentEquals(pkg)) return;
        // Accessibility also reports background windows. With an opaque Home surface in front,
        // only the top application or a focused system window may take the screen away.
        if (attached && !isForegroundWindow(event.getWindowId())) return;
        lastForegroundPackage=pkg.toString();lastForegroundClass=cls.toString();
        for (String app : SETTINGS_APPS) if (app.contentEquals(pkg)) settingsSeenAt = now;
        if (AMAZON_LAUNCHER.contentEquals(pkg) && !cls.toString().contains(AMAZON_HOME)) {
            // Amazon emits widget/popup window events while Home is behind our overlay.
            // They are not app navigation and must not tear down the cover or invalidate its request.
            if (cls.toString().contains("SettingsActivity")) {
                yieldToOtherScreen = true;
                settingsSeenAt = now;cancelRedirect();clearExternalLaunch();releaseAfterAppDraw();
            }
            return;
        }
        if (!AMAZON_LAUNCHER.contentEquals(pkg) || !cls.toString().contains(AMAZON_HOME)) {
            if (pendingExternalPackage != null && !pendingExternalPackage.contentEquals(pkg)
                    && !"android".contentEquals(pkg) && !"com.android.systemui".contentEquals(pkg)
                    && !pkg.toString().contains("packageinstaller") && !pkg.toString().contains("permissioncontroller")) return;
            if (("com.android.systemui".contentEquals(pkg) || "android".contentEquals(pkg))
                    && !cls.toString().contains("Activity") && pendingExternalPackage == null) return;
            // Toasts and other background widgets are not evidence that the user left Home.
            if (!event.isFullScreen() && !cls.toString().contains("Activity")
                    && pendingExternalPackage == null) return;
            cancelRedirect();
            yieldToOtherScreen = true;
            // The selected app has actually opened. Allow the very next Home press immediately.
            // Android system dialogs also need input focus; never leave the overlay over them.
            if (!AMAZON_LAUNCHER.contentEquals(pkg)) clearExternalLaunch();
            releaseAfterAppDraw();
            return;
        }
        if (now < bypassUntil || now < externalLaunchUntil) return;
        // Ignore Home window notifications from the background while our activity is resumed.
        if (resumedActivity != null && resumedActivity.isHomeResumed()) return;
        if (now - settingsSeenAt < SETTINGS_GRACE_MS) {
            Log.i(TAG, "home screen on the way into Settings: left alone");
            return;
        }
        yieldToOtherScreen = false;
        showOverlay();
        if (!launcherRequested) {
            Log.i(TAG, "home screen: overlay displayed; requesting activity");
            handler.removeCallbacks(redirect);
            handler.post(redirect);
        }
    }

    @Override
    public void onInterrupt() {
    }

    private void openLauncher() {
        if (launcherRequested || SystemClock.uptimeMillis() < externalLaunchUntil) return;
        anchorDeadline=SystemClock.uptimeMillis()+6000;
        requestHomeActivity();
    }
    private void requestHomeActivity(){
        try {
            launcherRequested = true;
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra(EXTRA_REDIRECT, ++redirectGeneration)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            handler.removeCallbacks(anchorRetry);handler.postDelayed(anchorRetry,1000);
        } catch (RuntimeException e) {
            launcherRequested = false;
            Log.w(TAG, "Could not request home activity", e);
        }
    }

    private void cancelRedirect() {
        handler.removeCallbacks(redirect);
        handler.removeCallbacks(anchorRetry);
        launcherRequested = false;
        redirectGeneration++;
    }

    private void showOverlay() {
        cancelTransitionRelease();
        if (attached) { overlay.setSurfaceActive(true);overlayPassThrough(false); return; }
        long start = SystemClock.uptimeMillis();
        try {
            if (overlay == null) overlay = new HomeSurface(new ContextThemeWrapper(this, R.style.Theme_TvHome));
            boolean retained=windowAdded;
            if (!windowAdded) {
                overlay.refresh();
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        // Hidden windows are INVISIBLE, not alpha-zero opaque surfaces.
                        PixelFormat.OPAQUE);
                params.setTitle("TV Home instant surface");
                ((WindowManager)getSystemService(WINDOW_SERVICE)).addView(overlay, params);
                windowAdded = true;
            }
            overlay.setVisibility(android.view.View.VISIBLE);
            attached = true;
            overlay.setSurfaceActive(true);
            overlay.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            overlayPassThrough(false);
            // Package broadcasts can invalidate the app catalog while an app runs.
            // Reveal the existing buffer before any package-manager queries / artwork work.
            if(retained){handler.removeCallbacks(refreshVisibleHome);handler.postDelayed(refreshVisibleHome,150);}
            overlay.post(() -> Log.i(TAG, "overlay first layout after " + (SystemClock.uptimeMillis()-start) + "ms"));
        } catch (RuntimeException e) {
            Log.w(TAG, "overlay unavailable; falling back to activity", e);
        }
    }
    private void hideOverlay() {
        removeStartupCover();
        cancelTransitionRelease();
        handler.removeCallbacks(refreshVisibleHome);
        if (!attached || overlay == null) return;
        overlay.dismissMenu();
        // Retain lightweight view state, but release the hidden window's rendering surface.
        // INVISIBLE also removes it from power policy; alpha zero alone does not.
        overlay.setSurfaceActive(false);
        overlay.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        attached = false;
        overlayPassThrough(true);
        overlay.setVisibility(android.view.View.INVISIBLE);
    }
    private void destroyOverlay() {
        hideOverlay();
        if (!windowAdded || overlay == null) return;
        try { ((WindowManager)getSystemService(WINDOW_SERVICE)).removeViewImmediate(overlay); }
        catch (IllegalArgumentException e) { Log.d(TAG, "Overlay was already removed", e); }
        windowAdded = false;
    }
    private void cancelTransitionRelease() {
        if (transitionRelease != null) handler.removeCallbacks(transitionRelease);
        transitionRelease = null;
    }
    private void releaseAfterAppDraw() {
        removeStartupCover();
        if (!attached) return;
        cancelTransitionRelease();
        overlayPassThrough(true);
        // A window-state event precedes its first visible frame on this device.
        transitionRelease = () -> { transitionRelease = null; hideOverlay(); };
        handler.postDelayed(transitionRelease, 250);
    }
    private void overlayPassThrough(boolean value) {
        if (!windowAdded || overlay == null) return;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams)overlay.getLayoutParams();
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        int updated=value ? params.flags | flags : params.flags & ~flags;
        float alpha=1f;
        if(params.flags==updated && params.alpha==alpha)return;
        params.flags=updated;params.alpha=alpha;
        ((WindowManager)getSystemService(WINDOW_SERVICE)).updateViewLayout(overlay, params);
    }
    public static void activityReady(MainActivity activity, HomeSurface surface) {
        HomeRedirectService s = instance;
        if (s == null) {
            HomeWatcherService watcher = HomeWatcherService.getInstance();
            if (watcher != null) {
                watcher.onActivityReady(activity, surface);
            }
            return;
        }
        if (!activity.isHomeResumed()) return;
        if (s.powerActions != null && s.powerActions.pending()) return;
        int request = activity.getIntent().getIntExtra(EXTRA_REDIRECT, 0);
        activity.getIntent().removeExtra(EXTRA_REDIRECT);
        if (request != 0 && request != s.redirectGeneration) {
            // startActivity cannot retract an intent Fire OS has already queued. If Settings,
            // another app or sleep won the race, let that screen retain the foreground.
            Log.i(TAG, "Ignoring superseded Home activity request");
            if (s.screenOff || s.yieldToOtherScreen || SystemClock.uptimeMillis()<s.bypassUntil
                    || SystemClock.uptimeMillis()<s.externalLaunchUntil) {
                activity.moveTaskToBack(true);
                return;
            }
            // A duplicate window event is not a reason to expose the task underneath Home.
        }
        s.getSharedPreferences("tvhome",MODE_PRIVATE).edit().putBoolean("resume_home",false).apply();
        s.handler.removeCallbacks(s.anchorRetry);
        s.resumedActivity = activity;
        s.yieldToOtherScreen = false;
        s.launcherRequested = false;
        if (s.queuedExternalIntent != null) {
            // Android may have queued MainActivity during its Home app-switch cooldown. Opening a
            // selected app before this callback would let the stale Home request overtake that app.
            Intent intent = s.queuedExternalIntent;
            s.queuedExternalIntent = null;
            s.startExternal(activity, intent);
            return;
        }
        if (SystemClock.uptimeMillis() < s.externalLaunchUntil) return;
        // Keep one interactive window throughout Home use. The activity is an anchor for app
        // launches and a fallback when the service is disabled; there is no timed UI handoff.
        s.showOverlay();
        surface.setSurfaceActive(false);
        surface.setVisibility(android.view.View.INVISIBLE);
        surface.refresh();
        surface.restorePage(s.overlay.page());
        surface.restoreSelection(s.overlay.selection(),s.overlay.scrollPosition());
    }
    public static void activityPaused(MainActivity activity) {
        HomeRedirectService s = instance;
        if (s != null && s.resumedActivity == activity) {
            s.resumedActivity = null;
        }
        HomeWatcherService watcher = HomeWatcherService.getInstance();
        if (watcher != null) {
            watcher.onActivityPaused(activity);
        }
    }
    private boolean isForegroundWindow(int windowId){
        java.util.List<android.view.accessibility.AccessibilityWindowInfo> windows=getWindows();
        int highestLayer=Integer.MIN_VALUE,topApplication=-1;
        boolean focused=false;
        try{for(android.view.accessibility.AccessibilityWindowInfo w:windows){
            if(w.getId()==windowId&&w.isFocused())focused=true;
            if(w.getType()==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                    &&w.getLayer()>highestLayer){highestLayer=w.getLayer();topApplication=w.getId();}
        }}finally{for(android.view.accessibility.AccessibilityWindowInfo w:windows)w.recycle();}
        return windowId>=0&&(focused||windowId==topApplication);
    }
    public static void bypassHome() {
        if (instance != null) {
            instance.stopRecovery();
            instance.bypassUntil = SystemClock.uptimeMillis() + 15000;
            instance.cancelRedirect();
            instance.clearExternalLaunch();
            instance.hideOverlay();
        }
    }
    public static boolean sleep() {
        HomeRedirectService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
    public static boolean requestRestart() {
        HomeRedirectService s = instance;
        if (s == null || s.powerActions == null) return false;
        s.stopRecovery();s.cancelRedirect();s.clearExternalLaunch();
        s.showOverlay();s.overlayPassThrough(true);
        // The opaque cover must be rendered before opening the legacy system menu below it.
        s.overlay.postOnAnimation(() -> s.overlay.postOnAnimation(() -> s.powerActions.begin()));
        return true;
    }
    public static void launchFromSurface(HomeSurface source, Intent intent) {
        Context context=source.getContext();
        HomeRedirectService s = instance;
        Intent request = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (s == null) {
            HomeWatcherService watcher = HomeWatcherService.getInstance();
            if (watcher != null) {
                watcher.launchFromSurface(source, request);
                return;
            }
            startExternalActivity(context, request);
            return;
        }
        s.stopRecovery();s.handler.removeCallbacks(s.wakeRedirect);
        request.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        s.handler.removeCallbacks(s.redirect);
        s.externalLaunchUntil = SystemClock.uptimeMillis() + EXTERNAL_LAUNCH_TIMEOUT_MS;
        s.handler.removeCallbacks(s.externalLaunchTimeout);
        s.handler.postDelayed(s.externalLaunchTimeout, EXTERNAL_LAUNCH_TIMEOUT_MS);
        if (!request.hasCategory(Intent.CATEGORY_HOME)) {
            s.showOverlay();
            if (source != s.overlay) {
                s.overlay.restorePage(source.page());
                s.overlay.restoreSelection(source.selection(), source.scrollPosition());
            }
        }
        if (s.attached && s.launcherRequested && s.resumedActivity == null) {
            s.queuedExternalIntent = request;
            return;
        }
        s.startExternal(s.resumedActivity != null ? s.resumedActivity : context, request);
    }
    private void startExternal(Context context, Intent intent) {
        yieldToOtherScreen = true;
        android.content.pm.ResolveInfo resolved = getPackageManager().resolveActivity(intent, 0);
        pendingExternalPackage = resolved == null ? null : resolved.activityInfo.packageName;
        overlayPassThrough(true);
        externalLaunchUntil = SystemClock.uptimeMillis() + EXTERNAL_LAUNCH_TIMEOUT_MS;
        handler.removeCallbacks(externalLaunchTimeout);
        handler.postDelayed(externalLaunchTimeout, EXTERNAL_LAUNCH_TIMEOUT_MS);
        // Cover the transition into Fire OS Settings, which briefly routes through Amazon Home.
        if (intent.getAction() != null && intent.getAction().startsWith("android.settings."))
            settingsSeenAt = SystemClock.uptimeMillis();
        if (!startExternalActivity(context, intent)) {
            clearExternalLaunch();
            if (resumedActivity != null) activityReady(resumedActivity, resumedActivity.surface());
            else { overlayPassThrough(false); openLauncher(); }
        }
    }
    private static boolean startExternalActivity(Context context, Intent intent) {
        try { context.startActivity(intent); return true; }
        catch (RuntimeException e) {
            Log.w(TAG, "Could not open selected screen", e);
            Toast.makeText(context, "Could not open this screen", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
    private void clearExternalLaunch() {
        handler.removeCallbacks(externalLaunchTimeout);
        queuedExternalIntent = null;
        externalLaunchUntil = 0;
        pendingExternalPackage = null;
    }
    @Override public void onDestroy() {
        if (powerActions != null) powerActions.close();
        if (homeWatcher != null) homeWatcher.close();
        destroyOverlay();handler.removeCallbacksAndMessages(null);
        if (receiversRegistered) {
            unregisterReceiver(screenReceiver);
            unregisterReceiver(packageReceiver);
            receiversRegistered = false;
        }
        resumedActivity = null;
        queuedExternalIntent = null;
        if(instance==this)instance=null;
        MainActivity current=MainActivity.currentHome();
        if(current!=null&&current.isHomeResumed()){current.surface().setVisibility(android.view.View.VISIBLE);current.surface().setSurfaceActive(true);}
        super.onDestroy();
    }

    // ---- on/off from the launcher's UI -----------------------------------------------------------

    static boolean isEnabled(Context ctx) {
        String on = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(on==null)return false;for(String value:on.split(":"))if(component(ctx).equals(ComponentName.unflattenFromString(value)))return true;return false;
    }

    /** False when the app lacks WRITE_SECURE_SETTINGS. */
    static boolean setEnabled(Context ctx, boolean on) {
        try {
            String current = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            java.util.LinkedHashSet<String> services = new java.util.LinkedHashSet<>();
            if (current != null && !current.isEmpty()) {
                for (String service : current.split(":")) {
                    if (!service.isEmpty() && !component(ctx).equals(ComponentName.unflattenFromString(service))) {
                        services.add(service);
                    }
                }
            }
            if (on) services.add(component(ctx).flattenToString());
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    android.text.TextUtils.join(":", services));
            Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, services.isEmpty() ? 0 : 1);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    static void refreshHome(){new Handler(Looper.getMainLooper()).post(()->{
        if(instance!=null&&instance.overlay!=null)instance.overlay.refresh();
        HomeWatcherService watcher = HomeWatcherService.getInstance();
        if(watcher!=null)watcher.refreshOverlay();
        MainActivity current=MainActivity.currentHome();if(current!=null)current.surface().refresh();
    });}

    static boolean isBound() { return instance != null; }

    static org.json.JSONObject health(Context context)throws org.json.JSONException{
        return new org.json.JSONObject()
                .put("configured",isEnabled(context))
                .put("connected",(isBound()||HomeWatcherService.isRunning())&&isEnabled(context))
                .put("connectionEpoch",connectionEpoch > 0 ? connectionEpoch : HomeWatcherService.getConnectionEpoch())
                .put("interactive",((android.os.PowerManager)context.getSystemService(POWER_SERVICE)).isInteractive())
                .put("overlay",(instance!=null&&instance.attached) || HomeWatcherService.isOverlayAttached())
                .put("readLogs",context.checkSelfPermission(android.Manifest.permission.READ_LOGS)==android.content.pm.PackageManager.PERMISSION_GRANTED);
    }
    static boolean repairHome(Context context){
        if(!isEnabled(context))return false;
        HomeWatcherService.startIfNecessary(context);
        // Disabling the service can make a provider-only process immediately killable.
        // Move the repair into a resumed activity before disconnecting the service.
        try{context.startActivity(new Intent(context,MainActivity.class).putExtra("repair_home",true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION));return true;}
        catch(RuntimeException e){Log.w(TAG,"Cannot open Home for repair",e);return false;}
    }
    static void repairFromActivity(MainActivity activity){
        if(!setEnabled(activity,false))return;
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            setEnabled(activity,true);
            HomeWatcherService.startIfNecessary(activity);
        },350);
    }
    static boolean chooseHome(Context context,boolean custom){
        if(!setEnabled(context,custom))return false;
        if(custom)HomeWatcherService.startIfNecessary(context);
        Context app=context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(()->{
            if(!custom){
                if(instance!=null){instance.handler.removeCallbacksAndMessages(null);instance.cancelRedirect();instance.clearExternalLaunch();instance.destroyOverlay();if(instance.homeWatcher!=null){instance.homeWatcher.close();instance.homeWatcher=null;}}
                HomeWatcherService watcher=HomeWatcherService.getInstance();
                if(watcher!=null)watcher.hideOverlay();
            }
            Intent intent=custom?new Intent(app,MainActivity.class):new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(AMAZON_LAUNCHER);
            try{app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION));}
            catch(RuntimeException e){Toast.makeText(app,"Open your Home screen from the remote",Toast.LENGTH_LONG).show();}
        });
        return true;
    }

    private static ComponentName component(Context ctx) {
        return new ComponentName(ctx, HomeRedirectService.class);
    }
}
