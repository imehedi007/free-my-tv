package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modern Split-Screen Android TV Dashboard for Live TV / IPTV.
 * Left Panel: Pinned countries 3x3 grid & All Countries modal.
 * Right Panel: Country header, category chips, and 3-column channel grid.
 */
public class IptvActivity extends Activity {
    private LinearLayout rootLayout;
    private GridLayout pinnedGrid;
    private LinearLayout categoriesRow;
    private HorizontalScrollView categoriesScroll;
    private GridLayout channelsGrid;
    private ScrollView gridScroll;
    private ProgressBar loadingBar;
    private TextView emptyText;
    private TextView rightCountryTitle;
    private TextView rightChannelCount;
    private View searchBtn;
    private View settingsBtn;
    private TextView editBtn;

    private List<IptvCountry> allCountries = new ArrayList<>();
    private final List<IptvCountry> pinnedCountries = new ArrayList<>();
    private IptvCountry selectedCountry = IptvManager.SOUTH_ASIA.get(0); // Default: Bangladesh
    private List<IptvChannel> fullChannelList = new ArrayList<>();
    private List<IptvChannel> filteredChannels = new ArrayList<>();
    private String selectedCategory = "All";
    private boolean isStarredMode = false;
    private String searchQuery = "";

    private final View[] pinnedViews = new View[9];
    private final int[] pinnedIds = new int[9];

    private static final String[] CATEGORIES = {
        "All", "News", "Sports", "Entertainment", "Kids", "Movies", "Music", "General"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        for (int i = 0; i < 9; i++) pinnedIds[i] = View.generateViewId();
        buildUi();

        loadAllCountries();
        loadChannels(selectedCountry.code);
    }

