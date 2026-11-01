package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Return to the user's enabled Home after a real device boot. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ((!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) || !HomeRedirectService.isEnabled(context)) return;
        HomeWatcherService.startIfNecessary(context);
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())&&HomeRedirectService.bootCompleted())return;
        context.getSharedPreferences("tvhome", Context.MODE_PRIVATE).edit().putBoolean("resume_home", true).apply();
        try{context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));}catch(RuntimeException e){android.util.Log.w("HomeRedirect","Home launch deferred until service reconnects",e);}
    }
}
