package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fullscreen live TV player for IPTV streams.
 * Supports D-pad channel zapping (Up/Down), elegant OSD overlay, and low-latency HLS playback.
 */
public class IptvPlayerActivity extends Activity {
    public static final String EXTRA_CHANNEL_INDEX = "channel_index";
    public static final String EXTRA_CHANNELS = "channels";

    private VideoView videoView;
    private FrameLayout rootLayout;
    private View osdContainer;
    private TextView titleText, metaText, zapText, errorText;
    private ImageView logoView;
    private ProgressBar bufferSpinner;

    private List<IptvChannel> channelList = new ArrayList<>();
    private int currentIndex = 0;
    private int currentBackupIndex = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isBuffering = true;

    private final Runnable hideOsdRunnable = () -> {
        if (osdContainer != null) {
            osdContainer.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                if (osdContainer != null) osdContainer.setVisibility(View.GONE);
            }).start();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        NetOverlayService.hide(this);

        Intent intent = getIntent();
        if (intent != null) {
            currentIndex = intent.getIntExtra(EXTRA_CHANNEL_INDEX, 0);
        }
        channelList = IptvManager.getActivePlaylist();

        buildUi();
        playChannel(currentIndex);
    }

    private void buildUi() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);
        FrameLayout.LayoutParams vLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        rootLayout.addView(videoView, vLp);

        // Loading spinner
        bufferSpinner = new ProgressBar(this);
        FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(
                dp(64), dp(64), Gravity.CENTER);
        rootLayout.addView(bufferSpinner, spinLp);

        // Error notice
        errorText = new TextView(this);
        errorText.setTextColor(0xFFEF4444); // Red
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        errorText.setGravity(Gravity.CENTER);
        errorText.setVisibility(View.GONE);
        FrameLayout.LayoutParams errLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        rootLayout.addView(errorText, errLp);

        // Top-left elegant OSD banner
        osdContainer = createOsd();
        FrameLayout.LayoutParams osdLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        osdLp.setMargins(dp(36), dp(32), dp(36), 0);
        rootLayout.addView(osdContainer, osdLp);

        // Zap badge in top-right
        zapText = new TextView(this);
        zapText.setTextColor(0xAAFFFFFF);
        zapText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        zapText.setText("▲ ▼ Change Channel   •   BACK to Exit");
        FrameLayout.LayoutParams zapLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        zapLp.setMargins(0, dp(36), dp(40), 0);
        rootLayout.addView(zapText, zapLp);

        setContentView(rootLayout);

        setupVideoListeners();
    }

    private View createOsd() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(18), dp(12), dp(22), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xDD0D111E); // Frosted dark slate
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x33FFFFFF);
        box.setBackground(bg);

        logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoLp.setMarginEnd(dp(14));
        box.addView(logoView, logoLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        titleText = new TextView(this);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleText.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        textCol.addView(titleText);

        metaText = new TextView(this);
        metaText.setTextColor(0xFF38BDF8); // Cyan
        metaText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        textCol.addView(metaText);

        box.addView(textCol);
        return box;
    }

    private void setupVideoListeners() {
        videoView.setOnPreparedListener(mp -> {
            isBuffering = false;
            bufferSpinner.setVisibility(View.GONE);
            errorText.setVisibility(View.GONE);
            mp.start();
            showOsd();
        });

        videoView.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                isBuffering = true;
                bufferSpinner.setVisibility(View.VISIBLE);
            } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                isBuffering = false;
                bufferSpinner.setVisibility(View.GONE);
            }
            return true;
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            isBuffering = false;
            bufferSpinner.setVisibility(View.GONE);
            IptvChannel ch = (currentIndex >= 0 && currentIndex < channelList.size()) ? channelList.get(currentIndex) : null;
            if (ch != null && ch.backupUrls != null && currentBackupIndex < ch.backupUrls.size()) {
                String backupUrl = ch.backupUrls.get(currentBackupIndex++);
                AppLog.i("Primary stream failed, attempting backup: " + backupUrl);
                try {
                    videoView.stopPlayback();
                    videoView.setVideoURI(Uri.parse(backupUrl), getStreamHeaders());
                    bufferSpinner.setVisibility(View.VISIBLE);
                    isBuffering = true;
                    showOsd();
                    return true;
                } catch (Exception ignored) {}
            }
            errorText.setText("Stream unavailable.\nPress ▲ / ▼ for next channel or OK to open in VLC.");
            errorText.setVisibility(View.VISIBLE);
            showOsd();
            return true;
        });
    }

    private void playChannel(int index) {
        if (channelList.isEmpty()) return;
        if (index < 0) index = channelList.size() - 1;
        if (index >= channelList.size()) index = 0;
        currentIndex = index;
        currentBackupIndex = 0;

        IptvChannel ch = channelList.get(currentIndex);
        titleText.setText(ch.name);

        String meta = (ch.group != null ? ch.group.toUpperCase() : "LIVE")
                + "  •  " + (ch.resolution != null && !ch.resolution.isEmpty() ? ch.resolution : "HD")
                + "  •  #" + (currentIndex + 1) + "/" + channelList.size();
        metaText.setText(meta);

        logoView.setImageDrawable(null);
        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            logoView.setVisibility(View.VISIBLE);
            final int zapSeq = currentIndex;
            logoView.setTag(zapSeq);
            IptvManager.loadLogo(ch.logoUrl, bmp -> {
                if (bmp != null && Integer.valueOf(zapSeq).equals(logoView.getTag())) {
                    logoView.setImageBitmap(bmp);
                }
            });
        } else {
            logoView.setVisibility(View.GONE);
        }

        errorText.setVisibility(View.GONE);
        bufferSpinner.setVisibility(View.VISIBLE);
        isBuffering = true;
        showOsd();

        try {
            videoView.stopPlayback();
            videoView.setVideoURI(Uri.parse(ch.url), getStreamHeaders());
        } catch (Exception e) {
            errorText.setText("Failed to connect: " + e.getMessage());
            errorText.setVisibility(View.VISIBLE);
            bufferSpinner.setVisibility(View.GONE);
        }
    }

    private static java.util.Map<String, String> getStreamHeaders() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 VLC/3.0.18");
        map.put("Accept", "*/*");
        map.put("Connection", "keep-alive");
        return map;
    }

    private void showOsd() {
        handler.removeCallbacks(hideOsdRunnable);
        if (osdContainer != null) {
            osdContainer.setVisibility(View.VISIBLE);
            osdContainer.animate().alpha(1f).setDuration(200).start();
        }
        if (zapText != null) {
            zapText.setVisibility(View.VISIBLE);
            zapText.animate().alpha(1f).setDuration(200).start();
        }
        handler.postDelayed(hideOsdRunnable, 4500);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                playChannel(currentIndex - 1);
                return true;
            } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                playChannel(currentIndex + 1);
                return true;
            } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (errorText.getVisibility() == View.VISIBLE) {
                    openInVlc();
                    return true;
                }
                showOsd();
                return true;
            } else if (code == KeyEvent.KEYCODE_MENU || code == 312 /* KEYCODE_QUICK_OPTIONS */) {
                if (!channelList.isEmpty()) {
                    IptvManager.toggleStar(this, channelList.get(currentIndex));
                    showOsd();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void openInVlc() {
        if (channelList.isEmpty()) return;
        IptvChannel ch = channelList.get(currentIndex);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(ch.url), "video/*");
            intent.setPackage("org.videolan.vlc");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
            try {
                Intent generic = new Intent(Intent.ACTION_VIEW);
                generic.setDataAndType(Uri.parse(ch.url), "video/*");
                generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(generic);
            } catch (Exception e) {
                errorText.setText("No external video player installed.");
            }
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(hideOsdRunnable);
        if (videoView != null) {
            videoView.stopPlayback();
            videoView.suspend();
            videoView = null;
        }
        NetOverlayService.show(this);
        super.onDestroy();
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
