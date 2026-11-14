package dev.lyriscope.core;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.palette.graphics.Palette;

/**
 * Five colours taken from the current cover, for the solid background.
 *
 * <p>The single colour {@link ArtworkColorPicker#pick} returns is chosen to sit behind white text, which
 * is right for the page but says nothing about the song: every cover produced one "readable" colour and
 * the reader had to open a full picker to change their mind. This offers the cover's own palette instead
 * — its dominant tone, its strong and muted accents, and a cool or warm counterpart — each already
 * darkened into the band that keeps lyrics legible.
 *
 * <p>Order is stable for a given cover, so the second swatch means the same thing every time the page is
 * opened. A track without artwork gets a deterministic set derived from its key, never an empty row.
 */
final class ArtworkPalette {
    /** How many swatches the row shows. */
    static final int SIZE = 5;
    /** Two swatches closer than this are the same colour to the eye; the duplicate is skipped. */
    private static final int MIN_CHANNEL_DISTANCE = 36;

    private ArtworkPalette() {
    }

    /** The palette for one cover; never null and never empty. */
    static int[] from(Bitmap artwork, String fallbackKey) {
        int[] picked = new int[SIZE];
        int count = 0;
        if (artwork != null && !artwork.isRecycled()) {
            Palette palette;
            try {
                palette = Palette.from(artwork).generate();
            } catch (RuntimeException unusable) {
                palette = null;
            }
            if (palette != null) {
                int[] candidates = {
                        colorOf(palette.getVibrantSwatch()),
                        colorOf(palette.getDarkVibrantSwatch()),
                        colorOf(palette.getMutedSwatch()),
                        colorOf(palette.getDarkMutedSwatch()),
                        colorOf(palette.getLightMutedSwatch()),
                        colorOf(palette.getDominantSwatch())
                };
                for (int candidate : candidates) {
                    if (candidate == 0) {
                        continue;
                    }
                    int readable = ArtworkColorPicker.readable(
                            Color.red(candidate), Color.green(candidate), Color.blue(candidate));
                    if (isNewColour(picked, count, readable)) {
                        picked[count++] = readable;
                    }
                    if (count == SIZE) {
                        break;
                    }
                }
            }
        }
        // A cover with two or three tones still has to fill the row: vary what was found, then fall back
        // to the key-derived colour, so the row is never half empty.
        int base = count > 0 ? picked[0] : ArtworkColorPicker.fallbackFor(fallbackKey);
        for (int step = 1; count < SIZE && step <= SIZE * 3; step++) {
            int variant = variant(base, step);
            if (isNewColour(picked, count, variant)) {
                picked[count++] = variant;
            }
        }
        return picked;
    }

    /** The five colours for a track that has no cover yet. */
    static int[] fromKey(String trackKey) {
        return from(null, trackKey);
    }

    private static int colorOf(Palette.Swatch swatch) {
        return swatch == null ? 0 : swatch.getRgb();
    }

    /** Brightness and hue shifts of one colour, kept inside the readable band. */
    private static int variant(int base, int step) {
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        if (step % 2 == 1) {
            hsv[2] = hsv[2] * 0.78f;
            hsv[1] = Math.min(1f, hsv[1] * 1.12f);
        } else {
            hsv[0] = (hsv[0] + 28f * (step / 2)) % 360f;
            hsv[2] = Math.min(0.62f, hsv[2] * 1.18f);
        }
        return ArtworkColorPicker.readable(
                Color.red(Color.HSVToColor(hsv)),
                Color.green(Color.HSVToColor(hsv)),
                Color.blue(Color.HSVToColor(hsv)));
    }

    private static boolean isNewColour(int[] picked, int count, int candidate) {
        for (int index = 0; index < count; index++) {
            int existing = picked[index];
            int distance = Math.abs(Color.red(existing) - Color.red(candidate))
                    + Math.abs(Color.green(existing) - Color.green(candidate))
                    + Math.abs(Color.blue(existing) - Color.blue(candidate));
            if (distance < MIN_CHANNEL_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    /** "#rrggbb" for the settings store, which keeps colours as hex strings. */
    static String toHex(int color) {
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
