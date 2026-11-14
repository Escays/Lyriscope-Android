package dev.lyriscope.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Which catalogue result the reader picked by hand for a track.
 *
 * <p>The lyric cache remembers the result itself, but a cache clear — saving provider or translation
 * settings, clearing one track — takes it with it, and the next visit quietly returns to the automatic
 * match. Keeping the pick separately means the choice can be replayed instead of silently dropped.
 */
final class ManualLyricsPicks {
    private static final String PREFS_NAME = "manual_lyrics_picks_v1";
    private static final String SEPARATOR = "\u0000";

    private final SharedPreferences prefs;

    ManualLyricsPicks(Context context) {
        prefs = context == null ? null : context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Records the pick for one track; an empty candidate id clears it. */
    void remember(String trackKey, String source, String candidateId) {
        if (prefs == null || trackKey == null || trackKey.trim().isEmpty()) {
            return;
        }
        if (source == null || source.trim().isEmpty()
                || candidateId == null || candidateId.trim().isEmpty()) {
            forget(trackKey);
            return;
        }
        prefs.edit().putString(trackKey, source + SEPARATOR + candidateId).apply();
    }

    void forget(String trackKey) {
        if (prefs == null || trackKey == null || trackKey.trim().isEmpty()) {
            return;
        }
        prefs.edit().remove(trackKey).apply();
    }

    /** {source, candidateId}, or null when this track has no manual pick. */
    String[] find(String trackKey) {
        if (prefs == null || trackKey == null || trackKey.trim().isEmpty()) {
            return null;
        }
        String value = prefs.getString(trackKey, "");
        if (value == null || value.isEmpty()) {
            return null;
        }
        int separator = value.indexOf(SEPARATOR);
        if (separator <= 0 || separator == value.length() - 1) {
            return null;
        }
        return new String[]{value.substring(0, separator), value.substring(separator + 1)};
    }
}
