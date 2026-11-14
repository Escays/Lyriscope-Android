package dev.lyriscope.core;

import java.util.List;
import java.util.Locale;

/**
 * Tells a catalogue's "this recording has no words" notice from actual lyrics.
 *
 * <p>NetEase, QQ and Kugou answer an instrumental track with a single sentence instead of an empty
 * body — "这首歌是纯音乐请欣赏", "此歌曲为没有填词的纯音乐请您欣赏", "暂无歌词" — and pasting one of
 * those onto the page is how a lyric card ends up insisting an instrumental has words. The notice is
 * only a notice when <em>every</em> usable row is one: a real lyric sheet mentioning 纯音乐 in one line
 * must stay a lyric sheet, so the check is all-or-nothing and never per line.
 *
 * <p>{@link Verdict#INSTRUMENTAL} and {@link Verdict#NO_LYRICS} differ only in what the reader is told:
 * the first means the recording has no vocal at all, the second means the catalogue simply has nothing.
 */
final class InstrumentalDetector {
    enum Verdict {
        /** Rows carry real lyric text, or there are no rows to judge. */
        NONE,
        /** Every row is the catalogue's "no words, enjoy the music" notice. */
        INSTRUMENTAL,
        /** Every row is the catalogue's "no lyrics here" notice without claiming the track is instrumental. */
        NO_LYRICS
    }

    private InstrumentalDetector() {
    }

    /** The verdict for a whole lyric body; empty or blank input is {@link Verdict#NONE}. */
    static Verdict verdictOf(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Verdict.NONE;
        }
        boolean sawRow = false;
        boolean sawInstrumental = false;
        for (String text : texts) {
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            sawRow = true;
            Verdict line = noticeOf(text);
            if (line == Verdict.NONE) {
                return Verdict.NONE;
            }
            if (line == Verdict.INSTRUMENTAL) {
                sawInstrumental = true;
            }
        }
        if (!sawRow) {
            return Verdict.NONE;
        }
        return sawInstrumental ? Verdict.INSTRUMENTAL : Verdict.NO_LYRICS;
    }

    /** True when a single row reads as a catalogue notice rather than as a lyric line. */
    static boolean isNotice(String text) {
        return noticeOf(text) != Verdict.NONE;
    }

    /**
     * One row's verdict. Only letters and digits survive normalisation, so a notice survives quotes,
     * brackets, spaces, "♪" decorations and full-width punctuation.
     */
    private static Verdict noticeOf(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            // A row that keeps punctuation and nothing else is a "♪♪♪" line — the catalogue's way of
            // drawing an instrumental break, not a word to sing.
            return text.trim().isEmpty() ? Verdict.NONE : Verdict.INSTRUMENTAL;
        }
        // The marker may be introduced ("这首歌是…", "此歌曲为没有填词的…") but the row has to end on
        // it: a lyric line that merely mentions 纯音乐 in the middle is a lyric line, not a notice.
        for (String core : INSTRUMENTAL_CORES) {
            int index = normalized.lastIndexOf(core);
            if (index >= 0 && isInstrumentalTail(normalized.substring(index + core.length()))) {
                return Verdict.INSTRUMENTAL;
            }
        }
        for (String marker : NO_LYRICS_MARKERS) {
            if (normalized.endsWith(marker)) {
                return Verdict.NO_LYRICS;
            }
        }
        return Verdict.NONE;
    }

    /** Nothing but the "enjoy the music" courtesy may follow the marker. */
    private static boolean isInstrumentalTail(String tail) {
        for (String allowed : INSTRUMENTAL_TAILS) {
            if (tail.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /** Letters and digits only, lower-cased: " 这首歌是纯音乐，请欣赏♪ " → "这首歌是纯音乐请欣赏". */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Phrases that mean "there is no vocal". A row has to <em>end</em> on one of them: the catalogues
     * introduce the same sentence countless ways ("这首歌是…", "此歌曲为没有填词的…"), but a lyric line
     * that merely mentions the word in the middle is a lyric line.
     */
    private static final String[] INSTRUMENTAL_CORES = {
            "纯音乐", "純音樂",
            "instrumental", "novocal", "offvocal", "backingtrack",
            "インスト", "インストゥルメンタル", "연주곡"
    };

    /** What may follow the marker and still leave the row a notice rather than a sentence. */
    private static final String[] INSTRUMENTAL_TAILS = {
            "", "请欣赏", "请您欣赏", "敬请欣赏", "欣赏", "请聆听", "曲目", "version", "track"
    };

    /** Phrases that only mean "we have nothing", never "this track has no vocal". */
    private static final String[] NO_LYRICS_MARKERS = {
            "暂无歌词", "暫無歌詞", "没有歌词", "沒有歌詞", "歌词暂缺", "歌詞暫缺",
            "nolyrics", "nolyric", "lyricsnotavailable", "lyricsunavailable"
    };
}
