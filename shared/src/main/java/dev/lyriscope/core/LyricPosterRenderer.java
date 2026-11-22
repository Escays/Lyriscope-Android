package dev.lyriscope.core;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Off-screen high-resolution editorial Canvas renderer for Lyric Quote Posters.
 *
 * <p>Supports four visual styles (Solid, Fluid, Vinyl, Paper), three aspect ratios
 * (3:4, 1:1, 9:16), adaptive hero typography, left/center alignment, optional decorative quotes,
 * cover palette swatches, Spotify footer badge, and direct MediaStore export/share without extra
 * manifest FileProvider entries.
 */
final class LyricPosterRenderer {
    static final int STYLE_SOLID = 0;
    static final int STYLE_FLUID = 1;
    static final int STYLE_VINYL = 2;
    static final int STYLE_PAPER = 3;

    static final int RATIO_3_4 = 0;
    static final int RATIO_1_1 = 1;
    static final int RATIO_9_16 = 2;

    static final int ALIGN_LEFT = 0;
    static final int ALIGN_CENTER = 1;

    static final int SIZE_COMPACT = 0;
    static final int SIZE_STANDARD = 1;
    static final int SIZE_LARGE = 2;

    private static final int SPOTIFY_GREEN = 0xFF1DB954;

    static final class Options {
        int style = STYLE_SOLID;
        int ratio = RATIO_3_4;
        int alignment = ALIGN_LEFT;
        int sizeMode = SIZE_STANDARD;
        int accentColor = 0xFF1C2D3D;
        int[] paletteColors;
        boolean showArtwork = true;
        boolean showTranslation = true;
        boolean showPronunciation = false;
        boolean showSpotifyBadge = true;
        boolean showQuotes = true;
    }

    private LyricPosterRenderer() {}

    static int[] dimensionsFor(int ratio, int baseWidth) {
        int width = Math.max(360, baseWidth);
        switch (ratio) {
            case RATIO_1_1:
                return new int[]{width, width};
            case RATIO_9_16:
                return new int[]{width, Math.round(width * 16f / 9f)};
            case RATIO_3_4:
            default:
                return new int[]{width, Math.round(width * 4f / 3f)};
        }
    }

