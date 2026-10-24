package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modern Android TV Dashboard for IPTV.
 * Pinned South Asian country bar, category filters, and channel grid.
 */
public class IptvActivity extends Activity {
    private LinearLayout rootLayout;
    private LinearLayout countriesRow;
    private HorizontalScrollView countriesScroll;
    private LinearLayout categoriesRow;
    private HorizontalScrollView categoriesScroll;
    private GridLayout channelsGrid;
    private ScrollView gridScroll;
    private ProgressBar loadingBar;
    private TextView emptyText;
    private TextView headerTitle;
    private TextView headerCount;

    private List<IptvCountry> countryList = new ArrayList<>();
    private IptvCountry selectedCountry = IptvManager.SOUTH_ASIA.get(0); // Default: Bangladesh
    private List<IptvChannel> fullChannelList = new ArrayList<>();
    private List<IptvChannel> filteredChannels = new ArrayList<>();
    private String selectedCategory = "All";
    private boolean isStarredMode = false;

    private static final String[] CATEGORIES = {
        "All", "News", "Sports", "Entertainment", "Kids", "Movies", "Music", "General"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        buildUi();

        // Load country list and default to Bangladesh
        loadCountries();
        loadChannels(selectedCountry.code);
    }

    private void buildUi() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFF090D1A); // Apple TV dark navy
        rootLayout.setPadding(dp(32), dp(24), dp(32), dp(20));

        // 1. Header Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        headerTitle = new TextView(this);
        headerTitle.setText("Live TV  •  🇧🇩 Bangladesh");
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        headerTitle.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        headerCount = new TextView(this);
        headerCount.setTextColor(0xFF38BDF8); // Cyan
        headerCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        headerCount.setText("Loading channels…");
        header.addView(headerCount);

        rootLayout.addView(header);

        // 2. Pinned South Asian Countries Bar
        TextView pLabel = new TextView(this);
        pLabel.setText("PINNED COUNTRIES");
        pLabel.setTextColor(0x88FFFFFF);
        pLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pLabel.setPadding(0, dp(14), 0, dp(6));
        rootLayout.addView(pLabel);

        countriesScroll = new HorizontalScrollView(this);
        countriesScroll.setHorizontalScrollBarEnabled(false);
        countriesRow = new LinearLayout(this);
        countriesRow.setOrientation(LinearLayout.HORIZONTAL);
        countriesScroll.addView(countriesRow);
        rootLayout.addView(countriesScroll);

        // 3. Category Filter Row
        categoriesScroll = new HorizontalScrollView(this);
        categoriesScroll.setHorizontalScrollBarEnabled(false);
        categoriesScroll.setPadding(0, dp(10), 0, dp(12));
        categoriesRow = new LinearLayout(this);
        categoriesRow.setOrientation(LinearLayout.HORIZONTAL);
        buildCategoryPills();
        categoriesScroll.addView(categoriesRow);
        rootLayout.addView(categoriesScroll);

        // 4. Main Scroll & Channel Grid
        FrameLayout contentFrame = new FrameLayout(this);
        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);

        channelsGrid = new GridLayout(this);
        channelsGrid.setColumnCount(4);
        channelsGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        channelsGrid.setRowOrderPreserved(false);
        channelsGrid.setUseDefaultMargins(true);
        gridScroll.addView(channelsGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentFrame.addView(gridScroll);

        loadingBar = new ProgressBar(this);
        FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER);
        contentFrame.addView(loadingBar, spinLp);

        emptyText = new TextView(this);
        emptyText.setText("No channels available for this category.");
        emptyText.setTextColor(0x88FFFFFF);
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        contentFrame.addView(emptyText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        rootLayout.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(rootLayout);
    }

    private void loadCountries() {
        IptvManager.getCountries(this, new IptvManager.Callback<List<IptvCountry>>() {
            @Override public void onResult(List<IptvCountry> list) {
                countryList = list;
                buildCountryPills();
            }

            @Override public void onError(Exception e) {
                countryList = new ArrayList<>(IptvManager.SOUTH_ASIA);
                buildCountryPills();
            }
        });
    }

    private void buildCountryPills() {
        countriesRow.removeAllViews();

        // 1. Starred pill
        View starPill = createPill("⭐ Starred", isStarredMode, v -> {
            isStarredMode = true;
            headerTitle.setText("Live TV  •  ⭐ Starred Channels");
            fullChannelList = IptvManager.getStarredChannels(this);
            applyFilter();
            buildCountryPills();
        });
        countriesRow.addView(starPill);

        // 2. Country pills
        for (IptvCountry c : countryList) {
            boolean isSelected = !isStarredMode && selectedCountry != null && selectedCountry.code.equals(c.code);
            String title = c.flag + " " + c.name;
            View pill = createPill(title, isSelected, v -> {
                isStarredMode = false;
                selectedCountry = c;
                headerTitle.setText("Live TV  •  " + c.flag + " " + c.name);
                loadChannels(c.code);
                buildCountryPills();
            });
            countriesRow.addView(pill);
        }
    }

    private void buildCategoryPills() {
        categoriesRow.removeAllViews();
        for (String cat : CATEGORIES) {
            boolean isSelected = cat.equals(selectedCategory);
            View pill = createPill(cat, isSelected, v -> {
                selectedCategory = cat;
                buildCategoryPills();
                applyFilter();
            });
            categoriesRow.addView(pill);
        }
    }

    private View createPill(String title, boolean isSelected, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        tv.setPadding(dp(16), dp(8), dp(16), dp(8));
        tv.setFocusable(true);
        tv.setClickable(true);

        updatePillStyle(tv, isSelected, false);

        tv.setOnFocusChangeListener((v, hasFocus) -> {
            updatePillStyle(tv, isSelected, hasFocus);
            if (hasFocus) {
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
        });

        tv.setOnClickListener(onClick);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(10));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void updatePillStyle(TextView tv, boolean isSelected, boolean hasFocus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (hasFocus) {
            bg.setColor(0xFF0284C7); // Electric sky cyan
            bg.setStroke(dp(2), Color.WHITE);
            tv.setTextColor(Color.WHITE);
        } else if (isSelected) {
            bg.setColor(0x440284C7);
            bg.setStroke(dp(1.5f), 0xFF38BDF8);
            tv.setTextColor(0xFF38BDF8);
        } else {
            bg.setColor(0x22FFFFFF);
            bg.setStroke(dp(1), 0x22FFFFFF);
            tv.setTextColor(0xCCFFFFFF);
        }
        tv.setBackground(bg);
    }

    private void loadChannels(String countryCode) {
        loadingBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        channelsGrid.removeAllViews();
        headerCount.setText("Loading playlist…");

        IptvManager.getChannels(this, countryCode, new IptvManager.Callback<List<IptvChannel>>() {
            @Override public void onResult(List<IptvChannel> channels) {
                loadingBar.setVisibility(View.GONE);
                fullChannelList = channels != null ? channels : new ArrayList<>();
                applyFilter();
            }

            @Override public void onError(Exception e) {
                loadingBar.setVisibility(View.GONE);
                fullChannelList = new ArrayList<>();
                applyFilter();
                Toast.makeText(IptvActivity.this, "Network error loading channels", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int displayLimit = 60;

    private void applyFilter() {
        displayLimit = 60;
        filteredChannels.clear();
        for (IptvChannel ch : fullChannelList) {
            if ("All".equals(selectedCategory) || (ch.group != null && ch.group.equalsIgnoreCase(selectedCategory))) {
                filteredChannels.add(ch);
            }
        }

        headerCount.setText(filteredChannels.size() + " channels");
        populateGrid();
    }

    private void populateGrid() {
        channelsGrid.removeAllViews();
        if (filteredChannels.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        emptyText.setVisibility(View.GONE);

        int screenW = getResources().getDisplayMetrics().widthPixels - dp(64);
        int cardW = Math.max(dp(180), (screenW - dp(36)) / 4);

        int count = Math.min(filteredChannels.size(), displayLimit);
        for (int i = 0; i < count; i++) {
            final int index = i;
            final IptvChannel ch = filteredChannels.get(i);
            View card = createChannelCard(ch, cardW, index);
            channelsGrid.addView(card);
        }

        if (filteredChannels.size() > displayLimit) {
            int remaining = filteredChannels.size() - displayLimit;
            View loadMore = createLoadMoreCard(cardW, remaining);
            channelsGrid.addView(loadMore);
        }
    }

    private View createLoadMoreCard(int cardW, int remaining) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(12), dp(24), dp(12), dp(24));

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(0x440284C7);
        normalBg.setCornerRadius(dp(16));
        normalBg.setStroke(dp(1.5f), 0xFF38BDF8);
        card.setBackground(normalBg);

        TextView icon = new TextView(this);
        icon.setText("➕");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        TextView text = new TextView(this);
        text.setText("Load More\n(" + remaining + " remaining)");
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(6), 0, 0);
        card.addView(text);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                GradientDrawable focusedBg = new GradientDrawable();
                focusedBg.setColor(0x880284C7);
                focusedBg.setCornerRadius(dp(16));
                focusedBg.setStroke(dp(2.5f), Color.WHITE);
                v.setBackground(focusedBg);
                v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(160).start();
            } else {
                v.setBackground(normalBg);
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            }
        });

        card.setOnClickListener(v -> {
            displayLimit += 60;
            populateGrid();
        });

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = cardW;
        glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        glp.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(glp);
        return card;
    }

    private View createChannelCard(IptvChannel ch, int cardW, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(12), dp(14), dp(12), dp(12));

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(0x331E293B); // Slate glass
        normalBg.setCornerRadius(dp(16));
        normalBg.setStroke(dp(1), 0x22FFFFFF);
        card.setBackground(normalBg);

        // Logo Container
        FrameLayout logoFrame = new FrameLayout(this);
        ImageView logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(dp(72), dp(52), Gravity.CENTER);
        logoFrame.addView(logoView, lParams);

        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            logoView.setTag(ch.logoUrl);
            IptvManager.loadLogo(ch.logoUrl, bmp -> {
                if (bmp != null && ch.logoUrl.equals(logoView.getTag())) {
                    logoView.setImageBitmap(bmp);
                }
            });
        }
        card.addView(logoFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        // Channel Name
        TextView nameText = new TextView(this);
        nameText.setText(ch.name);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameText.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        nameText.setGravity(Gravity.CENTER);
        nameText.setSingleLine(true);
        nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameText.setPadding(0, dp(6), 0, dp(4));
        card.addView(nameText);

        // Badges row: category + resolution + star
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER);

        TextView groupBadge = new TextView(this);
        groupBadge.setText(ch.group != null ? ch.group : "Live");
        groupBadge.setTextColor(0xFF38BDF8);
        groupBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        metaRow.addView(groupBadge);

        if (ch.resolution != null && !ch.resolution.isEmpty()) {
            TextView resBadge = new TextView(this);
            resBadge.setText(" • " + ch.resolution);
            resBadge.setTextColor(0xFFF59E0B); // Gold
            resBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            metaRow.addView(resBadge);
        }

        if (ch.isStarred) {
            TextView starBadge = new TextView(this);
            starBadge.setText(" • ⭐");
            starBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            metaRow.addView(starBadge);
        }

        card.addView(metaRow);

        // Focus & Click Listeners
        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                GradientDrawable focusedBg = new GradientDrawable();
                focusedBg.setColor(0x881E293B);
                focusedBg.setCornerRadius(dp(16));
                focusedBg.setStroke(dp(2.5f), 0xFF38BDF8);
                v.setBackground(focusedBg);
                v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(160).start();
            } else {
                v.setBackground(normalBg);
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            }
        });

        card.setOnClickListener(v -> {
            IptvManager.setActivePlaylist(filteredChannels);
            Intent intent = new Intent(IptvActivity.this, IptvPlayerActivity.class);
            intent.putExtra(IptvPlayerActivity.EXTRA_CHANNEL_INDEX, index);
            startActivity(intent);
        });

        card.setOnLongClickListener(v -> {
            IptvManager.toggleStar(this, ch);
            Toast.makeText(this, ch.isStarred ? "⭐ Added to Starred" : "Removed from Starred", Toast.LENGTH_SHORT).show();
            applyFilter();
            return true;
        });

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = cardW;
        glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        glp.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(glp);

        return card;
    }

    @Override protected void onResume() {
        super.onResume();
        // Refresh starred state on resume
        if (isStarredMode) {
            fullChannelList = IptvManager.getStarredChannels(this);
            applyFilter();
        }
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
