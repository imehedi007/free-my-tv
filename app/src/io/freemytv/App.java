package io.freemytv;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Global Application class with autonomous OS-level memory management:
 * 1. Responds dynamically to Android onTrimMemory() signals across the entire process.
 * 2. Self-heals on standby/screen-off (ACTION_SCREEN_OFF) to prevent memory fragmentation.
 */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        registerStandbyReceiver();
    }

    private void registerStandbyReceiver() {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        AppLog.i("Screen OFF: TV entering standby. Executing autonomous self-healing memory trim.");
                        trimAll(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
                    }
                }
            }, filter);
        } catch (Throwable ignored) {}
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        trimAll(level);
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        trimAll(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
    }

    private void trimAll(int level) {
        try {
            HomeApps.trimMemory(level);
            IptvManager.trimMemory(level);
            Recs.get(this).trim();

            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                System.runFinalization();
                System.gc();
            }
        } catch (Throwable ignored) {}
    }
}
