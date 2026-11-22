package dev.lyriscope.core;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Apple Music-inspired Miuix bottom sheet for Lyric Quote Posters.
 *
 * <p>Architecture avoids nested ScrollViews completely:
 * <ul>
 *   <li>Pinned top live preview stage that updates immediately on every tap.</li>
 *   <li>Miuix 2-tab deck directly below preview: {@code 选择歌词} (default, full scrollable list
 *       auto-scrolled to the long-pressed line) and {@code 海报样式} (style, ratio, alignment,
 *       font scale, cover palette, toggles).</li>
 *   <li>Pinned bottom action bar for saving to gallery or sharing.</li>
 * </ul>
 */
final class LyricPosterDialog {
    private static final int MAX_SELECTED_LINES = 6;

    private LyricPosterDialog() {}

    static void show(
            Context context,
            SettingsKit kit,
            String uiLang,
            TrackSnapshot track,
            Bitmap artwork,
            LyricsResult lyrics,
            int initialLineIndex
    ) {
        boolean zh = uiLang == null || uiLang.isEmpty() || "auto".equals(uiLang)
                ? "zh".equals(java.util.Locale.getDefault().getLanguage())
                : uiLang.toLowerCase(java.util.Locale.ROOT).startsWith("zh");
        List<LyricsLine> allLines = new ArrayList<>();
        int mappedInitial = 0;
        if (lyrics != null && lyrics.lines != null) {
            for (int i = 0; i < lyrics.lines.size(); i++) {
                LyricsLine line = lyrics.lines.get(i);
                if (line != null && line.text != null && !line.text.trim().isEmpty()) {
                    if (i <= initialLineIndex) {
                        mappedInitial = allLines.size();
                    }
                    allLines.add(line);
                }
            }
        }
        if (allLines.isEmpty()) {
            Toast.makeText(context, zh ? "当前没有可生成海报的歌词" : "No lyrics available for poster",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final int anchorIndex = Math.max(0, Math.min(mappedInitial, allLines.size() - 1));
        final Set<Integer> selectedIndices = new LinkedHashSet<>();
        selectedIndices.add(anchorIndex);

        final LyricPosterRenderer.Options options = new LyricPosterRenderer.Options();
        String key = track == null ? "" : track.stableKey();
        options.paletteColors = ArtworkPalette.from(artwork, key);
        int cached = TrackColorResolver.cached(key);
        options.accentColor = cached != 0 ? cached : ArtworkColorPicker.pick(artwork, key);
        boolean hasTranslation = false;
        boolean hasPronunciation = false;
        for (LyricsLine line : allLines) {
            if (line.translationText != null && !line.translationText.trim().isEmpty()) hasTranslation = true;
            if (line.pronunciationText != null && !line.pronunciationText.trim().isEmpty()) hasPronunciation = true;
        }
        options.showTranslation = hasTranslation;
        options.showPronunciation = false;
        options.showSpotifyBadge = true;
        options.showQuotes = true;

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int sheetHeight = Math.round(dm.heightPixels * 0.88f);
        int previewHeight = Math.max(kit.dp(190), Math.min(kit.dp(260), Math.round(sheetHeight * 0.34f)));

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = kit.vertical();
        root.setPadding(kit.dp(18), kit.dp(10), kit.dp(18), kit.dp(16));
        root.setBackground(kit.rect(kit.p.card, 28f));

        // Subtle drag handle pill.
        View handle = new View(context);
        handle.setBackground(kit.rect(kit.p.line3, 2f));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(kit.dp(38), kit.dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = kit.dp(10);
        root.addView(handle, handleParams);

        // Header bar: centered title + pixel-centered circular close button.
        FrameLayout headerBar = new FrameLayout(context);
        TextView titleView = kit.text(zh ? "分享歌词海报" : "Share Lyric Poster", 18f, kit.p.text, kit.semi);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        headerBar.addView(titleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        ImageView closeBtn = new ImageView(context);
        closeBtn.setScaleType(ImageView.ScaleType.CENTER);
        closeBtn.setBackground(kit.rect(kit.p.surface, 16f));
        closeBtn.setImageDrawable(closeCrossDrawable(kit.p.text2, kit.dp(13), kit.dp(2)));
        closeBtn.setContentDescription(zh ? "关闭" : "Close");
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        headerBar.addView(closeBtn, new FrameLayout.LayoutParams(kit.dp(32), kit.dp(32), Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(headerBar, SettingsKit.matchWrap());

        // Pinned live poster preview stage (transparent container, poster itself is rounded).
        FrameLayout previewStage = new FrameLayout(context);
        ImageView preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        preview.setClipToOutline(true);
        preview.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), kit.dp(16));
            }
        });
        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        previewStage.addView(preview, imgParams);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, previewHeight);
        stageParams.topMargin = kit.dp(12);
        root.addView(previewStage, stageParams);

        // Preview bitmap holder + refresh hook.
        final Bitmap[] previewBitmap = new Bitmap[1];
        final Runnable[] refreshPreviewRef = new Runnable[1];

        // 2-Tab Miuix segmented bar directly below the preview card.
        LinearLayout tabBar = kit.horizontal();
        tabBar.setBackground(kit.rect(kit.p.surface, 12f));
        tabBar.setPadding(kit.dp(4), kit.dp(4), kit.dp(4), kit.dp(4));
        LinearLayout.LayoutParams tabBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, kit.dp(42));
        tabBarParams.topMargin = kit.dp(12);
        root.addView(tabBar, tabBarParams);