    static Bitmap render(
            Context context,
            TrackSnapshot track,
            Bitmap artwork,
            List<LyricsLine> lines,
            Options options,
            int targetWidth
    ) {
        Options safe = options == null ? new Options() : options;
        int[] dims = dimensionsFor(safe.ratio, targetWidth);
        int width = dims[0];
        int height = dims[1];
        float scale = width / 1080f;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        boolean lightPaper = safe.style == STYLE_PAPER;
        boolean centered = safe.alignment == ALIGN_CENTER;
        int baseColor = safe.accentColor == 0 ? 0xFF1D2C3A : safe.accentColor;

        drawBackground(canvas, width, height, scale, safe.style, baseColor, safe.paletteColors);

        int primaryText = lightPaper ? 0xFF17191E : 0xFFFFFFFF;
        int secondaryText = lightPaper ? 0xB820242B : 0xD8FFFFFF;
        int mutedText = lightPaper ? 0x8C2B3038 : 0x9EFFFFFF;

        float marginX = 88f * scale;
        float topY = (safe.ratio == RATIO_9_16 ? 148f : 92f) * scale;
        float contentWidth = width - marginX * 2f;

        String title = track == null || TextUtils.isEmpty(track.title) ? "Untitled" : track.title.trim();
        String artist = track == null || TextUtils.isEmpty(track.artist) ? "" : track.artist.trim();
        String album = track == null || TextUtils.isEmpty(track.album) ? "" : track.album.trim();

        // Top track header: album artwork + title + artist.
        float cursorY = drawTrackHeader(
                canvas, context, artwork, title, artist,
                marginX, topY, contentWidth, scale,
                safe, lightPaper, centered, baseColor, primaryText, secondaryText);

        // Available vertical zone for the lyric quote block.
        float footerReserve = (safe.showSpotifyBadge ? 142f : 72f) * scale;
        float lyricsTopLimit = cursorY + (safe.ratio == RATIO_1_1 ? 36f : 56f) * scale;
        float availableHeight = Math.max(160f * scale, height - lyricsTopLimit - footerReserve);

        // Adaptive editorial hero sizing based on selected line count & character length.
        int nonEmptyCount = 0;
        int totalChars = 0;
        if (lines != null) {
            for (LyricsLine line : lines) {
                if (line != null && line.text != null && !line.text.trim().isEmpty()) {
                    nonEmptyCount++;
                    totalChars += line.text.trim().length();
                }
            }
        }
        float baseMainSize;
        if (nonEmptyCount <= 1) {
            baseMainSize = totalChars <= 10 ? 104f : (totalChars <= 24 ? 88f : 74f);
        } else if (nonEmptyCount == 2) {
            baseMainSize = totalChars <= 30 ? 84f : 72f;
        } else if (nonEmptyCount == 3) {
            baseMainSize = 70f;
        } else if (nonEmptyCount == 4) {
            baseMainSize = 62f;
        } else {
            baseMainSize = 54f;
        }
        float sizeFactor = safe.sizeMode == SIZE_COMPACT ? 0.84f
                : (safe.sizeMode == SIZE_LARGE ? 1.18f : 1.0f);
        if (safe.ratio == RATIO_1_1) {
            sizeFactor *= 0.92f;
        } else if (safe.ratio == RATIO_9_16) {
            sizeFactor *= 1.06f;
        }

        Typeface mainFace = AppFonts.bold(context);
        Typeface subFace = AppFonts.semiBold(context);

        TextPaint mainPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mainPaint.setTypeface(mainFace);
        mainPaint.setColor(primaryText);

        TextPaint pronPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        pronPaint.setTypeface(subFace);
        pronPaint.setColor(mutedText);

        TextPaint transPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        transPaint.setTypeface(subFace);
        transPaint.setColor(secondaryText);

        Layout.Alignment textAlign = centered
                ? Layout.Alignment.ALIGN_CENTER
                : Layout.Alignment.ALIGN_NORMAL;

        float quoteHeight = safe.showQuotes ? 62f * scale : 0f;
        float textScale = sizeFactor;
        float totalBlockHeight = 0f;
        for (int attempt = 0; attempt < 9; attempt++) {
            float mainSp = baseMainSize * textScale;
            mainPaint.setTextSize(mainSp * scale);
            pronPaint.setTextSize(Math.max(24f, mainSp * 0.44f) * scale);
            transPaint.setTextSize(Math.max(30f, mainSp * 0.56f) * scale);
            totalBlockHeight = quoteHeight + measureLyricsBlock(
                    lines, safe, mainPaint, pronPaint, transPaint,
                    Math.round(contentWidth), scale * textScale, textAlign);
            if (totalBlockHeight <= availableHeight || textScale <= 0.45f) break;
            textScale *= 0.88f;
        }

        float lyricsY = lyricsTopLimit + Math.max(0f, (availableHeight - totalBlockHeight) * 0.44f);

        // Optional decorative editorial opening quote mark.
        if (safe.showQuotes) {
            TextPaint quotePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            quotePaint.setTypeface(Typeface.SERIF);
            quotePaint.setFakeBoldText(true);
            quotePaint.setTextSize(124f * scale);
            quotePaint.setColor(lightPaper ? 0x2417191E : 0x36FFFFFF);
            String glyph = "\u201C";
            float qx = centered
                    ? (width - quotePaint.measureText(glyph)) * 0.5f
                    : marginX - 8f * scale;
            canvas.drawText(glyph, qx, lyricsY + 78f * scale, quotePaint);
            lyricsY += quoteHeight;
        }

        if (lines != null) {
            for (int index = 0; index < lines.size(); index++) {
                LyricsLine line = lines.get(index);
                if (line == null || line.text == null || line.text.trim().isEmpty()) continue;

                if (safe.showPronunciation && line.pronunciationText != null
                        && !line.pronunciationText.trim().isEmpty()) {
                    String pronStr = line.pronunciationText.trim();
                    StaticLayout pron = StaticLayout.Builder.obtain(
                                    pronStr, 0, pronStr.length(),
                                    pronPaint, Math.round(contentWidth))
                            .setAlignment(textAlign)
                            .setLineSpacing(4f * scale * textScale, 1f)
                            .build();
                    canvas.save();
                    canvas.translate(marginX, lyricsY);
                    pron.draw(canvas);
                    canvas.restore();
                    lyricsY += pron.getHeight() + 8f * scale * textScale;
                }

                String body = line.text.trim();
                StaticLayout mainLayout = StaticLayout.Builder.obtain(
                                body, 0, body.length(), mainPaint, Math.round(contentWidth))
                        .setAlignment(textAlign)
                        .setLineSpacing(12f * scale * textScale, 1.04f)
                        .build();
                canvas.save();
                canvas.translate(marginX, lyricsY);
                mainLayout.draw(canvas);
                canvas.restore();
                lyricsY += mainLayout.getHeight();

                if (safe.showTranslation && line.translationText != null
                        && !line.translationText.trim().isEmpty()) {
                    lyricsY += 14f * scale * textScale;
                    String trans = line.translationText.trim();
                    StaticLayout transLayout = StaticLayout.Builder.obtain(
                                    trans, 0, trans.length(), transPaint, Math.round(contentWidth))
                            .setAlignment(textAlign)
                            .setLineSpacing(6f * scale * textScale, 1.04f)
                            .build();
                    canvas.save();
                    canvas.translate(marginX, lyricsY);
                    transLayout.draw(canvas);
                    canvas.restore();
                    lyricsY += transLayout.getHeight();
                }

                lyricsY += 38f * scale * textScale;
            }
        }

        // Spotify editorial footer badge.
        if (safe.showSpotifyBadge) {
            drawSpotifyFooter(canvas, context, width, height, marginX, scale,
                    lightPaper, baseColor, primaryText, mutedText, album, title);
        }

        return bitmap;
    }

