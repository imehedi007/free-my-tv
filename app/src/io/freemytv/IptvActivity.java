package io.freemytv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modern Split-Screen Android TV Dashboard for Live TV / IPTV.
 * Left Panel: Pinned countries 3x3 grid & Edit Pinned Dialog.
 * Right Panel: Country header, category chips, and 3-column channel grid.
 */
public class IptvActivity extends Activity {
    private LinearLayout rootLayout;
    private GridLayout pinnedGrid;
    private LinearLayout categoryContainer;
    private GridLayout channelsGrid;
    private ScrollView gridScroll;
    private ProgressBar loadingBar;
    private TextView emptyText;
    private TextView rightCountryTitle;
    private TextView rightChannelCount;
    private TextView editBtn;

    private List<IptvCountry> allCountries = new ArrayList<>();
    private final List<IptvCountry> pinnedCountries = new ArrayList<>();
    private IptvCountry selectedCountry = IptvManager.SOUTH_ASIA.get(0); // Default: Bangladesh
    private List<IptvChannel> fullChannelList = new ArrayList<>();
    private List<IptvChannel> filteredChannels = new ArrayList<>();
    private String selectedCategory = "All";

    private final View[] pinnedViews = new View[9];
    private final int[] pinnedIds = new int[9];

    private static final String[][] CATEGORY_GRID = {
        {"All", "News", "Sports", "Entertainment"},
        {"Kids", "Movies", "Music", "General"}
    };
    private final View[][] catViews = new View[2][4];
    private final int[][] catIds = new int[2][4];

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        for (int i = 0; i < 9; i++) pinnedIds[i] = View.generateViewId();
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 4; c++) {
                catIds[r][c] = View.generateViewId();
            }
        }

        buildUi();

        loadAllCountries();
        loadChannels(selectedCountry.code);
    }

    private void buildUi() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xFF080D1A); // Deep dark navy slate
        rootLayout.setPadding(dp(20), dp(16), dp(20), dp(10));

        // 1. Top Bar: "Live TV" on left, followed by subtle full-width horizontal divider
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 0, 0, dp(6));

        TextView appTitle = new TextView(this);
        appTitle.setText("Live TV");
        appTitle.setTextColor(Color.WHITE);
        appTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        appTitle.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        topBar.addView(appTitle);
        rootLayout.addView(topBar);

        View horizLine = new View(this);
        horizLine.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        hlp.setMargins(0, 0, 0, dp(12));
        rootLayout.addView(horizLine, hlp);

        // 2. Main Content: Split Screen (Left: Pinned Countries, Right: Channels)
        LinearLayout mainSplit = new LinearLayout(this);
        mainSplit.setOrientation(LinearLayout.HORIZONTAL);

        // --- Left Panel: Pinned countries ---
        LinearLayout leftPanel = new LinearLayout(this);
        leftPanel.setOrientation(LinearLayout.VERTICAL);
        int leftWidth = dp(275);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(leftWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        leftLp.setMarginEnd(dp(14));
        leftPanel.setLayoutParams(leftLp);

        // Pinned countries header row with Edit button
        LinearLayout pinnedHeader = new LinearLayout(this);
        pinnedHeader.setOrientation(LinearLayout.HORIZONTAL);
        pinnedHeader.setGravity(Gravity.CENTER_VERTICAL);
        pinnedHeader.setPadding(0, 0, 0, dp(8));

        TextView pinnedTitle = new TextView(this);
        pinnedTitle.setText("Pinned countries");
        pinnedTitle.setTextColor(0xFFFFFFFF);
        pinnedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        pinnedTitle.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        pinnedHeader.addView(pinnedTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        editBtn = new TextView(this);
        editBtn.setId(View.generateViewId());
        editBtn.setText("✎ Edit");
        editBtn.setTextColor(0xFF00B4D8);
        editBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        editBtn.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        editBtn.setFocusable(true);
        editBtn.setClickable(true);
        editBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        editBtn.setOnClickListener(v -> showEditPinnedDialog());
        editBtn.setOnFocusChangeListener((v, hasFocus) -> {
            editBtn.setTextColor(hasFocus ? Color.WHITE : 0xFF00B4D8);
            editBtn.setBackgroundColor(hasFocus ? 0x3300B4D8 : Color.TRANSPARENT);
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

        // Navigation hint at bottom of left panel
        TextView hint = new TextView(this);
        hint.setText("Arrow keys to move • OK to select");
        hint.setTextColor(0x7794A3B8);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        hint.setPadding(0, dp(14), 0, 0);
        leftPanel.addView(hint);

        mainSplit.addView(leftPanel);

        // Subtle vertical separator line
        View sep = new View(this);
        sep.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT);
        sepLp.setMarginEnd(dp(16));
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
        rightCountryTitle.setText("🇧🇩  Bangladesh");
        rightCountryTitle.setTextColor(Color.WHITE);
        rightCountryTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        rightCountryTitle.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        rightHeader.addView(rightCountryTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        rightChannelCount = new TextView(this);
        rightChannelCount.setText("Loading…");
        rightChannelCount.setTextColor(0xFF00B4D8);
        rightChannelCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        rightChannelCount.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        rightHeader.addView(rightChannelCount);
        rightPanel.addView(rightHeader);

        // 2x4 Category Grid Container
        categoryContainer = new LinearLayout(this);
        categoryContainer.setOrientation(LinearLayout.VERTICAL);
        categoryContainer.setPadding(0, 0, 0, dp(10));
        buildCategoryPills();
        rightPanel.addView(categoryContainer);

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
        FrameLayout.LayoutParams spinLp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
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

        // Bottom Bar: (OK) OK Select pill centered
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(0, dp(6), 0, dp(2));

        LinearLayout okPill = new LinearLayout(this);
        okPill.setOrientation(LinearLayout.HORIZONTAL);
        okPill.setGravity(Gravity.CENTER);
        okPill.setPadding(dp(12), dp(3), dp(12), dp(3));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0xFF0F172A);
        pillBg.setCornerRadius(dp(14));
        pillBg.setStroke(dp(1), 0xFF1E293B);
        okPill.setBackground(pillBg);

        TextView okBadge = new TextView(this);
        okBadge.setText("OK");
        okBadge.setTextColor(Color.WHITE);
        okBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        okBadge.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        okBadge.setGravity(Gravity.CENTER);
        okBadge.setPadding(dp(6), dp(1), dp(6), dp(1));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xFF1E293B);
        badgeBg.setCornerRadius(dp(4));
        badgeBg.setStroke(dp(1), 0xFF475569);
        okBadge.setBackground(badgeBg);
        okPill.addView(okBadge);

        TextView okLabel = new TextView(this);
        okLabel.setText("OK Select");
        okLabel.setTextColor(0xFF94A3B8);
        okLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        okLabel.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        okLabel.setPadding(dp(8), 0, 0, 0);
        okPill.addView(okLabel);

        bottomBar.addView(okPill);
        rootLayout.addView(bottomBar);

        setContentView(rootLayout);
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
        Map<String, IptvCountry> map = new HashMap<>();
        for (IptvCountry c : allCountries) {
            if (c.code != null && !c.code.isEmpty()) {
                map.put(c.code.toUpperCase().trim(), c);
            }
        }
        for (IptvCountry c : IptvManager.SOUTH_ASIA) {
            if (!map.containsKey(c.code.toUpperCase().trim())) {
                map.put(c.code.toUpperCase().trim(), c);
            }
        }
        for (IptvCountry c : IptvManager.POPULAR_WORLD) {
            if (!map.containsKey(c.code.toUpperCase().trim())) {
                map.put(c.code.toUpperCase().trim(), c);
            }
        }

        List<String> codes = IptvManager.getPinnedCountryCodes(this);
        if (codes == null || codes.isEmpty()) {
            codes = new ArrayList<>(Arrays.asList("BD", "IN", "PK", "NP", "LK", "BT", "MV"));
        }

        for (String code : codes) {
            if (code == null) continue;
            String key = code.toUpperCase().trim();
            IptvCountry c = map.get(key);
            if (c == null) {
                for (IptvCountry ac : allCountries) {
                    if (ac.code != null && ac.code.equalsIgnoreCase(key)) {
                        c = ac;
                        break;
                    }
                }
                if (c == null) {
                    c = new IptvCountry(key, key, "🌐");
                }
            }
            if (!pinnedCountries.contains(c)) {
                pinnedCountries.add(c);
            }
        }

        if (pinnedCountries.isEmpty()) {
            for (IptvCountry c : IptvManager.SOUTH_ASIA) {
                if (!"AF".equalsIgnoreCase(c.code)) pinnedCountries.add(c);
            }
        }
    }

    /**
     * Builds the 3x3 Pinned Grid:
     * Row 1: Bangladesh, India, Pakistan
     * Row 2: Nepal, Sri Lanka, Bhutan
     * Row 3: Maldives, All countries, (Spacer)
     */
    private void buildPinnedGrid() {
        pinnedGrid.removeAllViews();
        int cardW = dp(83);
        int cardH = dp(84);
        int margin = dp(4);

        int maxCountries = Math.min(7, pinnedCountries.size());
        for (int i = 0; i < 7; i++) {
            if (i < maxCountries) {
                final IptvCountry c = pinnedCountries.get(i);
                boolean isSelected = selectedCountry != null && selectedCountry.code.equalsIgnoreCase(c.code);
                pinnedViews[i] = createPinnedCard(c.flag, c.name, isSelected, isSelected, i, v -> {
                    selectedCountry = c;
                    rightCountryTitle.setText(c.flag + "  " + c.name);
                    selectedCategory = "All";
                    updateCategoryPillsSelection();
                    if (gridScroll != null) {
                        gridScroll.scrollTo(0, 0);
                    }
                    loadChannels(c.code);
                    updatePinnedCardsSelection();
                });
            } else {
                View emptyCard = new View(this);
                emptyCard.setVisibility(View.INVISIBLE);
                pinnedViews[i] = emptyCard;
            }
            pinnedViews[i].setId(pinnedIds[i]);
            addGridCell(pinnedGrid, pinnedViews[i], cardW, cardH, margin);
        }

        // Slot 7: "All countries" Card
        pinnedViews[7] = createPinnedCard("🌐", "All countries", false, false, 7, v -> showAllCountriesModal());
        pinnedViews[7].setId(pinnedIds[7]);
        addGridCell(pinnedGrid, pinnedViews[7], cardW, cardH, margin);

        // Slot 8: Empty spacer to complete 3x3 layout
        View emptySlot = new View(this);
        emptySlot.setVisibility(View.INVISIBLE);
        pinnedViews[8] = emptySlot;
        pinnedViews[8].setId(pinnedIds[8]);
        addGridCell(pinnedGrid, emptySlot, cardW, cardH, margin);

        wirePinnedFocus();
    }

    private void updatePinnedCardsSelection() {
        int maxCountries = Math.min(7, pinnedCountries.size());
        for (int i = 0; i < 7; i++) {
            View card = pinnedViews[i];
            if (card instanceof FrameLayout && i < maxCountries) {
                IptvCountry c = pinnedCountries.get(i);
                boolean isSelected = selectedCountry != null && selectedCountry.code.equalsIgnoreCase(c.code);
                updatePinnedCardStyle(card, isSelected, card.hasFocus());

                View check = card.findViewWithTag("checkBadge");
                if (check != null) {
                    check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
                }
                View label = card.findViewWithTag("label");
                if (label instanceof TextView) {
                    ((TextView) label).setTextColor(isSelected ? 0xFF00B4D8 : Color.WHITE);
                }
            }
        }
    }

    private void wirePinnedFocus() {
        if (editBtn != null) {
            editBtn.setNextFocusDownId(pinnedIds[0]); // Move down to Bangladesh
            if (catIds[0][0] != 0) editBtn.setNextFocusRightId(catIds[0][0]);
        }

        for (int i = 0; i < 8; i++) {
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
            if (row == 0) {
                v.setNextFocusDownId(pinnedIds[i + 3]);
            } else if (row == 1) {
                v.setNextFocusDownId(col < 2 ? pinnedIds[i + 3] : pinnedIds[7]);
            }

            // Left
            if (col > 0) {
                v.setNextFocusLeftId(pinnedIds[i - 1]);
            }

            // Right into categories / channels:
            if (col < 2 && (i != 7)) {
                v.setNextFocusRightId(pinnedIds[i + 1]);
            } else {
                if (row == 0) v.setNextFocusRightId(catIds[0][0]);
                else if (row == 1) v.setNextFocusRightId(catIds[1][0]);
                else {
                    if (channelsGrid != null && channelsGrid.getChildCount() > 0) {
                        v.setNextFocusRightId(channelsGrid.getChildAt(0).getId());
                    } else {
                        v.setNextFocusRightId(catIds[1][0]);
                    }
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

    private View createPinnedCard(String iconStr, String labelStr, boolean isSelected, boolean showCheck, int slotIndex, View.OnClickListener click) {
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);

        updatePinnedCardStyle(card, isSelected, false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(4), dp(6), dp(4), dp(6));

        TextView icon = new TextView(this);
        icon.setText(iconStr);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        icon.setGravity(Gravity.CENTER);
        content.addView(icon);

        TextView label = new TextView(this);
        label.setTag("label");
        label.setText(labelStr);
        label.setTextColor(isSelected ? 0xFF00B4D8 : Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(4), 0, 0);
        content.addView(label);

        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        // Circular check badge on top right for active selected country
        TextView checkBadge = new TextView(this);
        checkBadge.setTag("checkBadge");
        checkBadge.setText("✔");
        checkBadge.setTextColor(Color.WHITE);
        checkBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        checkBadge.setGravity(Gravity.CENTER);

        GradientDrawable checkBg = new GradientDrawable();
        checkBg.setShape(GradientDrawable.OVAL);
        checkBg.setColor(0xFF00B4D8);
        checkBadge.setBackground(checkBg);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(5), dp(5), 0);
        checkBadge.setVisibility(showCheck ? View.VISIBLE : View.GONE);
        card.addView(checkBadge, clp);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            boolean active = (slotIndex < pinnedCountries.size() &&
                    selectedCountry != null &&
                    selectedCountry.code.equalsIgnoreCase(pinnedCountries.get(slotIndex).code));
            updatePinnedCardStyle(card, active, hasFocus);
            if (hasFocus) {
                label.setTextColor(Color.WHITE);
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start();
            } else {
                label.setTextColor(active ? 0xFF00B4D8 : Color.WHITE);
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
        });

        card.setOnClickListener(click);
        return card;
    }

    private void updatePinnedCardStyle(View card, boolean isSelected, boolean hasFocus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (hasFocus) {
            bg.setColor(0xFF1E2C48);
            bg.setStroke(dp(2.5f), 0xFFE0F2FE); // Bright focus outline
        } else if (isSelected) {
            bg.setColor(0x2800B4D8);
            bg.setStroke(dp(2), 0xFF00B4D8); // Cyan border
        } else {
            bg.setColor(0xFF0F172A); // Dark slate
            bg.setStroke(dp(1), 0xFF1E293B);
        }
        card.setBackground(bg);
    }

    private void buildCategoryPills() {
        if (categoryContainer == null) return;
        categoryContainer.removeAllViews();

        for (int r = 0; r < 2; r++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(4f);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r == 0) rlp.setMargins(0, 0, 0, dp(6));
            rowLayout.setLayoutParams(rlp);

            for (int c = 0; c < 4; c++) {
                final String cat = CATEGORY_GRID[r][c];
                boolean isSelected = cat.equalsIgnoreCase(selectedCategory);
                View pill = createCategoryPill(cat, isSelected, v -> {
                    selectedCategory = cat;
                    // Update styles in-place without removing views to preserve focus on clicked pill!
                    updateCategoryPillsSelection();
                    applyFilter();
                });
                pill.setId(catIds[r][c]);
                catViews[r][c] = pill;
                rowLayout.addView(pill);
            }
            categoryContainer.addView(rowLayout);
        }
        wireCategoryFocus();
    }

    private void updateCategoryPillsSelection() {
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 4; c++) {
                View v = catViews[r][c];
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    String cat = CATEGORY_GRID[r][c];
                    boolean isSelected = cat.equalsIgnoreCase(selectedCategory);
                    updateCategoryPillStyle(tv, isSelected, tv.hasFocus());
                }
            }
        }
    }

    private void wireCategoryFocus() {
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 4; c++) {
                View v = catViews[r][c];
                if (v == null) continue;

                // Up
                if (r == 0) {
                    if (editBtn != null) v.setNextFocusUpId(editBtn.getId());
                } else {
                    v.setNextFocusUpId(catIds[0][c]);
                }

                // Down
                if (r == 0) {
                    v.setNextFocusDownId(catIds[1][c]);
                } else {
                    if (channelsGrid != null && channelsGrid.getChildCount() > 0) {
                        int targetCol = Math.min(channelsGrid.getChildCount() - 1, c % 3);
                        v.setNextFocusDownId(channelsGrid.getChildAt(targetCol).getId());
                    }
                }

                // Left
                if (c > 0) {
                    v.setNextFocusLeftId(catIds[r][c - 1]);
                } else {
                    int pinnedIndex = r == 0 ? 2 : 5; // Pakistan or Bhutan
                    v.setNextFocusLeftId(pinnedIds[pinnedIndex]);
                }

                // Right
                if (c < 3) {
                    v.setNextFocusRightId(catIds[r][c + 1]);
                }
            }
        }
    }

    private View createCategoryPill(String title, boolean isSelected, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        tv.setGravity(Gravity.CENTER);
        tv.setFocusable(true);
        tv.setClickable(true);

        updateCategoryPillStyle(tv, isSelected, false);

        tv.setOnFocusChangeListener((v, hasFocus) -> {
            boolean sel = title.equalsIgnoreCase(selectedCategory);
            updateCategoryPillStyle(tv, sel, hasFocus);
            if (hasFocus) {
                v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
        });

        tv.setOnClickListener(onClick);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void updateCategoryPillStyle(TextView tv, boolean isSelected, boolean hasFocus) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        if (hasFocus) {
            bg.setColor(0xFF0284C7);
            bg.setStroke(dp(2), Color.WHITE);
            tv.setTextColor(Color.WHITE);
        } else if (isSelected) {
            bg.setColor(0xFF00A3E0); // Solid vivid cyan as in mockup
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(0xFF131D31); // Dark slate as in mockup
            bg.setStroke(dp(1), 0x25FFFFFF);
            tv.setTextColor(0xFFCBD5E1);
        }
        tv.setBackground(bg);
    }

    private static final List<String> TOP_BD_NAMES = Arrays.asList(
        "channel 24", "news 24", "somoy tv", "jamuna tv", "independent tv",
        "ekattor tv", "ntv", "rtv", "bangla vision", "maasranga tv",
        "atn bangla", "atn news", "boishakhi tv", "btv national", "btv news",
        "btv chattogram", "btv world", "t sports", "gazi tv", "duronto tv",
        "desh tv", "ekushey tv", "nagorik tv", "channel i", "asian tv",
        "ananda tv", "bijoy tv", "nexus tv", "ekhon tv", "global tv",
        "my tv", "mohona tv", "sa tv", "channel 9", "channel 16",
        "channel 1", "jago news 24", "bengali beats", "movie bangla",
        "bangla tv", "channel 52", "dbc news", "deepto tv", "green tv",
        "rajdhani tv", "enter tv", "g series drama", "g tv", "jonmobhumi tv",
        "matribhumi tv", "rupashi bangla tv", "star news", "jalsha bangla bd"
    );

    private boolean isBangladeshiChannel(IptvChannel ch) {
        if (ch == null) return false;
        String name = ch.name != null ? ch.name.trim().toLowerCase(Locale.US) : "";
        for (String bd : TOP_BD_NAMES) {
            if (name.equals(bd) || name.startsWith(bd + " ")) return true;
        }
        String id = ch.id != null ? ch.id.trim().toLowerCase(Locale.US) : "";
        if (id.endsWith(".bd") || id.contains(".bd@") || id.startsWith("bd-")) {
            if (!name.contains("disney") && !name.contains("eurosport") && !name.contains("medina")
                    && !name.contains("usa tv") && !name.contains("vissa")) {
                return true;
            }
        }
        return false;
    }

    private int getBdChannelRank(IptvChannel ch) {
        if (ch == null) return 999;
        String name = ch.name != null ? ch.name.trim().toLowerCase(Locale.US) : "";
        for (int i = 0; i < TOP_BD_NAMES.size(); i++) {
            String bd = TOP_BD_NAMES.get(i);
            if (name.equals(bd) || name.startsWith(bd + " ")) return i;
        }
        return 500;
    }

    private void sortChannelsForCountry(List<IptvChannel> list, String countryCode) {
        if (list == null || list.isEmpty()) return;

        if ("BD".equalsIgnoreCase(countryCode)) {
            java.util.Collections.sort(list, (a, b) -> {
                boolean aBd = isBangladeshiChannel(a);
                boolean bBd = isBangladeshiChannel(b);
                if (aBd && !bBd) return -1;
                if (!aBd && bBd) return 1;
                if (aBd && bBd) {
                    int rankA = getBdChannelRank(a);
                    int rankB = getBdChannelRank(b);
                    if (rankA != rankB) return Integer.compare(rankA, rankB);
                    return a.name.compareToIgnoreCase(b.name);
                }
                // Non-BD: sort alphabetically
                return a.name.compareToIgnoreCase(b.name);
            });
        } else {
            java.util.Collections.sort(list, (a, b) -> a.name.compareToIgnoreCase(b.name));
        }
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
                sortChannelsForCountry(fullChannelList, countryCode);
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
        if (gridScroll != null) {
            gridScroll.post(() -> gridScroll.scrollTo(0, 0));
        }
        filteredChannels.clear();
        for (IptvChannel ch : fullChannelList) {
            boolean matchesCat = "All".equalsIgnoreCase(selectedCategory) ||
                    (ch.group != null && ch.group.equalsIgnoreCase(selectedCategory));

            if (matchesCat) {
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

        // Screen is 1280px wide on 720p TV
        // Total non-grid width: dp(20) [left pad] + dp(275) [leftPanel] + dp(14) [sep margin] + dp(1) [sep] + dp(16) [sep margin] + dp(20) [right pad] = dp(346)
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int availRightPx = screenW - dp(346);
        // 3 cards have 6 margins of dp(4) = dp(24). Reserve extra dp(8) safe padding from edge
        int cardW = (availRightPx - dp(32)) / 3;

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

    private boolean isMostlyDark(Bitmap bmp) {
        if (bmp == null) return false;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        long totalLuma = 0;
        int count = 0;
        int stepX = Math.max(1, w / 8);
        int stepY = Math.max(1, h / 8);
        for (int x = 0; x < w; x += stepX) {
            for (int y = 0; y < h; y += stepY) {
                int p = bmp.getPixel(x, y);
                int a = (p >> 24) & 0xff;
                if (a > 60) {
                    int r = (p >> 16) & 0xff;
                    int g = (p >> 8) & 0xff;
                    int b = p & 0xff;
                    int luma = (r * 299 + g * 587 + b * 114) / 1000;
                    totalLuma += luma;
                    count++;
                }
            }
        }
        if (count == 0) return false;
        return (totalLuma / count) < 70;
    }

    private View createChannelCard(IptvChannel ch, int cardW, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        int cId = View.generateViewId();
        card.setId(cId);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(0xFF0F172A); // Dark slate
        normalBg.setCornerRadius(dp(12));
        normalBg.setStroke(dp(1), 0xFF1E293B);
        card.setBackground(normalBg);

        // Logo Container: Dark slate glass by default (so white logos like ADN TV+ pop out),
        // or light container if logo is mostly black
        FrameLayout logoFrame = new FrameLayout(this);
        GradientDrawable logoBg = new GradientDrawable();
        logoBg.setColor(0xFF131D31);
        logoBg.setCornerRadius(dp(8));
        logoBg.setStroke(dp(1), 0x22FFFFFF);
        logoFrame.setBackground(logoBg);
        logoFrame.setPadding(dp(6), dp(4), dp(6), dp(4));

        ImageView logoView = new ImageView(this);
        logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(dp(80), dp(44), Gravity.CENTER);
        logoFrame.addView(logoView, lParams);

        TextView fallbackLogo = new TextView(this);
        fallbackLogo.setText(ch.name.length() > 0 ? ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase() : "TV");
        fallbackLogo.setTextColor(0xFF38BDF8); // Cyan
        fallbackLogo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
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
                    if (isMostlyDark(bmp)) {
                        logoBg.setColor(0xFFF1F5F9); // Light container for dark/black logos
                        logoBg.setStroke(0, 0);
                    } else {
                        logoBg.setColor(0xFF131D31);
                        logoBg.setStroke(dp(1), 0x22FFFFFF);
                    }
                } else {
                    fallbackLogo.setVisibility(View.VISIBLE);
                }
            });
        } else {
            fallbackLogo.setVisibility(View.VISIBLE);
        }
        card.addView(logoFrame, new LinearLayout.LayoutParams(dp(100), dp(52)));

        // Channel Name
        TextView nameText = new TextView(this);
        nameText.setText(ch.name);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        nameText.setTypeface(Ui.semibold != null ? Ui.semibold : android.graphics.Typeface.DEFAULT_BOLD);
        nameText.setGravity(Gravity.CENTER);
        nameText.setSingleLine(true);
        nameText.setEllipsize(TextUtils.TruncateAt.END);
        nameText.setPadding(0, dp(5), 0, dp(1));
        card.addView(nameText);

        // Subtitle row: Category & Resolution (e.g. "Entertainment • 720P")
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER);

        TextView groupText = new TextView(this);
        groupText.setText(ch.group != null ? ch.group : "General");
        groupText.setTextColor(0xFF38BDF8); // Cyan
        groupText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        groupText.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        metaRow.addView(groupText);

        String res = (ch.resolution != null && !ch.resolution.isEmpty()) ? ch.resolution : "HD";
        TextView resText = new TextView(this);
        resText.setText(" • " + res);
        resText.setTextColor(0xFF38BDF8); // Cyan
        resText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        resText.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        metaRow.addView(resText);

        card.addView(metaRow);

        // Column 0 in channel grid: moving left focuses the pinned grid
        if (index % 3 == 0) {
            int rowInGrid = index / 3;
            if (rowInGrid == 0) card.setNextFocusLeftId(pinnedIds[2]);
            else if (rowInGrid == 1) card.setNextFocusLeftId(pinnedIds[5]);
            else card.setNextFocusLeftId(pinnedIds[7]);
        }
        // First row in channel grid: moving up focuses category row 2
        if (index < 3) {
            card.setNextFocusUpId(catIds[1][index]);
        }

        // D-Pad Focus & Click
        card.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable fbg = new GradientDrawable();
            fbg.setCornerRadius(dp(12));
            if (hasFocus) {
                fbg.setColor(0xFF1A2742);
                fbg.setStroke(dp(2.5f), 0xFF38BDF8); // Cyan glow focus ring
                v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start();
            } else {
                fbg.setColor(0xFF0F172A);
                fbg.setStroke(dp(1), 0xFF1E293B);
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            v.setBackground(fbg);
        });

        card.setOnClickListener(v -> {
            IptvManager.setActivePlaylist(filteredChannels);
            Intent intent = new Intent(IptvActivity.this, IptvPlayerActivity.class);
            intent.putExtra(IptvPlayerActivity.EXTRA_CHANNEL_INDEX, index);
            startActivity(intent);
        });

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = cardW;
        glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        glp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(glp);
        return card;
    }

    private View createLoadMoreCard(int cardW, int remaining) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(12), dp(20), dp(12), dp(20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x350284C7);
        bg.setCornerRadius(dp(12));
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
                fbg.setCornerRadius(dp(12));
                fbg.setStroke(dp(2.5f), Color.WHITE);
                v.setBackground(fbg);
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(140).start();
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
        glp.setMargins(dp(4), dp(4), dp(4), dp(4));
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

        // Dense Grid in ScrollView: 10 columns
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);

        final GridLayout denseGrid = new GridLayout(this);
        denseGrid.setColumnCount(10);
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
                selectedCountry = c;
                rightCountryTitle.setText(c.flag + "  " + c.name);
                selectedCategory = "All";
                updateCategoryPillsSelection();
                if (gridScroll != null) {
                    gridScroll.scrollTo(0, 0);
                }
                loadChannels(c.code);
                updatePinnedCardsSelection();
                if (pinnedViews[7] != null) {
                    pinnedViews[7].requestFocus();
                }
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

    /**
     * Edit Pinned Countries Dialog:
     * - NO Save or Cancel buttons: clicking any country immediately toggles pinned status and saves instantly!
     * - Pinned countries appear first for instant access, followed by all remaining countries.
     * - Newly added countries show up immediately on the home screen pinned grid.
     */
    private void showEditPinnedDialog() {
        if (allCountries.isEmpty()) return;

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        int dialogW = Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f);
        int dialogH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.88f);

        LinearLayout modalRoot = new LinearLayout(this);
        modalRoot.setOrientation(LinearLayout.VERTICAL);
        modalRoot.setPadding(dp(24), dp(18), dp(24), dp(18));

        GradientDrawable mBg = new GradientDrawable();
        mBg.setColor(0xF60A0F1D);
        mBg.setCornerRadius(dp(18));
        mBg.setStroke(dp(1.5f), 0x4438BDF8);
        modalRoot.setBackground(mBg);

        // Header: Title + Done button (NO SAVE OR CANCEL BUTTONS!)
        LinearLayout mHeader = new LinearLayout(this);
        mHeader.setOrientation(LinearLayout.HORIZONTAL);
        mHeader.setGravity(Gravity.CENTER_VERTICAL);
        mHeader.setPadding(0, 0, 0, dp(12));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView mTitle = new TextView(this);
        mTitle.setText("✎ Edit Pinned Countries");
        mTitle.setTextColor(Color.WHITE);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mTitle.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
        titleCol.addView(mTitle);

        final TextView subTitle = new TextView(this);
        final List<String> currentCodes = new ArrayList<>(IptvManager.getPinnedCountryCodes(this));
        subTitle.setText("Currently pinned: " + currentCodes.size() + "/7 • Click to pin/unpin • Instant save");
        subTitle.setTextColor(0xFF38BDF8);
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subTitle.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
        titleCol.addView(subTitle);

        mHeader.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕ Done [BACK]");
        closeBtn.setTextColor(0xAAFFFFFF);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        closeBtn.setFocusable(true);
        closeBtn.setClickable(true);
        closeBtn.setPadding(dp(14), dp(6), dp(14), dp(6));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        closeBtn.setOnFocusChangeListener((v, hasFocus) -> {
            closeBtn.setTextColor(hasFocus ? Color.WHITE : 0xAAFFFFFF);
            closeBtn.setBackgroundColor(hasFocus ? 0x33FF0033 : Color.TRANSPARENT);
        });
        mHeader.addView(closeBtn);
        modalRoot.addView(mHeader);

        View sep = new View(this);
        sep.setBackgroundColor(0x1FFFFFFF);
        modalRoot.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 3-Column Country Grid in ScrollView
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);

        int colW = (dialogW - dp(64)) / 3;

        // Sort allCountries so currently pinned countries appear FIRST, followed by all others sorted alphabetically
        List<IptvCountry> sortedList = new ArrayList<>(allCountries);
        java.util.Collections.sort(sortedList, (a, b) -> {
            boolean aPin = currentCodes.contains(a.code.toUpperCase().trim());
            boolean bPin = currentCodes.contains(b.code.toUpperCase().trim());
            if (aPin && !bPin) return -1;
            if (!aPin && bPin) return 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        View firstCard = null;
        for (final IptvCountry c : sortedList) {
            final String upperCode = c.code.toUpperCase().trim();

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));

            TextView flag = new TextView(this);
            flag.setText(c.flag);
            flag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            flag.setPadding(0, 0, dp(8), 0);
            card.addView(flag);

            TextView name = new TextView(this);
            name.setText(c.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            name.setTypeface(Ui.medium != null ? Ui.medium : android.graphics.Typeface.DEFAULT);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final TextView status = new TextView(this);
            status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            status.setTypeface(Ui.displaySemibold != null ? Ui.displaySemibold : android.graphics.Typeface.DEFAULT_BOLD);
            status.setPadding(dp(6), dp(2), dp(6), dp(2));
            card.addView(status);

            // Runnable to refresh card appearance
            Runnable refreshState = () -> {
                boolean pinned = currentCodes.contains(upperCode);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(8));
                if (card.hasFocus()) {
                    bg.setColor(0xFF1E2C48);
                    bg.setStroke(dp(2), 0xFFE0F2FE);
                } else if (pinned) {
                    bg.setColor(0x2800B4D8);
                    bg.setStroke(dp(1.5f), 0xFF00B4D8);
                } else {
                    bg.setColor(0xFF0F172A);
                    bg.setStroke(dp(1), 0xFF1E293B);
                }
                card.setBackground(bg);

                if (pinned) {
                    status.setText("✔ PINNED");
                    status.setTextColor(0xFF00B4D8);
                } else {
                    status.setText("+ PIN");
                    status.setTextColor(0xFF64748B);
                }
            };
            refreshState.run();

            card.setOnFocusChangeListener((v, hasFocus) -> {
                refreshState.run();
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(100).start();
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                }
            });

            card.setOnClickListener(v -> {
                boolean pinned = currentCodes.contains(upperCode);
                if (pinned) {
                    // Unpin
                    currentCodes.remove(upperCode);
                    IptvManager.savePinnedCountryCodes(IptvActivity.this, currentCodes);
                    rebuildPinnedList();
                    buildPinnedGrid();
                    refreshState.run();
                    subTitle.setText("Currently pinned: " + currentCodes.size() + "/7 • Click to pin/unpin • Instant save");
                    Toast.makeText(IptvActivity.this, "Unpinned " + c.name, Toast.LENGTH_SHORT).show();
                } else {
                    // Pin
                    if (currentCodes.size() >= 7) {
                        Toast.makeText(IptvActivity.this, "Maximum 7 countries pinned. Please unpin one first.", Toast.LENGTH_SHORT).show();
                    } else {
                        currentCodes.add(upperCode);
                        IptvManager.savePinnedCountryCodes(IptvActivity.this, currentCodes);
                        rebuildPinnedList();
                        buildPinnedGrid();
                        refreshState.run();
                        subTitle.setText("Currently pinned: " + currentCodes.size() + "/7 • Click to pin/unpin • Instant save");
                        Toast.makeText(IptvActivity.this, "Pinned " + c.name, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = colW;
            glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            glp.setMargins(dp(3), dp(3), dp(3), dp(3));
            card.setLayoutParams(glp);
            grid.addView(card);

            if (firstCard == null) firstCard = card;
        }

        scroll.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        modalRoot.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(modalRoot, new ViewGroup.LayoutParams(dialogW, dialogH));

        final View f = firstCard;
        dialog.setOnShowListener(d -> {
            if (f != null) f.requestFocus();
        });

        dialog.setOnDismissListener(d -> {
            if (editBtn != null) editBtn.requestFocus();
        });

        dialog.show();
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
