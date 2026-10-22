package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 */
public class RemoteKeyService extends AccessibilityService {
    @Override protected boolean onKeyEvent(KeyEvent event) {
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                String p = pkg.toString();
                if ("com.amazon.amazonvideo.livingroom".equals(p) || "com.netflix.ninja".equals(p)) {
                    NetOverlayService.hide(this);
                } else if ("io.freemytv".equals(p)
                        || "com.google.android.youtube.tv".equals(p)
                        || "app.blaze.spofficial".equals(p)
                        || "org.videolan.vlc".equals(p)) {
                    NetOverlayService.show(this);
                }
            }
        }
    }

    @Override public void onInterrupt() {}
}