    private static float drawTrackHeader(
            Canvas canvas,
            Context context,
            Bitmap artwork,
            String title,
            String artist,
            float marginX,
            float topY,
            float contentWidth,
            float scale,
            Options options,
            boolean lightPaper,
            boolean centered,
            int baseColor,
            int primaryText,
            int secondaryText
    ) {
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(AppFonts.bold(context));
        titlePaint.setTextSize(42f * scale);
        titlePaint.setColor(primaryText);

        TextPaint artistPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        artistPaint.setTypeface(AppFonts.semiBold(context));
        artistPaint.setTextSize(30f * scale);
        artistPaint.setColor(secondaryText);

        boolean hasArt = options.showArtwork && artwork != null && !artwork.isRecycled();
        float artSize = (options.style == STYLE_VINYL ? 164f : 144f) * scale;
        float textLeft = marginX;
        float textWidth = contentWidth;

        if (hasArt) {
            if (centered && options.style != STYLE_VINYL) {
                float artLeft = marginX + (contentWidth - artSize) * 0.5f;
                drawArtworkCard(canvas, artwork, artLeft, topY, artSize, scale, lightPaper);
                float textTop = topY + artSize + 24f * scale;
                StaticLayout titleLayout = StaticLayout.Builder.obtain(
                                title, 0, title.length(), titlePaint, Math.round(contentWidth))
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(2)
                        .setEllipsize(TextUtils.TruncateAt.END)
                        .build();
                canvas.save();
                canvas.translate(marginX, textTop);
                titleLayout.draw(canvas);
                canvas.restore();
                float nextY = textTop + titleLayout.getHeight();
                if (!artist.isEmpty()) {
                    nextY += 8f * scale;
                    StaticLayout artistLayout = StaticLayout.Builder.obtain(
                                    artist, 0, artist.length(), artistPaint, Math.round(contentWidth))
                            .setAlignment(Layout.Alignment.ALIGN_CENTER)
                            .setMaxLines(1)
                            .setEllipsize(TextUtils.TruncateAt.END)
                            .build();
                    canvas.save();
                    canvas.translate(marginX, nextY);
                    artistLayout.draw(canvas);
                    canvas.restore();
                    nextY += artistLayout.getHeight();
                }
                return nextY;
            }

            if (options.style == STYLE_VINYL) {
                drawVinylDisc(canvas, marginX + artSize * 0.94f, topY + artSize * 0.5f,
                        artSize * 0.47f, baseColor, scale);
            }
            drawArtworkCard(canvas, artwork, marginX, topY, artSize, scale, lightPaper);

            float extraRight = options.style == STYLE_VINYL ? artSize * 0.52f : 0f;
            textLeft = marginX + artSize + extraRight + 30f * scale;
            textWidth = Math.max(120f * scale, marginX + contentWidth - textLeft);
        }

        Layout.Alignment headerAlign = (!hasArt && centered)
                ? Layout.Alignment.ALIGN_CENTER
                : Layout.Alignment.ALIGN_NORMAL;

        StaticLayout titleLayout = StaticLayout.Builder.obtain(
                        title, 0, title.length(), titlePaint, Math.round(textWidth))
                .setAlignment(headerAlign)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();

        StaticLayout artistLayout = artist.isEmpty() ? null
                : StaticLayout.Builder.obtain(
                        artist, 0, artist.length(), artistPaint, Math.round(textWidth))
                .setAlignment(headerAlign)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();

        float metaHeight = titleLayout.getHeight()
                + (artistLayout == null ? 0f : 8f * scale + artistLayout.getHeight());
        float metaTop = hasArt
                ? topY + Math.max(0f, (artSize - metaHeight) * 0.5f)
                : topY;

        canvas.save();
        canvas.translate(textLeft, metaTop);
        titleLayout.draw(canvas);
        canvas.restore();

        if (artistLayout != null) {
            canvas.save();
            canvas.translate(textLeft, metaTop + titleLayout.getHeight() + 8f * scale);
            artistLayout.draw(canvas);
            canvas.restore();
        }

        return topY + Math.max(hasArt ? artSize : 0f, metaHeight);
    }

