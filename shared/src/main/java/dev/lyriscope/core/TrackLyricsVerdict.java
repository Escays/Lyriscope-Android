package dev.lyriscope.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The conclusion reached for one track when the catalogues have nothing to sing.
 *
 * <p>An instrumental recording is not a failed lookup, and a catalogue that returns a notice instead
 * of lyrics is not a reason to ask the other three every time the song comes round again. The verdict
 * is stored per track and replayed on the next visit: the page opens on "纯音乐曲目" instead of
 * re-running four searches that already answered. The reader can always overturn it from the sheet,
 * which is why {@link #forget(String)} exists.
 */
final class TrackLyricsVerdict {
    /** The recording has no vocal at all. */
    static final String INSTRUMENTAL = "instrumental";
    /** The recording may have words, but this catalogue has nothing for it. */
    static final String NO_LYRICS = "no_lyrics";

    private static final String PREFS_NAME = "track_lyrics_verdict_v1";
    private static final String SELECTION_PREFIX = "verdict:";

    private final SharedPreferences prefs;

    TrackLyricsVerdict(Context context) {
        prefs = context == null ? null : context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Marker a result carries so the page can tell a verdict from a lyric sheet. */
    static String selectionKey(String verdict) {
        return SELECTION_PREFIX + (verdict == null ? "" : verdict);
    }

    /** The verdict a result stands for, or "" when it is an ordinary lyric result. */
    static String verdictOf(LyricsResult result) {
        if (result == null || result.selectionPolicyKey == null
                || !result.selectionPolicyKey.startsWith(SELECTION_PREFIX)) {
            return "";
        }
        String verdict = result.selectionPolicyKey.substring(SELECTION_PREFIX.length());
        return INSTRUMENTAL.equals(verdict) || NO_LYRICS.equals(verdict) ? verdict : "";
    }

    void remember(String trackKey, String verdict) {
        if (prefs == null || isEmpty(trackKey)) {
            return;
        }
        if (!INSTRUMENTAL.equals(verdict) && !NO_LYRICS.equals(verdict)) {
            forget(trackKey);
            return;
        }
        prefs.edit().putString(trackKey, verdict).apply();
    }

    void forget(String trackKey) {
        if (prefs == null || isEmpty(trackKey)) {
            return;
        }
        prefs.edit().remove(trackKey).apply();
    }

    /** The stored verdict for one track, or "" when nothing was concluded. */
    String find(String trackKey) {
        if (prefs == null || isEmpty(trackKey)) {
            return "";
        }
        String verdict = prefs.getString(trackKey, "");
        return INSTRUMENTAL.equals(verdict) || NO_LYRICS.equals(verdict) ? verdict : "";
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
