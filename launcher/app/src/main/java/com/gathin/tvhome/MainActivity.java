package com.gathin.tvhome;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
public class MainActivity extends Activity {
    private static java.lang.ref.WeakReference<MainActivity> current=new java.lang.ref.WeakReference<>(null);
    private HomeSurface surface;
    private boolean resumed;
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); overridePendingTransition(0,0); getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff080f1b)); surface=new HomeSurface(this); setContentView(surface); }
    @Override public void onResume() { super.onResume(); resumed=true; current=new java.lang.ref.WeakReference<>(this); surface.setVisibility(android.view.View.VISIBLE);surface.setSurfaceActive(true);surface.refresh(); HomeRedirectService.activityReady(this,surface); HomeWatcherService.startIfNecessary(this); if(getIntent().getBooleanExtra("repair_home",false)){getIntent().removeExtra("repair_home");HomeRedirectService.repairFromActivity(this);} }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); }
    @Override protected void onPause() { resumed=false; if(current.get()==this)current.clear(); HomeRedirectService.activityPaused(this); surface.setSurfaceActive(false);surface.dismissMenu(); super.onPause(); overridePendingTransition(0,0); }
    @Override public void onBackPressed() { surface.goHome(); }
    boolean isHomeResumed() { return resumed; }
    HomeSurface surface() { return surface; }
    static MainActivity currentHome(){return current.get();}
}
