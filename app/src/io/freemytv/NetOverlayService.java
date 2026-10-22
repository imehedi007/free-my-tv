package io.freemytv;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.Locale;

/**
 * System-wide floating network speed monitor.
 * Displays a modern frosted-glass capsule in the bottom-left corner across all apps
 * (YouTube, Netflix, Live TV, settings, etc.) without intercepting any remote control focus or touch events.
 */
public class NetOverlayService extends Service {
    private static volatile boolean running;
    private WindowManager windowManager;
    private NetOverlayView overlayView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private long lastRxBytes = -1, lastTxBytes = -1, lastNetTime;
    private String downVal = "0", downUnit = "Kb/s";
    private String upVal = "0", upUnit = "Kb/s";

    private final Runnable netTick = new Runnable() {
        @Override public void run() {
            updateSpeed();
            handler.postDelayed(this, 1000);
        }
    };

    private static volatile NetOverlayService instance;

    public static void start(Context context) {
        if (!Prefs.netMonitor()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return;
        }
        try {
            Intent intent = new Intent(context, NetOverlayService.class);
            context.startService(intent);
        } catch (Throwable ignored) {
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, NetOverlayService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void hide() {
        NetOverlayService s = instance;
        if (s != null && s.overlayView != null) {
            s.handler.post(() -> {
                if (s.overlayView != null) s.overlayView.setVisibility(View.GONE);
            });
        }
    }

    public static void show() {
        NetOverlayService s = instance;
        if (s != null && s.overlayView != null) {
            s.handler.post(() -> {
                if (s.overlayView != null) s.overlayView.setVisibility(View.VISIBLE);
            });
        }
    }

    public static boolean isRunning() {
        return running;
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        Ui.init(this);
        overlayView = new NetOverlayView(this);

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        float h = getResources().getDisplayMetrics().heightPixels;
        float s = Math.max(1f, h / 720f);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = Math.round(28 * s);
        lp.y = Math.round(22 * s);

        try {
            windowManager.addView(overlayView, lp);
            instance = this;
            running = true;
            handler.post(netTick);
        } catch (Throwable t) {
            stopSelf();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (overlayView != null && windowManager != null && !running) {
            instance = this;
            handler.removeCallbacks(netTick);
            handler.post(netTick);
            running = true;
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        running = false;
        handler.removeCallbacks(netTick);
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Throwable ignored) {
            }
            overlayView = null;
        }
        super.onDestroy();
    }

    private void updateSpeed() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        long now = SystemClock.elapsedRealtime();
        if (rx < 0 || tx < 0) return;

        if (lastRxBytes >= 0 && lastTxBytes >= 0 && now > lastNetTime) {
            long dt = now - lastNetTime;
            long rxBytes = rx >= lastRxBytes ? rx - lastRxBytes : 0;
            long txBytes = tx >= lastTxBytes ? tx - lastTxBytes : 0;
            long rxBps = rxBytes * 1000L / dt;
            long txBps = txBytes * 1000L / dt;

            Speed down = parseSpeed(rxBps);
            Speed up = parseSpeed(txBps);

            if (!down.val.equals(downVal) || !down.unit.equals(downUnit)
                    || !up.val.equals(upVal) || !up.unit.equals(upUnit)) {
                downVal = down.val;
                downUnit = down.unit;
                upVal = up.val;
                upUnit = up.unit;
                if (overlayView != null) {
                    overlayView.setSpeeds(downVal, downUnit, upVal, upUnit);
                }
            }
        }
        lastRxBytes = rx;
        lastTxBytes = tx;
        lastNetTime = now;
    }

    private static class Speed {
        final String val;
        final String unit;
        Speed(String val, String unit) { this.val = val; this.unit = unit; }
    }

    private static Speed parseSpeed(long bytesPerSec) {
        if (bytesPerSec < 128L) return new Speed("0", "Kb/s");
        if (bytesPerSec < 1024L * 1024L) {
            return new Speed(String.valueOf(bytesPerSec / 1024L), "Kb/s");
        }
        float mb = bytesPerSec / (1024f * 1024f);
        if (mb >= 10f) {
            return new Speed(String.valueOf(Math.round(mb)), "Mb/s");
        } else {
            return new Speed(String.format(Locale.US, "%.1f", mb), "Mb/s");
        }
    }

    /**
     * Modern floating capsule pill view.
     */
    private static class NetOverlayView extends View {
        private final float s;
        private final RectF pillRect = new RectF();
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint downArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint upArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valPaint;
        private final Paint unitPaint;

        private String dVal = "0", dUnit = "Kb/s";
        private String uVal = "0", uUnit = "Kb/s";

        private final float pillHeight;
        private final float pillRadius;
        private final float iconSize;
        private final float padH;
        private final float spaceIconText;
        private final float spaceValUnit;
        private final float spaceSection;

        NetOverlayView(Context context) {
            super(context);
            float h = context.getResources().getDisplayMetrics().heightPixels;
            s = Math.max(1f, h / 720f);

            pillHeight = 36f * s;
            pillRadius = pillHeight / 2f;
            iconSize = 13f * s;
            padH = 14f * s;
            spaceIconText = 6f * s;
            spaceValUnit = 3.5f * s;
            spaceSection = 11f * s;

            // Deep slate frosted glass background
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0xDD0A0F1D); // ~87% opacity deep slate-navy

            // Subtle glowing cyan rim
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(0x4538BDF8);
            borderPaint.setStrokeWidth(1.1f * s);

            // Hairline separator
            dividerPaint.setStyle(Paint.Style.STROKE);
            dividerPaint.setColor(0x28FFFFFF);
            dividerPaint.setStrokeWidth(1.0f * s);

            // Download arrow paint (Emerald mint)
            downArrowPaint.setStyle(Paint.Style.STROKE);
            downArrowPaint.setColor(0xFF10B981);
            downArrowPaint.setStrokeWidth(2.0f * s);
            downArrowPaint.setStrokeCap(Paint.Cap.ROUND);
            downArrowPaint.setStrokeJoin(Paint.Join.ROUND);

            // Upload arrow paint (Electric sky blue)
            upArrowPaint.setStyle(Paint.Style.STROKE);
            upArrowPaint.setColor(0xFF38BDF8);
            upArrowPaint.setStrokeWidth(2.0f * s);
            upArrowPaint.setStrokeCap(Paint.Cap.ROUND);
            upArrowPaint.setStrokeJoin(Paint.Join.ROUND);

            // Value typography (bold white with tabular digits)
            Typeface boldFont = Ui.semibold != null ? Ui.semibold : Typeface.create("sans-serif-medium", Typeface.BOLD);
            valPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            valPaint.setTypeface(boldFont);
            valPaint.setTextSize(16.5f * s);
            valPaint.setColor(0xFFFFFFFF);
            valPaint.setFontFeatureSettings("'tnum'");

            // Unit typography (slate-400 clean muted)
            Typeface unitFont = Ui.medium != null ? Ui.medium : Typeface.create("sans-serif", Typeface.NORMAL);
            unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            unitPaint.setTypeface(unitFont);
            unitPaint.setTextSize(12f * s);
            unitPaint.setColor(0xFF94A3B8);
        }

        void setSpeeds(String dVal, String dUnit, String uVal, String uUnit) {
            this.dVal = dVal;
            this.dUnit = dUnit;
            this.uVal = uVal;
            this.uUnit = uUnit;
            requestLayout();
            invalidate();
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float dValW = valPaint.measureText(dVal);
            float dUnitW = unitPaint.measureText(dUnit);
            float uValW = valPaint.measureText(uVal);
            float uUnitW = unitPaint.measureText(uUnit);

            float totalW = padH + iconSize + spaceIconText + dValW + spaceValUnit + dUnitW
                    + spaceSection + dividerPaint.getStrokeWidth() + spaceSection
                    + iconSize + spaceIconText + uValW + spaceValUnit + uUnitW + padH;

            setMeasuredDimension(Math.round(totalW), Math.round(pillHeight));
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            // 1. Draw modern glass pill
            float strokeHalf = borderPaint.getStrokeWidth() / 2f;
            pillRect.set(strokeHalf, strokeHalf, w - strokeHalf, h - strokeHalf);
            c.drawRoundRect(pillRect, pillRadius, pillRadius, bgPaint);
            c.drawRoundRect(pillRect, pillRadius, pillRadius, borderPaint);

            float cy = h / 2f;
            float valBaseline = cy + Ui.halfCap(valPaint);
            float unitBaseline = valBaseline; // Shared baseline for perfect alignment

            // 2. Download section
            float x = padH;
            drawArrowBadge(c, x + iconSize / 2f, cy, iconSize, true, downArrowPaint);
            x += iconSize + spaceIconText;

            c.drawText(dVal, x, valBaseline, valPaint);
            x += valPaint.measureText(dVal) + spaceValUnit;

            c.drawText(dUnit, x, unitBaseline, unitPaint);
            x += unitPaint.measureText(dUnit) + spaceSection;

            // 3. Hairline divider
            float divHalf = 8.5f * s;
            c.drawLine(x, cy - divHalf, x, cy + divHalf, dividerPaint);
            x += dividerPaint.getStrokeWidth() + spaceSection;

            // 4. Upload section
            drawArrowBadge(c, x + iconSize / 2f, cy, iconSize, false, upArrowPaint);
            x += iconSize + spaceIconText;

            c.drawText(uVal, x, valBaseline, valPaint);
            x += valPaint.measureText(uVal) + spaceValUnit;

            c.drawText(uUnit, x, unitBaseline, unitPaint);
        }

        private static void drawArrowBadge(Canvas c, float cx, float cy, float s, boolean down, Paint paint) {
            float half = s / 2f;
            float barY = cy + half;
            // Base tray
            c.drawLine(cx - half * 0.9f, barY, cx + half * 0.9f, barY, paint);

            // Vertical arrow shaft & wings
            float stroke = paint.getStrokeWidth();
            float arrowTipY = down ? barY - 2.5f * stroke : cy - half;
            float arrowStartY = down ? cy - half : barY - 2.5f * stroke;
            c.drawLine(cx, arrowStartY, cx, arrowTipY, paint);

            float wing = s * 0.35f;
            float wingY = down ? arrowTipY - wing : arrowTipY + wing;
            c.drawLine(cx - wing, wingY, cx, arrowTipY, paint);
            c.drawLine(cx + wing, wingY, cx, arrowTipY, paint);
        }
    }
}
