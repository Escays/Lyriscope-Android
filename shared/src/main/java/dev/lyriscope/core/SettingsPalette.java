package dev.lyriscope.core;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

/**
 * Colour tokens of the Miuix settings screen: neutral surfaces and a configurable primary accent
 * (default Miuix blue, optional Monet dynamic colour on Android 12+, or preset key colours).
 */
final class SettingsPalette {
    static final String THEME_SYSTEM = "system";
    static final String THEME_DARK = "dark";
    static final String THEME_LIGHT = "light";

    /** Key colour options inspired by KernelSU-Style-UI-Kit (0 = default Miuix blue). */
    static final int[] ACCENT_PRESETS = {
            0,          // Default Miuix Blue
            0xFF36D167, // Emerald / Spotify Green
            0xFF009688, // Teal
            0xFF9C27B0, // Purple
            0xFFE91E63, // Rose
            0xFFFF9800, // Amber Orange
            0xFFF44336, // Crimson
            0xFFFF7AA2  // Sakura
    };

    final boolean dark;
    final int bg;
    final int surface;
    final int surface2;
    final int surface3;
    final int surface4;
    final int raise;
    final int line;
    final int line2;
    final int line3;
    final int text;
    final int text2;
    final int text3;
    final int text4;
    final int accent;
    final int accentText;
    final int accentSoft;
    final int accentLine;
    final int accentInk;
    final int ink;
    final int inkText;
    final int ok;
    final int okSoft;
    final int warn;
    final int warnSoft;
    final int danger;
    final int dangerSoft;
    final int info;
    final int infoSoft;
    final int switchOff;
    final int ripple;
    final int layerOriginal;
    final int layerPhonetic;
    final int layerTranslation;
    /** Card fill, one step above the page canvas (Miuix card). */
    final int card;
    /** Hairline between two rows of one card; it starts inset, never at the card edge. */
    final int divider;

    // KernelSU-Style-UI-Kit Miuix dark tokens (top.yukonga.miuix.kmp darkColorScheme):
    // pure OLED black canvas (#000000), elevated #242424 cards, #277AF7 primary blue, borderless tonal buttons.
    static final SettingsPalette DARK = new SettingsPalette(true,
            0xFF000000, 0xFF1A1A1C, 0xFF333333, 0xFF434343, 0xFF505050, 0xFF2D2D2D,
            0x14FFFFFF, 0x22FFFFFF, 0xFF404040,
            0xFFF2F2F2, 0xCCFFFFFF, 0x80FFFFFF, 0x66FFFFFF,
            0xFF277AF7, 0xFF4788FF, 0xFF2B3B54, 0xFF277AF7, 0xFFFFFFFF,
            0xFFF2F2F2, 0xFF000000,
            0xFF36D167, 0xFF1A3825, 0xFFF5A623, 0xFF3E2F1B, 0xFFF72727, 0xFF310808,
            0xFF4788FF, 0xFF1E2D45, 0xFF505050, 0x22FFFFFF,
            0xFFD4D4D8, 0xFFE9A23B, 0xFF3FBF8A,
            0xFF242424, 0xFF393939);

    // KernelSU-Style-UI-Kit Miuix light tokens (top.yukonga.miuix.kmp lightColorScheme):
    // #F7F7F7 canvas, pure white cards (#FFFFFF), #3482FF primary blue, #F0F0F0 tonal controls.
    static final SettingsPalette LIGHT = new SettingsPalette(false,
            0xFFF7F7F7, 0xFFF0F0F0, 0xFFF0F0F0, 0xFFE8E8E8, 0xFFDCDCDC, 0xFFFFFFFF,
            0x12000000, 0x1C000000, 0xFFD9D9D9,
            0xFF000000, 0xCC000000, 0x99000000, 0x66000000,
            0xFF3482FF, 0xFF3482FF, 0xFFEAF2FF, 0xFF3482FF, 0xFFFFFFFF,
            0xFF111111, 0xFFFFFFFF,
            0xFF1E9E49, 0xFFDFFAE4, 0xFFD48806, 0xFFFFF0DB, 0xFFE02020, 0xFFF8E2E2,
            0xFF3482FF, 0xFFEAF2FF, 0xFFE6E6E6, 0x16000000,
            0xFF3F3F46, 0xFFC27612, 0xFF15845A,
            0xFFFFFFFF, 0xFFE0E0E0);