    private static void drawArtworkCard(
            Canvas canvas,
            Bitmap artwork,
            float left,
            float top,
            float size,
            float scale,
            boolean lightPaper
    ) {
        RectF rect = new RectF(left, top, left + size, top + size);
        float radius = 22f * scale;

        // Soft layered elevation shadow.
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(lightPaper ? 0x1F000000 : 0x45000000);
        RectF shadowRect = new RectF(left, top + 10f * scale, left + size, top + size + 14f * scale);
        canvas.drawRoundRect(shadowRect, radius, radius, shadow);

        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(artwork, null, rect, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        canvas.restore();

        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 1.6f * scale));
        rim.setColor(lightPaper ? 0x18000000 : 0x2CFFFFFF);
        canvas.drawRoundRect(rect, radius, radius, rim);
    }

    private static void drawSpotifyFooter(
            Canvas canvas,
            Context context,
            int width,
            int height,
            float marginX,
            float scale,
            boolean lightPaper,
            int baseColor,
            int primaryText,
            int mutedText,
            String album,
            String title
    ) {
        Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
        divider.setColor(lightPaper ? 0x16000000 : 0x22FFFFFF);
        float lineY = height - 118f * scale;
        canvas.drawRect(marginX, lineY, width - marginX, lineY + Math.max(1f, 1.5f * scale), divider);

        float badgeCenterY = height - 62f * scale;
        float iconRadius = 22f * scale;
        float iconCx = marginX + iconRadius;

        drawSpotifyLogo(canvas, iconCx, badgeCenterY, iconRadius,
                lightPaper ? SPOTIFY_GREEN : Color.argb(232, 255, 255, 255),
                lightPaper ? 0xFFF5F1E9 : darken(baseColor, 0.72f));

        TextPaint brandPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        brandPaint.setTypeface(AppFonts.bold(context));
        brandPaint.setTextSize(32f * scale);
        brandPaint.setColor(lightPaper ? primaryText : Color.argb(235, 255, 255, 255));
        Paint.FontMetrics fm = brandPaint.getFontMetrics();
        float textBaseline = badgeCenterY - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText("Spotify", iconCx + iconRadius + 14f * scale, textBaseline, brandPaint);

        if (!TextUtils.isEmpty(album) && !album.equalsIgnoreCase(title)) {
            TextPaint albumPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            albumPaint.setTypeface(AppFonts.regular(context));
            albumPaint.setTextSize(24f * scale);
            albumPaint.setColor(mutedText);
            float maxAlbumW = (width - marginX * 2f) * 0.52f;
            CharSequence clipped = TextUtils.ellipsize(album, albumPaint, maxAlbumW, TextUtils.TruncateAt.END);
            float w = albumPaint.measureText(clipped, 0, clipped.length());
            Paint.FontMetrics afm = albumPaint.getFontMetrics();
            float aBaseline = badgeCenterY - (afm.ascent + afm.descent) * 0.5f;
            canvas.drawText(clipped, 0, clipped.length(), width - marginX - w, aBaseline, albumPaint);
        }
    }

    /** Draws the iconic Spotify circle with three curved soundwave arcs natively on Canvas. */
    private static void drawSpotifyLogo(
            Canvas canvas,
            float cx,
            float cy,
            float radius,
            int circleColor,
            int waveColor
    ) {
        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(circleColor);
        canvas.drawCircle(cx, cy, radius, circlePaint);

        Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setColor(waveColor);

        canvas.save();
        canvas.rotate(8f, cx, cy);

        // Top wave (widest & thickest)
        wavePaint.setStrokeWidth(radius * 0.17f);
        Path p1 = new Path();
        p1.moveTo(cx - radius * 0.52f, cy - radius * 0.24f);
        p1.quadTo(cx, cy - radius * 0.44f, cx + radius * 0.54f, cy - radius * 0.20f);
        canvas.drawPath(p1, wavePaint);

        // Middle wave
        wavePaint.setStrokeWidth(radius * 0.145f);
        Path p2 = new Path();
        p2.moveTo(cx - radius * 0.44f, cy + radius * 0.04f);
        p2.quadTo(cx, cy - radius * 0.12f, cx + radius * 0.45f, cy + radius * 0.07f);
        canvas.drawPath(p2, wavePaint);

        // Bottom wave
        wavePaint.setStrokeWidth(radius * 0.125f);
        Path p3 = new Path();
        p3.moveTo(cx - radius * 0.36f, cy + radius * 0.31f);
        p3.quadTo(cx, cy + radius * 0.18f, cx + radius * 0.36f, cy + radius * 0.34f);
        canvas.drawPath(p3, wavePaint);

        canvas.restore();
    }

    private static float measureLyricsBlock(
            List<LyricsLine> lines,
            Options options,
            TextPaint mainPaint,
            TextPaint pronPaint,
            TextPaint transPaint,
            int width,
            float unit,
            Layout.Alignment align
    ) {
        if (lines == null || lines.isEmpty()) return 0f;
        float total = 0f;
        int drawn = 0;
        for (int index = 0; index < lines.size(); index++) {
            LyricsLine line = lines.get(index);
            if (line == null || line.text == null || line.text.trim().isEmpty()) continue;
            if (drawn > 0) total += 38f * unit;
            if (options.showPronunciation && line.pronunciationText != null
                    && !line.pronunciationText.trim().isEmpty()) {
                String pronStr = line.pronunciationText.trim();
                total += StaticLayout.Builder.obtain(pronStr, 0, pronStr.length(), pronPaint, width)
                        .setAlignment(align)
                        .setLineSpacing(4f * unit, 1f)
                        .build().getHeight() + 8f * unit;
            }
            String body = line.text.trim();
            total += StaticLayout.Builder.obtain(body, 0, body.length(), mainPaint, width)
                    .setAlignment(align)
                    .setLineSpacing(12f * unit, 1.04f)
                    .build().getHeight();
            if (options.showTranslation && line.translationText != null
                    && !line.translationText.trim().isEmpty()) {
                String transStr = line.translationText.trim();
                total += 14f * unit + StaticLayout.Builder.obtain(transStr, 0, transStr.length(), transPaint, width)
                        .setAlignment(align)
                        .setLineSpacing(6f * unit, 1.04f)
                        .build().getHeight();
            }
            drawn++;
        }
        return total;
    }

    private static void drawBackground(
            Canvas canvas,
            int width,
            int height,
            float scale,
            int style,
            int baseColor,
            int[] palette
    ) {
        if (style == STYLE_PAPER) {
            canvas.drawColor(0xFFF5F1E9);
            Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
            wash.setShader(new LinearGradient(0, 0, width, height,
                    0xFFF8F4EC, 0xFFEDE7DC, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, wash);
            Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(Math.max(1f, 2f * scale));
            frame.setColor(0x16000000);
            float inset = 34f * scale;
            canvas.drawRoundRect(new RectF(inset, inset, width - inset, height - inset),
                    28f * scale, 28f * scale, frame);
            return;
        }

        if (style == STYLE_FLUID) {
            int c0 = palette != null && palette.length > 0 ? palette[0] : baseColor;
            int c1 = palette != null && palette.length > 1 ? palette[1] : shiftColor(baseColor, 1.28f);
            int c2 = palette != null && palette.length > 2 ? palette[2] : shiftColor(baseColor, 0.72f);
            int c3 = palette != null && palette.length > 3 ? palette[3] : shiftColor(c1, 0.85f);
            canvas.drawColor(darken(c0, 0.50f));
            drawBlob(canvas, width * 0.16f, height * 0.14f, width * 0.88f, withAlpha(c1, 215));
            drawBlob(canvas, width * 0.88f, height * 0.34f, width * 0.78f, withAlpha(c0, 200));
            drawBlob(canvas, width * 0.24f, height * 0.82f, width * 0.85f, withAlpha(c2, 195));
            drawBlob(canvas, width * 0.82f, height * 0.86f, width * 0.65f, withAlpha(c3, 175));
            Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
            scrim.setShader(new LinearGradient(0, 0, 0, height,
                    0x18000000, 0x52000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, scrim);
            return;
        }

        if (style == STYLE_VINYL) {
            int top = darken(baseColor, 0.56f);
            int bottom = 0xFF0D0F13;
            Paint grad = new Paint(Paint.ANTI_ALIAS_FLAG);
            grad.setShader(new LinearGradient(0, 0, 0, height, top, bottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, grad);
            drawBlob(canvas, width * 0.28f, height * 0.18f, width * 0.62f, withAlpha(shiftColor(baseColor, 1.25f), 88));
            return;
        }

        // STYLE_SOLID: lush Spotify editorial solid with soft ambient lighting.
        int top = shiftColor(baseColor, 1.12f);
        int bottom = darken(baseColor, 0.74f);
        Paint grad = new Paint(Paint.ANTI_ALIAS_FLAG);
        grad.setShader(new LinearGradient(0, 0, width * 0.25f, height, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, grad);
        drawBlob(canvas, width * 0.18f, height * 0.12f, width * 0.72f, withAlpha(0xFFFFFFFF, 24));
    }

    private static void drawBlob(Canvas canvas, float cx, float cy, float radius, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(cx, cy, Math.max(1f, radius),
                color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private static void drawVinylDisc(Canvas canvas, float cx, float cy, float radius, int labelColor, float scale) {
        Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        disc.setColor(0xFF141518);
        canvas.drawCircle(cx, cy, radius, disc);

        // Subtle diagonal specular reflection on the vinyl surface.
        Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
                new int[]{0x00FFFFFF, 0x1EFFFFFF, 0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.28f, 0.5f, 0.74f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 0.96f, sheen);

        Paint groove = new Paint(Paint.ANTI_ALIAS_FLAG);
        groove.setStyle(Paint.Style.STROKE);
        groove.setStrokeWidth(Math.max(1f, 1.3f * scale));
        groove.setColor(0x28FFFFFF);
        for (float factor = 0.42f; factor <= 0.94f; factor += 0.085f) {
            canvas.drawCircle(cx, cy, radius * factor, groove);
        }

        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(shiftColor(labelColor, 1.15f));
        canvas.drawCircle(cx, cy, radius * 0.34f, label);

        Paint spindle = new Paint(Paint.ANTI_ALIAS_FLAG);
        spindle.setColor(0xFF111317);
        canvas.drawCircle(cx, cy, radius * 0.085f, spindle);
    }

    private static int darken(int color, float factor) {
        return Color.rgb(
                Math.max(0, Math.min(255, Math.round(Color.red(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.green(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor)))
        );
    }

    private static int shiftColor(int color, float factor) {
        return Color.rgb(
                Math.max(0, Math.min(255, Math.round(Color.red(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.green(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor)))
        );
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Saves the rendered poster bitmap to MediaStore Pictures/Lyriscope and returns its content Uri. */
    static Uri saveToGallery(Context context, Bitmap bitmap, TrackSnapshot track) throws IOException {
        String safeTitle = track == null || TextUtils.isEmpty(track.title)
                ? "lyrics"
                : track.title.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]+", "_");
        if (safeTitle.length() > 36) safeTitle = safeTitle.substring(0, 36);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        String displayName = "Spotify_Lyrics_" + safeTitle + "_" + stamp + ".png";

        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lyriscope");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore insert failed");
        }
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Failed to encode PNG");
            }
        } catch (IOException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
        }
        return uri;
    }
}