    private void buildUi() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFF090E17); // Deep modern dark slate
        rootLayout.setPadding(dp(28), dp(20), dp(28), dp(16));

        // 1. Top Bar: "Live TV" on left, Search | Settings on right
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(14));

        TextView appTitle = new TextView(this);
        appTitle.setText("Live TV");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        appTitle.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        topBar.addView(appTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Right actions: Search | Settings
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        searchBtn = createTopActionButton("🔍 Search", v -> showSearchDialog());
        int sId = View.generateViewId();
        searchBtn.setId(sId);
        actions.addView(searchBtn);

        TextView div = new TextView(this);
        div.setText("|");
        div.setTextColor(0x44FFFFFF);
        div.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        div.setPadding(dp(10), 0, dp(10), 0);
        actions.addView(div);

        settingsBtn = createTopActionButton("⚙ Settings", v -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Settings not available", Toast.LENGTH_SHORT).show();
            }
        });
        int setmId = View.generateViewId();
        settingsBtn.setId(setmId);
        actions.addView(settingsBtn);

        topBar.addView(actions);
        rootLayout.addView(topBar);

        // 2. Main Content: Split Screen (Left: Pinned Countries, Right: Channels)
        LinearLayout mainSplit = new LinearLayout(this);
        mainSplit.setOrientation(LinearLayout.HORIZONTAL);

        // --- Left Panel: Pinned countries ---
        LinearLayout leftPanel = new LinearLayout(this);
        leftPanel.setOrientation(LinearLayout.VERTICAL);
        int leftWidth = dp(310);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(leftWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        leftLp.setMarginEnd(dp(22));
        leftPanel.setLayoutParams(leftLp);

        // Pinned countries header row with Edit button
        LinearLayout pinnedHeader = new LinearLayout(this);
        pinnedHeader.setOrientation(LinearLayout.HORIZONTAL);
        pinnedHeader.setGravity(Gravity.CENTER_VERTICAL);
        pinnedHeader.setPadding(0, 0, 0, dp(10));

        TextView pinnedTitle = new TextView(this);
        pinnedTitle.setText("Pinned countries");
        pinnedTitle.setTextColor(0xFFFFFFFF);
        pinnedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        pinnedTitle.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        pinnedHeader.addView(pinnedTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        editBtn = new TextView(this);
        editBtn.setId(View.generateViewId());
        editBtn.setText("✎ Edit");
        editBtn.setTextColor(0xFF38BDF8);
        editBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        editBtn.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        editBtn.setFocusable(true);
        editBtn.setClickable(true);
        editBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        editBtn.setOnClickListener(v -> showEditPinnedDialog());
        editBtn.setOnFocusChangeListener((v, hasFocus) -> {
            editBtn.setTextColor(hasFocus ? Color.WHITE : 0xFF38BDF8);
            editBtn.setBackgroundColor(hasFocus ? 0x3338BDF8 : Color.TRANSPARENT);
        });
        pinnedHeader.addView(editBtn);
        leftPanel.addView(pinnedHeader);

        // 3x3 Pinned Cards Grid
        pinnedGrid = new GridLayout(this);
        pinnedGrid.setColumnCount(3);
        pinnedGrid.setRowCount(3);
        pinnedGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        pinnedGrid.setUseDefaultMargins(false);
        leftPanel.addView(pinnedGrid);

        // Navigation hint at bottom
        TextView hint = new TextView(this);
        hint.setText("Arrow keys to move • OK to select");
        hint.setTextColor(0x7794A3B8);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setPadding(0, dp(14), 0, 0);
        leftPanel.addView(hint);

        mainSplit.addView(leftPanel);

        // Subtle vertical separator line
        View sep = new View(this);
        sep.setBackgroundColor(0x18FFFFFF);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT);
        sepLp.setMarginEnd(dp(22));
        mainSplit.addView(sep, sepLp);

        // --- Right Panel: Selected Country & Channels ---
        LinearLayout rightPanel = new LinearLayout(this);
        rightPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightPanel.setLayoutParams(rightLp);

        // Right Header: Country Name + Channel Count
        LinearLayout rightHeader = new LinearLayout(this);
        rightHeader.setOrientation(LinearLayout.HORIZONTAL);
        rightHeader.setGravity(Gravity.CENTER_VERTICAL);
        rightHeader.setPadding(0, 0, 0, dp(10));

        rightCountryTitle = new TextView(this);
        rightCountryTitle.setText("🇧🇩 Bangladesh");
        rightCountryTitle.setTextColor(Color.WHITE);
        rightCountryTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        rightCountryTitle.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        rightHeader.addView(rightCountryTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rightChannelCount = new TextView(this);
        rightChannelCount.setText("Loading…");
        rightChannelCount.setTextColor(0xFF38BDF8);
        rightChannelCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        rightChannelCount.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        rightHeader.addView(rightChannelCount);
        rightPanel.addView(rightHeader);

        // Category Pills (Horizontal)
        categoriesScroll = new HorizontalScrollView(this);
        categoriesScroll.setHorizontalScrollBarEnabled(false);
        categoriesScroll.setPadding(0, 0, 0, dp(12));
        categoriesRow = new LinearLayout(this);
        categoriesRow.setOrientation(LinearLayout.HORIZONTAL);
        buildCategoryPills();
        categoriesScroll.addView(categoriesRow);
        rightPanel.addView(categoriesScroll);

        // Channels Grid Container (Vertical Scroll)
        FrameLayout channelContainer = new FrameLayout(this);
        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);

        channelsGrid = new GridLayout(this);
        channelsGrid.setColumnCount(3);
        channelsGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        channelsGrid.setUseDefaultMargins(false);
        gridScroll.addView(channelsGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        channelContainer.addView(gridScroll);

        loadingBar = new ProgressBar(this);
        FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER);
        channelContainer.addView(loadingBar, spinLp);

        emptyText = new TextView(this);
        emptyText.setText("No channels found.");
        emptyText.setTextColor(0x88FFFFFF);
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        channelContainer.addView(emptyText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        rightPanel.addView(channelContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mainSplit.addView(rightPanel);
        rootLayout.addView(mainSplit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(rootLayout);
    }

    private View createTopActionButton(String text, View.OnClickListener click) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xCCFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        tv.setFocusable(true);
        tv.setClickable(true);
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x20FFFFFF);
        bg.setStroke(dp(1), 0x22FFFFFF);
        tv.setBackground(bg);

        tv.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable fbg = new GradientDrawable();
            fbg.setCornerRadius(dp(16));
            if (hasFocus) {
                fbg.setColor(0x550284C7);
                fbg.setStroke(dp(2), 0xFF38BDF8);
                tv.setTextColor(Color.WHITE);
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).start();
            } else {
                fbg.setColor(0x20FFFFFF);
                fbg.setStroke(dp(1), 0x22FFFFFF);
                tv.setTextColor(0xCCFFFFFF);
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            v.setBackground(fbg);
        });

        tv.setOnClickListener(click);
        return tv;
    }

    private void loadAllCountries() {
        IptvManager.getCountries(this, new IptvManager.Callback<List<IptvCountry>>() {
            @Override public void onResult(List<IptvCountry> list) {
                allCountries = list != null ? list : new ArrayList<>();
                rebuildPinnedList();
                buildPinnedGrid();
            }

            @Override public void onError(Exception e) {
                allCountries = new ArrayList<>(IptvManager.SOUTH_ASIA);
                rebuildPinnedList();
                buildPinnedGrid();
            }
        });
    }

    private void rebuildPinnedList() {
        pinnedCountries.clear();
        List<String> codes = IptvManager.getPinnedCountryCodes(this);
        Map<String, IptvCountry> map = new HashMap<>();
        for (IptvCountry c : allCountries) map.put(c.code.toUpperCase(), c);
        for (IptvCountry c : IptvManager.SOUTH_ASIA) map.put(c.code.toUpperCase(), c);

        for (String code : codes) {
            IptvCountry c = map.get(code.toUpperCase());
            if (c != null && !pinnedCountries.contains(c)) pinnedCountries.add(c);
        }
    }

    /**
     * Builds the 3x3 Pinned Grid:
     * Card 0: Starred
     * Cards 1–7: 7 pinned countries
     * Card 8: All countries
     */
    private void buildPinnedGrid() {
        pinnedGrid.removeAllViews();
        int cardW = dp(96);
        int cardH = dp(84);
        int margin = dp(4);

        // 1. Starred Card (Index 0)
        pinnedViews[0] = createPinnedCard("⭐", "Starred", isStarredMode, false, v -> {
            isStarredMode = true;
            rightCountryTitle.setText("⭐ Starred Channels");
            fullChannelList = IptvManager.getStarredChannels(this);
            applyFilter();
            buildPinnedGrid();
        });
        pinnedViews[0].setId(pinnedIds[0]);
        addGridCell(pinnedGrid, pinnedViews[0], cardW, cardH, margin);

        // 2–8. Pinned Countries (up to 7, indices 1..7)
        int maxCountries = Math.min(7, pinnedCountries.size());
        for (int i = 0; i < maxCountries; i++) {
            final IptvCountry c = pinnedCountries.get(i);
            boolean isSelected = !isStarredMode && selectedCountry != null && selectedCountry.code.equalsIgnoreCase(c.code);
            pinnedViews[i + 1] = createPinnedCard(c.flag, c.name, isSelected, isSelected, v -> {
                isStarredMode = false;
                selectedCountry = c;
                rightCountryTitle.setText(c.flag + " " + c.name);
                loadChannels(c.code);
                buildPinnedGrid();
            });
            pinnedViews[i + 1].setId(pinnedIds[i + 1]);
            addGridCell(pinnedGrid, pinnedViews[i + 1], cardW, cardH, margin);
        }

        // Fill empty if fewer than 7 countries
        for (int i = maxCountries; i < 7; i++) {
            View emptyCard = new View(this);
            pinnedViews[i + 1] = emptyCard;
            addGridCell(pinnedGrid, emptyCard, cardW, cardH, margin);
        }

        // 9. "All countries" Card (Index 8)
        pinnedViews[8] = createPinnedCard("🌐", "All countries", false, false, v -> showAllCountriesModal());
        pinnedViews[8].setId(pinnedIds[8]);
        addGridCell(pinnedGrid, pinnedViews[8], cardW, cardH, margin);

        // Wire 3x3 grid D-pad focus navigation explicitly
        wirePinnedFocus();
    }

    private void wirePinnedFocus() {
        // Edit button
        if (editBtn != null) {
            editBtn.setNextFocusDownId(pinnedIds[1]); // Move down to Bangladesh
            if (searchBtn != null) editBtn.setNextFocusRightId(searchBtn.getId());
        }

        // 3x3 Grid:
        // 0 1 2
        // 3 4 5
        // 6 7 8
        for (int i = 0; i < 9; i++) {
            View v = pinnedViews[i];
            if (v == null) continue;

            int row = i / 3;
            int col = i % 3;

            // Up
            if (row > 0) {
                v.setNextFocusUpId(pinnedIds[i - 3]);
            } else {
                if (editBtn != null) v.setNextFocusUpId(editBtn.getId());
            }

            // Down
            if (row < 2) {
                v.setNextFocusDownId(pinnedIds[i + 3]);
            }

            // Left
            if (col > 0) {
                v.setNextFocusLeftId(pinnedIds[i - 1]);
            }

            // Right: column 2 points into right panel (categories)
            if (col < 2) {
                v.setNextFocusRightId(pinnedIds[i + 1]);
            } else {
                if (categoriesRow != null && categoriesRow.getChildCount() > 0) {
                    v.setNextFocusRightId(categoriesRow.getChildAt(0).getId());
                }
            }
        }
    }

    private void addGridCell(GridLayout grid, View view, int w, int h, int m) {
        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = w;
        glp.height = h;
        glp.setMargins(m, m, m, m);
        view.setLayoutParams(glp);
        grid.addView(view);
    }

    private View createPinnedCard(String iconStr, String labelStr, boolean isSelected, boolean showCheck, View.OnClickListener click) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setCornerRadius(dp(14));
        if (isSelected) {
            normalBg.setColor(0x350284C7);
            normalBg.setStroke(dp(2), 0xFF38BDF8);
        } else {
            normalBg.setColor(0x401E293B); // Dark slate glass
            normalBg.setStroke(dp(1), 0x22FFFFFF);
        }
        card.setBackground(normalBg);

        // Content layout inside card
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(6), dp(8), dp(6), dp(8));

        TextView icon = new TextView(this);
        icon.setText(iconStr);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        icon.setGravity(Gravity.CENTER);
        content.addView(icon);

        TextView label = new TextView(this);
        label.setText(labelStr);
        label.setTextColor(isSelected ? 0xFF38BDF8 : Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        label.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(4), 0, 0);
        content.addView(label);

        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // Check badge on top right if active/selected
        if (showCheck) {
            TextView checkBadge = new TextView(this);
            checkBadge.setText("✔");
            checkBadge.setTextColor(Color.WHITE);
            checkBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            checkBadge.setGravity(Gravity.CENTER);

            GradientDrawable checkBg = new GradientDrawable();
            checkBg.setShape(GradientDrawable.OVAL);
            checkBg.setColor(0xFF0284C7);
            checkBadge.setBackground(checkBg);

            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP | Gravity.END);
            clp.setMargins(0, dp(6), dp(6), 0);
            card.addView(checkBadge, clp);
        }

        card.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable fbg = new GradientDrawable();
            fbg.setCornerRadius(dp(14));
            if (hasFocus) {
                fbg.setColor(0x600284C7);
                fbg.setStroke(dp(2.5f), 0xFF67E8F9); // Vibrant cyan-white glow border
                label.setTextColor(Color.WHITE);
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).start();
            } else {
                if (isSelected) {
                    fbg.setColor(0x350284C7);
                    fbg.setStroke(dp(2), 0xFF38BDF8);
                    label.setTextColor(0xFF38BDF8);
                } else {
                    fbg.setColor(0x401E293B);
                    fbg.setStroke(dp(1), 0x22FFFFFF);
                    label.setTextColor(Color.WHITE);
                }
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            v.setBackground(fbg);
        });

        card.setOnClickListener(click);
        return card;
    }

    private void buildCategoryPills() {
        categoriesRow.removeAllViews();
        for (int i = 0; i < CATEGORIES.length; i++) {
            final String cat = CATEGORIES[i];
            boolean isSelected = cat.equalsIgnoreCase(selectedCategory);
            View pill = createCategoryPill(cat, isSelected, v -> {
                selectedCategory = cat;
                buildCategoryPills();
                applyFilter();
            });
            int pillId = View.generateViewId();
            pill.setId(pillId);
            if (i == 0) {
                // Moving left from first category pill goes to Sri Lanka or Pinned grid
                pill.setNextFocusLeftId(pinnedIds[5]);
            }
            categoriesRow.addView(pill);
        }
    }

    private View createCategoryPill(String title, boolean isSelected, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        tv.setPadding(dp(16), dp(7), dp(16), dp(7));
        tv.setFocusable(true);
        tv.setClickable(true);

        updateCategoryPillStyle(tv, isSelected, false);

        tv.setOnFocusChangeListener((v, hasFocus) -> {
            updateCategoryPillStyle(tv, isSelected, hasFocus);
            if (hasFocus) {
                v.animate().scaleX(1.07f).scaleY(1.07f).setDuration(140).start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
        });

        tv.setOnClickListener(onClick);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(10));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void updateCategoryPillStyle(TextView tv, boolean isSelected, boolean hasFocus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (hasFocus) {
            bg.setColor(0xFF0284C7);
            bg.setStroke(dp(2), Color.WHITE);
            tv.setTextColor(Color.WHITE);
        } else if (isSelected) {
            bg.setColor(0xFF0284C7); // Solid vivid cyan as in mockup
            tv.setTextColor(Color.WHITE);
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
        rightChannelCount.setText("Loading playlist…");

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

    private int displayLimit = 45;

    private void applyFilter() {
        displayLimit = 45;
        filteredChannels.clear();
        for (IptvChannel ch : fullChannelList) {
            boolean matchesCat = "All".equalsIgnoreCase(selectedCategory) ||
                    (ch.group != null && ch.group.equalsIgnoreCase(selectedCategory));
            boolean matchesQuery = searchQuery.isEmpty() ||
                    ch.name.toLowerCase(Locale.US).contains(searchQuery.toLowerCase(Locale.US));

            if (matchesCat && matchesQuery) {
                filteredChannels.add(ch);
            }
        }

        rightChannelCount.setText(filteredChannels.size() + " channels");
        populateGrid();
    }

    private void populateGrid() {
        channelsGrid.removeAllViews();
        if (filteredChannels.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        emptyText.setVisibility(View.GONE);

        // 3 columns in the right panel
        int screenW = getResources().getDisplayMetrics().widthPixels - dp(56);
        int rightW = screenW - dp(310) - dp(45);
        int cardW = Math.max(dp(200), (rightW - dp(24)) / 3);

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

    private View createChannelCard(IptvChannel ch, int cardW, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(0x401E293B); // Dark slate glass
        normalBg.setCornerRadius(dp(14));
        normalBg.setStroke(dp(1), 0x22FFFFFF);
        card.setBackground(normalBg);

        // Logo Container: White framed box as in mockup (gives high contrast for all channel logos)
        FrameLayout logoFrame = new FrameLayout(this);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setColor(0xFFFFFFFF);
        logoBg.setCornerRadius(dp(8));
        logoFrame.setBackground(logoBg);
        logoFrame.setPadding(dp(8), dp(4), dp(8), dp(4));

        ImageView logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(dp(76), dp(46), Gravity.CENTER);
        logoFrame.addView(logoView, lParams);

        TextView fallbackLogo = new TextView(this);
        fallbackLogo.setText(ch.name.length() > 0 ? ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase() : "TV");
        fallbackLogo.setTextColor(0xFF0284C7);
        fallbackLogo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        fallbackLogo.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        fallbackLogo.setGravity(Gravity.CENTER);
        fallbackLogo.setVisibility(View.GONE);
        logoFrame.addView(fallbackLogo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            logoView.setTag(ch.logoUrl);
            IptvManager.loadLogo(ch.logoUrl, bmp -> {
                if (bmp != null && ch.logoUrl.equals(logoView.getTag())) {
                    logoView.setImageBitmap(bmp);
                    fallbackLogo.setVisibility(View.GONE);
                } else {
                    fallbackLogo.setVisibility(View.VISIBLE);
                }
            });
        } else {
            fallbackLogo.setVisibility(View.VISIBLE);
        }
        card.addView(logoFrame, new LinearLayout.LayoutParams(dp(96), dp(54)));

        // Channel Name
        TextView nameText = new TextView(this);
        nameText.setText(ch.name);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        nameText.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        nameText.setGravity(Gravity.CENTER);
        nameText.setSingleLine(true);
        nameText.setEllipsize(TextUtils.TruncateAt.END);
        nameText.setPadding(0, dp(6), 0, dp(2));
        card.addView(nameText);

        // Subtitle row: Category & Resolution (e.g. "Entertainment • 720P")
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER);

        TextView groupText = new TextView(this);
        groupText.setText(ch.group != null ? ch.group : "General");
        groupText.setTextColor(0xFF38BDF8); // Cyan
        groupText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        metaRow.addView(groupText);

        String res = (ch.resolution != null && !ch.resolution.isEmpty()) ? ch.resolution : "HD";
        TextView resText = new TextView(this);
        resText.setText(" • " + res);
        resText.setTextColor(0xFFF59E0B); // Amber
        resText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        metaRow.addView(resText);

        if (ch.isStarred) {
            TextView starText = new TextView(this);
            starText.setText(" • ⭐");
            starText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            metaRow.addView(starText);
        }

        card.addView(metaRow);

        // Column 0 in channel grid: moving left focuses the pinned grid
        if (index % 3 == 0) {
            int targetPinned = pinnedIds[Math.min(8, (index / 3) * 3 + 2)];
            card.setNextFocusLeftId(targetPinned);
        }

        // D-Pad Focus & Click
        card.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable fbg = new GradientDrawable();
            fbg.setCornerRadius(dp(14));
            if (hasFocus) {
                fbg.setColor(0x881E293B);
                fbg.setStroke(dp(2.5f), 0xFF38BDF8); // Cyan glow focus ring
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start();
            } else {
                fbg.setColor(0x401E293B);
                fbg.setStroke(dp(1), 0x22FFFFFF);
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            v.setBackground(fbg);
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
        glp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(glp);
        return card;
    }

    private View createLoadMoreCard(int cardW, int remaining) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(12), dp(22), dp(12), dp(22));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x350284C7);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1.5f), 0xFF38BDF8);
        card.setBackground(bg);

        TextView icon = new TextView(this);
        icon.setText("➕");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        TextView text = new TextView(this);
        text.setText("Load More (" + remaining + ")");
        text.setTextColor(Color.WHITE);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        text.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(4), 0, 0);
        card.addView(text);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                GradientDrawable fbg = new GradientDrawable();
                fbg.setColor(0x880284C7);
                fbg.setCornerRadius(dp(14));
                fbg.setStroke(dp(2.5f), Color.WHITE);
                v.setBackground(fbg);
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start();
            } else {
                v.setBackground(bg);
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
        });

        card.setOnClickListener(v -> {
            displayLimit += 45;
            populateGrid();
        });

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = cardW;
        glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        glp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(glp);
        return card;
    }

    /**
     * "All Countries" Modal Dialog:
     * 10-column dense grid of all 250+ countries with flags and short codes/names.
     * Smooth D-pad scrolling and quick country selection.
     */
    private void showAllCountriesModal() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int dialogW = Math.round(getResources().getDisplayMetrics().widthPixels * 0.90f);
        int dialogH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.88f);

        LinearLayout modalRoot = new LinearLayout(this);
        modalRoot.setOrientation(LinearLayout.VERTICAL);
        modalRoot.setPadding(dp(24), dp(20), dp(24), dp(20));

        GradientDrawable mBg = new GradientDrawable();
        mBg.setColor(0xF00A0F1D); // Deep frosted dark dialog
        mBg.setCornerRadius(dp(20));
        mBg.setStroke(dp(1.5f), 0x4438BDF8);
        modalRoot.setBackground(mBg);

        // Header: Title + Close hint
        LinearLayout mHeader = new LinearLayout(this);
        mHeader.setOrientation(LinearLayout.HORIZONTAL);
        mHeader.setGravity(Gravity.CENTER_VERTICAL);
        mHeader.setPadding(0, 0, 0, dp(12));

        TextView mTitle = new TextView(this);
        mTitle.setText("🌐 All Countries (" + allCountries.size() + ")");
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mTitle.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        mHeader.addView(mTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕ Close [BACK]");
        closeBtn.setTextColor(0xAAFFFFFF);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        closeBtn.setFocusable(true);
        closeBtn.setClickable(true);
        closeBtn.setPadding(dp(12), dp(6), dp(12), dp(6));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        closeBtn.setOnFocusChangeListener((v, hasFocus) -> {
            closeBtn.setTextColor(hasFocus ? Color.WHITE : 0xAAFFFFFF);
            closeBtn.setBackgroundColor(hasFocus ? 0x33FF0033 : Color.TRANSPARENT);
        });
        mHeader.addView(closeBtn);
        modalRoot.addView(mHeader);

        // Dense Grid in ScrollView: 10 columns!
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);

        final GridLayout denseGrid = new GridLayout(this);
        denseGrid.setColumnCount(10); // 10 columns as user requested
        denseGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        denseGrid.setUseDefaultMargins(false);

        int cellW = (dialogW - dp(64)) / 10;
        int cellH = dp(68);

        View firstCell = null;
        for (final IptvCountry c : allCountries) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setFocusable(true);
            cell.setClickable(true);
            cell.setPadding(dp(2), dp(4), dp(2), dp(4));

            GradientDrawable cellBg = new GradientDrawable();
            cellBg.setColor(0x251E293B);
            cellBg.setCornerRadius(dp(10));
            cellBg.setStroke(dp(1), 0x15FFFFFF);
            cell.setBackground(cellBg);

            TextView flag = new TextView(this);
            flag.setText(c.flag);
            flag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            flag.setGravity(Gravity.CENTER);
            cell.addView(flag);

            TextView name = new TextView(this);
            name.setText(c.name);
            name.setTextColor(0xDDFFFFFF);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            cell.addView(name);

            cell.setOnFocusChangeListener((v, hasFocus) -> {
                GradientDrawable fbg = new GradientDrawable();
                fbg.setCornerRadius(dp(10));
                if (hasFocus) {
                    fbg.setColor(0x880284C7);
                    fbg.setStroke(dp(2), 0xFF38BDF8);
                    name.setTextColor(Color.WHITE);
                    v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
                } else {
                    fbg.setColor(0x251E293B);
                    fbg.setStroke(dp(1), 0x15FFFFFF);
                    name.setTextColor(0xDDFFFFFF);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                v.setBackground(fbg);
            });

            cell.setOnClickListener(v -> {
                dialog.dismiss();
                isStarredMode = false;
                selectedCountry = c;
                rightCountryTitle.setText(c.flag + " " + c.name);
                loadChannels(c.code);
                buildPinnedGrid();
            });

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cellW;
            glp.height = cellH;
            glp.setMargins(dp(2), dp(3), dp(2), dp(3));
            cell.setLayoutParams(glp);
            denseGrid.addView(cell);

            if (firstCell == null) firstCell = cell;
        }

        scroll.addView(denseGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        modalRoot.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(modalRoot, new ViewGroup.LayoutParams(dialogW, dialogH));

        final View fCell = firstCell;
        dialog.setOnShowListener(d -> {
            if (fCell != null) fCell.requestFocus();
        });

        dialog.show();
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Search Channels");

        final EditText input = new EditText(this);
        input.setHint("Enter channel or show name…");
        input.setText(searchQuery);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x77FFFFFF);
        input.setSingleLine(true);
        input.setPadding(dp(16), dp(16), dp(16), dp(16));
        builder.setView(input);

        builder.setPositiveButton("Search", (d, w) -> {
            searchQuery = input.getText().toString().trim();
            applyFilter();
        });

        builder.setNegativeButton("Clear", (d, w) -> {
            searchQuery = "";
            applyFilter();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showEditPinnedDialog() {
        if (allCountries.isEmpty()) return;
        List<String> currentCodes = IptvManager.getPinnedCountryCodes(this);
        final String[] names = new String[allCountries.size()];
        final boolean[] checked = new boolean[allCountries.size()];

        for (int i = 0; i < allCountries.size(); i++) {
            IptvCountry c = allCountries.get(i);
            names[i] = c.flag + "  " + c.name + " (" + c.code + ")";
            checked[i] = currentCodes.contains(c.code.toUpperCase());
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Edit Pinned Countries (Pick up to 7)");
        b.setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked);

        b.setPositiveButton("Save", (dialog, which) -> {
            List<String> newCodes = new ArrayList<>();
            for (int i = 0; i < checked.length; i++) {
                if (checked[i]) newCodes.add(allCountries.get(i).code.toUpperCase());
            }
            if (newCodes.isEmpty()) newCodes.add("BD");
            IptvManager.savePinnedCountryCodes(this, newCodes);
            rebuildPinnedList();
            buildPinnedGrid();
            Toast.makeText(this, "Pinned countries updated", Toast.LENGTH_SHORT).show();
        });

        b.setNegativeButton("Cancel", null);
        b.show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (isStarredMode) {
            fullChannelList = IptvManager.getStarredChannels(this);
            applyFilter();
        }
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