    // Over a darkened cover colour: no neutral cards, only translucent white layers, as the bulb
    // sheet draws them.
    static final SettingsPalette ARTWORK = new SettingsPalette(true,
            0x00000000, 0x12FFFFFF, 0x1FFFFFFF, 0x2BFFFFFF, 0x33FFFFFF, 0x0FFFFFFF,
            0x1AFFFFFF, 0x24FFFFFF, 0x33FFFFFF,
            0xFFFFFFFF, 0xE6FFFFFF, 0xA6FFFFFF, 0x70FFFFFF,
            0xFF9CC2FF, 0xFF0B1020, 0x269CC2FF, 0x8C9CC2FF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFF000000,
            0xFF7BE0A3, 0x1F7BE0A3, 0xFFFFC65C, 0x1FFFC65C, 0xFFFF7B72, 0x1FFF7B72,
            0xFF9CC2FF, 0x1A9CC2FF, 0x4DFFFFFF, 0x24FFFFFF,
            0xFFD4D4D8, 0xFFE9A23B, 0xFF3FBF8A,
            0x1AFFFFFF, 0x24FFFFFF);

    private SettingsPalette(
            boolean dark,
            int bg, int surface, int surface2, int surface3, int surface4, int raise,
            int line, int line2, int line3,
            int text, int text2, int text3, int text4,
            int accent, int accentText, int accentSoft, int accentLine, int accentInk,
            int ink, int inkText,
            int ok, int okSoft, int warn, int warnSoft, int danger, int dangerSoft,
            int info, int infoSoft, int switchOff, int ripple,
            int layerOriginal, int layerPhonetic, int layerTranslation,
            int card, int divider
    ) {
        this.dark = dark;
        this.bg = bg;
        this.surface = surface;
        this.surface2 = surface2;
        this.surface3 = surface3;
        this.surface4 = surface4;
        this.raise = raise;
        this.line = line;
        this.line2 = line2;
        this.line3 = line3;
        this.text = text;
        this.text2 = text2;
        this.text3 = text3;
        this.text4 = text4;
        this.accent = accent;
        this.accentText = accentText;
        this.accentSoft = accentSoft;
        this.accentLine = accentLine;
        this.accentInk = accentInk;
        this.ink = ink;
        this.inkText = inkText;
        this.ok = ok;
        this.okSoft = okSoft;
        this.warn = warn;
        this.warnSoft = warnSoft;
        this.danger = danger;
        this.dangerSoft = dangerSoft;
        this.info = info;
        this.infoSoft = infoSoft;
        this.switchOff = switchOff;
        this.ripple = ripple;
        this.layerOriginal = layerOriginal;
        this.layerPhonetic = layerPhonetic;
        this.layerTranslation = layerTranslation;
        this.card = card;
        this.divider = divider;
    }

    static SettingsPalette forTheme(String theme) {
        return THEME_LIGHT.equals(theme) ? LIGHT : DARK;
    }

    static SettingsPalette resolve(Context context, String themeMode, boolean monetEnabled, int customAccent) {
        boolean isDark;
        if (THEME_LIGHT.equals(themeMode)) {
            isDark = false;
        } else if (THEME_SYSTEM.equals(themeMode) && context != null) {
            int nightMask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            isDark = nightMask == Configuration.UI_MODE_NIGHT_YES;
        } else {
            isDark = true;
        }
        SettingsPalette base = isDark ? DARK : LIGHT;
        int accentColor = customAccent;
        if (monetEnabled && context != null && Build.VERSION.SDK_INT >= 31) {
            try {
                int resId = isDark
                        ? android.R.color.system_accent1_300
                        : android.R.color.system_accent1_500;
                int systemAccent = context.getResources().getColor(resId, context.getTheme());
                if ((systemAccent & 0x00FFFFFF) != 0) {
                    accentColor = systemAccent | 0xFF000000;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return accentColor == 0 ? base : base.withAccent(accentColor);
    }

    SettingsPalette withAccent(int color) {
        int solid = color | 0xFF000000;
        int soft = blend(dark ? 0xFF1A1A1A : 0xFFFFFFFF, solid, dark ? 0.24f : 0.14f);
        int textTone = dark ? blend(solid, 0xFFFFFFFF, 0.22f) : blend(solid, 0xFF000000, 0.08f);
        double lum = ArtworkColorPicker.luminance(solid);
        int inkOnAccent = lum > 0.45d ? 0xFF111111 : 0xFFFFFFFF;
        return new SettingsPalette(
                dark,
                bg, surface, surface2, surface3, surface4, raise,
                line, line2, line3,
                text, text2, text3, text4,
                solid, textTone, soft, solid, inkOnAccent,
                ink, inkText,
                ok, okSoft, warn, warnSoft, danger, dangerSoft,
                textTone, soft, switchOff, ripple,
                layerOriginal, layerPhonetic, layerTranslation,
                card, divider
        );
    }

    private static int blend(int from, int to, float fraction) {
        float t = Math.max(0f, Math.min(1f, fraction));
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(255, Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
    }

    String theme() {
        return dark ? THEME_DARK : THEME_LIGHT;
    }
}