        TextView lyricsTabBtn = new TextView(context);
        lyricsTabBtn.setGravity(Gravity.CENTER);
        lyricsTabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        lyricsTabBtn.setTypeface(kit.semi);
        tabBar.addView(lyricsTabBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        TextView styleTabBtn = new TextView(context);
        styleTabBtn.setGravity(Gravity.CENTER);
        styleTabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        styleTabBtn.setTypeface(kit.semi);
        styleTabBtn.setText(zh ? "海报样式" : "Style & Layout");
        tabBar.addView(styleTabBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // Tab content deck occupies ALL remaining middle space (ZERO nested ScrollViews!).
        FrameLayout deck = new FrameLayout(context);
        LinearLayout.LayoutParams deckParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        deckParams.topMargin = kit.dp(10);
        root.addView(deck, deckParams);

        // ---------------- Tab 0: Lyrics Selector (Default, generous scrollable list) ----------------
        LinearLayout lyricsTabPane = kit.vertical();

        LinearLayout pickerHeader = kit.horizontal();
        pickerHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView countHint = kit.text("", 12.5f, kit.p.text2, kit.semi);
        pickerHeader.addView(countHint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView quickThreeBtn = miniPillButton(context, kit, zh ? "连续选3句" : "Next 3");
        pickerHeader.addView(quickThreeBtn, SettingsKit.wrap());

        TextView resetOneBtn = miniPillButton(context, kit, zh ? "仅当前句" : "Reset");
        LinearLayout.LayoutParams resetParams = SettingsKit.wrap();
        resetParams.leftMargin = kit.dp(6);
        pickerHeader.addView(resetOneBtn, resetParams);

        LinearLayout.LayoutParams pickerHeaderParams = SettingsKit.matchWrap();
        pickerHeaderParams.bottomMargin = kit.dp(8);
        lyricsTabPane.addView(pickerHeader, pickerHeaderParams);

        ScrollView lineScroll = new ScrollView(context);
        lineScroll.setVerticalScrollBarEnabled(true);
        lineScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        lineScroll.setBackground(kit.rect(kit.p.surface, 16f));
        lineScroll.setPadding(kit.dp(8), kit.dp(8), kit.dp(8), kit.dp(8));
        lineScroll.setClipToPadding(false);

        LinearLayout lineList = kit.vertical();
        lineScroll.addView(lineList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        lyricsTabPane.addView(lineScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final List<LinearLayout> rowViews = new ArrayList<>();
        final List<TextView> rowMainLabels = new ArrayList<>();
        final List<TextView> rowSubLabels = new ArrayList<>();
        final List<TextView> rowChecks = new ArrayList<>();

        Runnable syncRowVisuals = () -> {
            lyricsTabBtn.setText(zh
                    ? "选择歌词 (" + selectedIndices.size() + "/" + MAX_SELECTED_LINES + ")"
                    : "Lyrics (" + selectedIndices.size() + "/" + MAX_SELECTED_LINES + ")");
            countHint.setText(zh
                    ? "点击多选歌词（已选 " + selectedIndices.size() + " / " + MAX_SELECTED_LINES + " 句）"
                    : "Tap lines to include (" + selectedIndices.size() + " / " + MAX_SELECTED_LINES + ")");
            for (int i = 0; i < rowViews.size(); i++) {
                boolean picked = selectedIndices.contains(i);
                rowViews.get(i).setBackground(picked ? kit.rect(kit.p.accentSoft, 12f) : null);
                rowMainLabels.get(i).setTextColor(picked ? kit.p.accent : kit.p.text);
                rowMainLabels.get(i).setTypeface(picked ? kit.semi : kit.regular);
                TextView sub = rowSubLabels.get(i);
                if (sub != null) {
                    sub.setTextColor(picked ? kit.p.accent : kit.p.text2);
                }
                TextView check = rowChecks.get(i);
                check.setText(picked ? "✓" : "");
                check.setBackground(picked
                        ? kit.rect(kit.p.accent, 11f)
                        : kit.rect(Color.TRANSPARENT, 11f, kit.p.line3, 1.5f));
            }
        };

        for (int index = 0; index < allLines.size(); index++) {
            final int lineIdx = index;
            LyricsLine line = allLines.get(index);
            LinearLayout row = kit.horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(kit.dp(12), kit.dp(11), kit.dp(12), kit.dp(11));
            row.setClickable(true);

            LinearLayout textCol = kit.vertical();
            TextView mainLabel = kit.text(line.text.trim(), 15f, kit.p.text, kit.regular);
            mainLabel.setMaxLines(2);
            mainLabel.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(mainLabel, SettingsKit.matchWrap());

            TextView subLabel = null;
            if (line.translationText != null && !line.translationText.trim().isEmpty()) {
                subLabel = kit.text(line.translationText.trim(), 12.5f, kit.p.text2, kit.regular);
                subLabel.setMaxLines(1);
                subLabel.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams subParams = SettingsKit.matchWrap();
                subParams.topMargin = kit.dp(2);
                textCol.addView(subLabel, subParams);
            }
            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView check = new TextView(context);
            check.setGravity(Gravity.CENTER);
            check.setTextColor(Color.WHITE);
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            check.setTypeface(kit.semi);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(kit.dp(22), kit.dp(22));
            checkParams.leftMargin = kit.dp(10);
            row.addView(check, checkParams);

            row.setOnClickListener(v -> {
                if (selectedIndices.contains(lineIdx)) {
                    if (selectedIndices.size() > 1) {
                        selectedIndices.remove(lineIdx);
                    }
                } else if (selectedIndices.size() < MAX_SELECTED_LINES) {
                    selectedIndices.add(lineIdx);
                } else {
                    Toast.makeText(context,
                            zh ? "最多选择 " + MAX_SELECTED_LINES + " 句歌词" : "Up to " + MAX_SELECTED_LINES + " lines",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                syncRowVisuals.run();
                if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
            });

            rowViews.add(row);
            rowMainLabels.add(mainLabel);
            rowSubLabels.add(subLabel);
            rowChecks.add(check);

            LinearLayout.LayoutParams rowParams = SettingsKit.matchWrap();
            if (index > 0) rowParams.topMargin = kit.dp(3);
            lineList.addView(row, rowParams);
        }

        quickThreeBtn.setOnClickListener(v -> {
            int base = selectedIndices.isEmpty() ? anchorIndex : Collections.min(selectedIndices);
            selectedIndices.clear();
            for (int i = base; i < Math.min(allLines.size(), base + 3); i++) {
                selectedIndices.add(i);
            }
            syncRowVisuals.run();
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        });

        resetOneBtn.setOnClickListener(v -> {
            selectedIndices.clear();
            selectedIndices.add(anchorIndex);
            syncRowVisuals.run();
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        });

        deck.addView(lyricsTabPane, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------------- Tab 1: Style & Layout Controls ----------------
        ScrollView styleScroll = new ScrollView(context);
        styleScroll.setVerticalScrollBarEnabled(false);
        styleScroll.setVisibility(View.GONE);
        LinearLayout styleBody = kit.vertical();
        styleBody.setPadding(0, kit.dp(2), 0, kit.dp(8));
        styleScroll.addView(styleBody, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. Visual Style.
        addSectionLabel(styleBody, kit, zh ? "海报风格" : "Poster Style", false);
        SettingsKit.Choice styleChoice = kit.segmented(Arrays.asList(
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.STYLE_SOLID), zh ? "纯色" : "Solid"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.STYLE_FLUID), zh ? "流光" : "Fluid"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.STYLE_VINYL), zh ? "黑胶" : "Vinyl"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.STYLE_PAPER), zh ? "浅纸" : "Paper")
        ), String.valueOf(options.style), value -> {
            options.style = Integer.parseInt(value);
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        });
        styleBody.addView(styleChoice.view, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(6)));

        // 2. Aspect Ratio.
        addSectionLabel(styleBody, kit, zh ? "画幅比例" : "Aspect Ratio", true);
        SettingsKit.Choice ratioChoice = kit.segmented(Arrays.asList(
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.RATIO_3_4), "3:4"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.RATIO_1_1), "1:1"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.RATIO_9_16), "9:16")
        ), String.valueOf(options.ratio), value -> {
            options.ratio = Integer.parseInt(value);
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        });
        styleBody.addView(ratioChoice.view, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(6)));

        // 3. Alignment & Font Size (side by side).
        LinearLayout typoRow = kit.horizontal();
        LinearLayout alignCol = kit.vertical();
        addSectionLabel(alignCol, kit, zh ? "对齐方式" : "Alignment", false);
        SettingsKit.Choice alignChoice = kit.segmented(Arrays.asList(
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.ALIGN_LEFT), zh ? "居左" : "Left"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.ALIGN_CENTER), zh ? "居中" : "Center")
        ), String.valueOf(options.alignment), value -> {
            options.alignment = Integer.parseInt(value);
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        });
        alignCol.addView(alignChoice.view, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(6)));
        LinearLayout.LayoutParams alignParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f);
        alignParams.rightMargin = kit.dp(8);
        typoRow.addView(alignCol, alignParams);

        LinearLayout sizeCol = kit.vertical();
        addSectionLabel(sizeCol, kit, zh ? "字号风格" : "Text Size", false);
        SettingsKit.Choice sizeChoice = kit.segmented(Arrays.asList(
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.SIZE_COMPACT), zh ? "紧凑" : "Small"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.SIZE_STANDARD), zh ? "标准" : "Normal"),
                new SettingsKit.Option(String.valueOf(LyricPosterRenderer.SIZE_LARGE), zh ? "醒目" : "Large")
        ), String.valueOf(options.sizeMode), value -> {
            options.sizeMode = Integer.parseInt(value);
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        });
        sizeCol.addView(sizeChoice.view, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(6)));
        typoRow.addView(sizeCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        styleBody.addView(typoRow, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(12)));

        // 4. Cover colour swatches.
        addSectionLabel(styleBody, kit, zh ? "封面取色" : "Cover Palette", true);
        LinearLayout swatchRow = kit.horizontal();
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        List<Integer> swatches = new ArrayList<>();
        swatches.add(options.accentColor);
        if (options.paletteColors != null) {
            for (int c : options.paletteColors) {
                if (!swatches.contains(c) && swatches.size() < 6) swatches.add(c);
            }
        }
        final List<TextView> swatchDots = new ArrayList<>();
        for (int index = 0; index < swatches.size(); index++) {
            final int swatchColor = swatches.get(index);
            TextView dot = new TextView(context);
            dot.setGravity(Gravity.CENTER);
            dot.setTextColor(Color.WHITE);
            dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            dot.setTypeface(kit.semi);
            dot.setClickable(true);
            dot.setOnClickListener(v -> {
                options.accentColor = swatchColor;
                updateSwatches(kit, swatchDots, swatches, swatchColor);
                if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(kit.dp(36), kit.dp(36));
            if (index > 0) lp.leftMargin = kit.dp(10);
            swatchRow.addView(dot, lp);
            swatchDots.add(dot);
        }
        updateSwatches(kit, swatchDots, swatches, options.accentColor);
        styleBody.addView(swatchRow, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(8)));

        // 5. Display Element Chips.
        addSectionLabel(styleBody, kit, zh ? "显示元素" : "Elements", true);
        SettingsKit.FlowLayout toggles = kit.flow(8f, 8f);
        toggles.addView(toggleChip(context, kit, zh ? "专辑封面" : "Cover", options.showArtwork, checked -> {
            options.showArtwork = checked;
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        }));
        toggles.addView(toggleChip(context, kit, zh ? "装饰引号" : "Quotes", options.showQuotes, checked -> {
            options.showQuotes = checked;
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        }));
        if (hasTranslation) {
            toggles.addView(toggleChip(context, kit, zh ? "译文" : "Translation", options.showTranslation, checked -> {
                options.showTranslation = checked;
                if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
            }));
        }
        if (hasPronunciation) {
            toggles.addView(toggleChip(context, kit, zh ? "注音" : "Pronunciation", options.showPronunciation, checked -> {
                options.showPronunciation = checked;
                if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
            }));
        }
        toggles.addView(toggleChip(context, kit, zh ? "Spotify 角标" : "Spotify Badge", options.showSpotifyBadge, checked -> {
            options.showSpotifyBadge = checked;
            if (refreshPreviewRef[0] != null) refreshPreviewRef[0].run();
        }));
        styleBody.addView(toggles, SettingsKit.topMargin(SettingsKit.matchWrap(), kit.dp(8)));

        deck.addView(styleScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Tab switcher behavior.
        final int[] activeTab = {0};
        Runnable applyTabState = () -> {
            boolean onLyrics = activeTab[0] == 0;
            lyricsTabPane.setVisibility(onLyrics ? View.VISIBLE : View.GONE);
            styleScroll.setVisibility(onLyrics ? View.GONE : View.VISIBLE);
            lyricsTabBtn.setBackground(onLyrics ? kit.rect(kit.p.card, 9f) : null);
            lyricsTabBtn.setTextColor(onLyrics ? kit.p.text : kit.p.text2);
            styleTabBtn.setBackground(!onLyrics ? kit.rect(kit.p.card, 9f) : null);
            styleTabBtn.setTextColor(!onLyrics ? kit.p.text : kit.p.text2);
        };
        lyricsTabBtn.setOnClickListener(v -> {
            activeTab[0] = 0;
            applyTabState.run();
        });
        styleTabBtn.setOnClickListener(v -> {
            activeTab[0] = 1;
            applyTabState.run();
        });

        // Pinned bottom action bar.
        LinearLayout actions = kit.horizontal();
        TextView saveBtn = kit.button(zh ? "保存相册" : "Save Image", SettingsKit.BUTTON_NORMAL, null);
        TextView shareBtn = kit.button(zh ? "分享海报" : "Share Poster", SettingsKit.BUTTON_PRIMARY, null);
        LinearLayout.LayoutParams leftBtn = new LinearLayout.LayoutParams(0, kit.dp(48), 1f);
        leftBtn.rightMargin = kit.dp(10);
        actions.addView(saveBtn, leftBtn);
        actions.addView(shareBtn, new LinearLayout.LayoutParams(0, kit.dp(48), 1f));
        LinearLayout.LayoutParams actionsParams = SettingsKit.matchWrap();
        actionsParams.topMargin = kit.dp(12);
        root.addView(actions, actionsParams);

        Runnable renderPreview = () -> {
            List<LyricsLine> chosen = selectedLinesInOrder(allLines, selectedIndices);
            Bitmap next = LyricPosterRenderer.render(context, track, artwork, chosen, options, 540);
            Bitmap old = previewBitmap[0];
            previewBitmap[0] = next;
            preview.setImageBitmap(next);
            if (old != null && !old.isRecycled()) {
                old.recycle();
            }
        };
        refreshPreviewRef[0] = renderPreview;

        saveBtn.setOnClickListener(v -> exportPoster(context, track, artwork,
                selectedLinesInOrder(allLines, selectedIndices), options, zh, false, dialog));
        shareBtn.setOnClickListener(v -> exportPoster(context, track, artwork,
                selectedLinesInOrder(allLines, selectedIndices), options, zh, true, dialog));

        syncRowVisuals.run();
        applyTabState.run();
        renderPreview.run();

        dialog.setOnDismissListener(d -> {
            if (previewBitmap[0] != null && !previewBitmap[0].isRecycled()) {
                previewBitmap[0].recycle();
                previewBitmap[0] = null;
            }
        });

        dialog.setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int width = Math.min(dm.widthPixels - kit.dp(16), kit.dp(500));
            window.setLayout(width, sheetHeight);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.y = kit.dp(8);
            lp.dimAmount = 0.56f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();

        // Auto-scroll the lyrics list straight to the long-pressed line after layout.
        lineScroll.post(() -> {
            if (anchorIndex >= 0 && anchorIndex < rowViews.size()) {
                View target = rowViews.get(anchorIndex);
                int scrollY = Math.max(0, target.getTop() - lineScroll.getHeight() / 3);
                lineScroll.scrollTo(0, scrollY);
            }
        });
    }

    private static android.graphics.drawable.Drawable closeCrossDrawable(int color, int sizePx, int strokePx) {
        return new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            {
                paint.setColor(color);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                paint.setStrokeWidth(Math.max(1.5f, strokePx));
            }
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b = getBounds();
                float cx = b.exactCenterX();
                float cy = b.exactCenterY();
                float half = sizePx * 0.36f;
                canvas.drawLine(cx - half, cy - half, cx + half, cy + half, paint);
                canvas.drawLine(cx + half, cy - half, cx - half, cy + half, paint);
            }
            @Override public int getIntrinsicWidth() { return sizePx; }
            @Override public int getIntrinsicHeight() { return sizePx; }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
    }

    private static TextView miniPillButton(Context context, SettingsKit kit, String label) {
        TextView btn = kit.text(label, 12f, kit.p.accent, kit.semi);
        btn.setPadding(kit.dp(10), kit.dp(5), kit.dp(10), kit.dp(5));
        btn.setBackground(kit.rect(kit.p.accentSoft, 10f));
        btn.setClickable(true);
        return btn;
    }

    private static void exportPoster(
            Context context,
            TrackSnapshot track,
            Bitmap artwork,
            List<LyricsLine> chosen,
            LyricPosterRenderer.Options options,
            boolean zh,
            boolean shareAfter,
            Dialog dialog
    ) {
        try {
            Bitmap full = LyricPosterRenderer.render(context, track, artwork, chosen, options, 1080);
            Uri uri = LyricPosterRenderer.saveToGallery(context, full, track);
            full.recycle();
            if (shareAfter) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("image/png");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(intent, zh ? "分享歌词海报" : "Share Lyric Poster"));
            } else {
                Toast.makeText(context, zh ? "已保存到相册 Pictures/Lyriscope" : "Saved to Pictures/Lyriscope",
                        Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        } catch (Exception error) {
            Toast.makeText(context, zh ? "导出失败：" + error.getMessage() : "Export failed: " + error.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private static List<LyricsLine> selectedLinesInOrder(List<LyricsLine> all, Set<Integer> indices) {
        List<Integer> sorted = new ArrayList<>(indices);
        Collections.sort(sorted);
        List<LyricsLine> out = new ArrayList<>(sorted.size());
        for (int idx : sorted) {
            if (idx >= 0 && idx < all.size()) out.add(all.get(idx));
        }
        return out;
    }

    private static void addSectionLabel(LinearLayout parent, SettingsKit kit, String text, boolean topSpace) {
        TextView view = kit.text(text, 12.5f, kit.p.text2, kit.semi);
        LinearLayout.LayoutParams lp = SettingsKit.matchWrap();
        if (topSpace) lp.topMargin = kit.dp(12);
        parent.addView(view, lp);
    }

    private static void updateSwatches(SettingsKit kit, List<TextView> dots, List<Integer> colors, int active) {
        for (int i = 0; i < dots.size(); i++) {
            int color = colors.get(i);
            boolean selected = color == active;
            TextView dot = dots.get(i);
            dot.setText(selected ? "✓" : "");
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setSize(kit.dp(36), kit.dp(36));
            shape.setColor(color);
            shape.setStroke(kit.dp(selected ? 3 : 1), selected ? kit.p.text : kit.p.line3);
            dot.setBackground(shape);
        }
    }

    private interface ToggleCallback {
        void onToggle(boolean checked);
    }

    private static TextView toggleChip(
            Context context,
            SettingsKit kit,
            String label,
            boolean initial,
            ToggleCallback callback
    ) {
        final boolean[] state = {initial};
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        chip.setTypeface(kit.semi);
        chip.setPadding(kit.dp(13), kit.dp(8), kit.dp(13), kit.dp(8));
        Runnable apply = () -> {
            chip.setBackground(kit.rect(state[0] ? kit.p.accentSoft : kit.p.surface, 12f));
            chip.setTextColor(state[0] ? kit.p.accent : kit.p.text2);
        };
        apply.run();
        chip.setOnClickListener(v -> {
            state[0] = !state[0];
            apply.run();
            callback.onToggle(state[0]);
        });
        return chip;
    }
}
