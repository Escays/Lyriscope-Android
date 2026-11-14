package dev.lyriscope.core;

import java.util.ArrayList;
import java.util.List;

/**
 * An instrumental track that borrowed another band's lyrics, a catalogue that answers "no words" with
 * a sentence, and a result list that ends in a dead end.
 *
 * <p>Fixtures are the answers the four catalogues actually gave for one instrumental track: LRCLIB
 * returned a namesake by another act, NetEase returned an empty body, QQ returned the sentence
 * "这首歌是纯音乐请欣赏". The three parts below pin the decisions that used to be wrong.
 */
public final class InstrumentalVerdictRegression {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        noticesAreRecognised();
        lyricSheetsMentioningTheWordStayLyricSheets();
        documentVerdicts();
        namesakesAreRejected();
        genuineMatchesSurvive();
        verdictMarkersRoundTrip();
        System.out.println("instrumental verdict regression: " + checks + " checks passed");
    }

    /** Every wording the catalogues use for "this recording has no words". */
    private static void noticesAreRecognised() {
        check(InstrumentalDetector.verdictOf(List.of("这首歌是纯音乐请欣赏"))
                        == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "QQ's exact sentence for this track must read as instrumental");
        check(InstrumentalDetector.verdictOf(List.of("纯音乐请欣赏"))
                        == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "the bare notice must read as instrumental");
        check(InstrumentalDetector.verdictOf(List.of("此歌曲为没有填词的纯音乐请您欣赏"))
                        == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "NetEase's long notice must read as instrumental");
        check(InstrumentalDetector.verdictOf(List.of("Instrumental"))
                        == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "the English label must read as instrumental");
        check(InstrumentalDetector.verdictOf(List.of("♪ ♪ ♪"))
                        == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "a row of music notes is the same answer drawn differently");
        check(InstrumentalDetector.verdictOf(List.of("这首是纯音乐，请欣赏"))
                        == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "the courtesy tail must not stop the notice from being read");
        check(InstrumentalDetector.verdictOf(List.of("纯音乐曲目"))
                        == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "the app's own wording must be recognised as well");
        check(InstrumentalDetector.verdictOf(List.of("暂无歌词"))
                        == InstrumentalDetector.Verdict.NO_LYRICS,
                "a plain 'no lyrics' notice must not claim the track is instrumental");
        check(InstrumentalDetector.verdictOf(List.of("没有歌词"))
                        == InstrumentalDetector.Verdict.NO_LYRICS,
                "the simplified spelling must be recognised too");
        check(InstrumentalDetector.verdictOf(new ArrayList<>()) == InstrumentalDetector.Verdict.NONE,
                "an empty body is the caller's business, not a verdict");
        check(InstrumentalDetector.verdictOf(List.of("   ", "")) == InstrumentalDetector.Verdict.NONE,
                "blank rows are not a notice");
    }

    /** The all-or-nothing rule: one real line keeps the sheet a lyric sheet. */
    private static void lyricSheetsMentioningTheWordStayLyricSheets() {
        check(InstrumentalDetector.verdictOf(List.of("梦中没有错与对", "梦中没有安也有危"))
                        == InstrumentalDetector.Verdict.NONE,
                "real lyrics must stay lyrics");
        check(InstrumentalDetector.verdictOf(List.of("纯音乐请欣赏", "梦中没有错与对"))
                        == InstrumentalDetector.Verdict.NONE,
                "one notice among real lines must not turn the sheet into an instrumental");
        check(InstrumentalDetector.verdictOf(List.of("我喜欢这首纯音乐，因为它安静"))
                        == InstrumentalDetector.Verdict.NONE,
                "a lyric line that mentions 纯音乐 is still a lyric line");
        check(!InstrumentalDetector.isNotice("我喜欢这首纯音乐，因为它安静"),
                "a sentence mentioning the word is not the notice itself");
    }

    /** What each catalogue's answer turns into, including NetEase's explicit flag. */
    private static void documentVerdicts() {
        PlatformLyricSource.PlatformLyricDocument instrumental =
                new PlatformLyricSource.PlatformLyricDocument(
                        "qq", "QQ音乐", "x", "", "", "", 0L,
                        PlatformLyricRow.parseLrc("[00:00.00]这首歌是纯音乐请欣赏"),
                        List.of(), false);
        check(instrumental.verdict() == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "QQ's notice must reach the page as an instrumental verdict");

        PlatformLyricSource.PlatformLyricDocument emptyNetease =
                new PlatformLyricSource.PlatformLyricDocument(
                        "netease", "网易云音乐", "x", "", "", "", 0L,
                        List.of(), List.of(), false);
        check(emptyNetease.verdict() == InstrumentalDetector.Verdict.NO_LYRICS,
                "an empty body is 'no lyrics here', not an error");

        PlatformLyricSource.PlatformLyricDocument flagged =
                new PlatformLyricSource.PlatformLyricDocument(
                        "netease", "网易云音乐", "x", "", "", "", 0L,
                        List.of(), List.of(), false, true);
        check(flagged.verdict() == InstrumentalDetector.Verdict.INSTRUMENTAL,
                "NetEase's nolyric flag means the recording has no words");

        PlatformLyricSource.PlatformLyricDocument real =
                new PlatformLyricSource.PlatformLyricDocument(
                        "netease", "网易云音乐", "x", "", "", "", 0L,
                        PlatformLyricRow.parseLrc("[00:01.00]第一句\n[00:05.00]第二句"),
                        List.of(), false);
        check(real.verdict() == InstrumentalDetector.Verdict.NONE,
                "a document with lyrics has no verdict");
    }

    /** The reported bug: another band's song with a similar title, matched on the title alone. */
    private static void namesakesAreRejected() {
        // The exact case: "Tone Twilight Zone" by Cornelius / album "Point" / 234.3s against
        // "Twilight Zone / Twilight Tone" by The Manhattan Transfer / album "1980" / 235.0s.
        // The title is a near miss and the lengths agree by coincidence; the credit is another act.
        check(RecordingIdentity.isDifferentRecording(234_266L, 235.0, 0.85, 0.50, 0.0),
                "a near-miss title with an unrelated act and another album is a different recording");
        check(RecordingIdentity.isDifferentRecording(234_266L, 238.0, 0.85, 0.50, 0.0),
                "the synced namesake must be rejected as well");
        check(RecordingIdentity.isDifferentRecording(0L, 235.0, 0.85, 0.50, 0.0),
                "without a length, a near-miss title and another album are still another recording");
        check(RecordingIdentity.isDifferentRecording(200_000L, 240.0, 0.40, 0.10, 0.0),
                "a different act with a much shorter length is another recording");
        check(RecordingIdentity.isDifferentRecording(200_000L, 226.0, 1.0, 0.30, 0.72),
                "an unrelated act far from the length is another take even on the same album");
    }

    /** The rules must not start rejecting the matches that are right. */
    private static void genuineMatchesSurvive() {
        check(!RecordingIdentity.isDifferentRecording(234_266L, 234.6, 1.0, 1.0, 1.0),
                "the same act, title and album is the same recording");
        check(!RecordingIdentity.isDifferentRecording(200_000L, 200.5, 1.0, 0.95, 1.0),
                "the same act and the same length is the same recording");
        check(!RecordingIdentity.isDifferentRecording(234_266L, 235.0, 0.85, 0.50, 0.72),
                "another credit on the same album is the same recording");
        check(!RecordingIdentity.isDifferentRecording(234_266L, 234.9, 1.0, 0.20, 0.0),
                "an identical title and length is the same recording under another credit");
        check(!RecordingIdentity.isDifferentRecording(0L, 240.0, 0.85, 0.90, 0.0),
                "an unknown length must not sink a matching act");
        check(!RecordingIdentity.isDifferentRecording(200_000L, 206.0, 0.95, 0.65, 0.0),
                "a matching act survives a six-second gap");
    }

    /** A verdict has to be recognisable on the page, or the card cannot offer to undo it. */
    private static void verdictMarkersRoundTrip() {
        LyricsResult instrumental = LyricsResult.empty("纯音乐曲目")
                .withSelection("", TrackLyricsVerdict.selectionKey(TrackLyricsVerdict.INSTRUMENTAL));
        check(TrackLyricsVerdict.INSTRUMENTAL.equals(TrackLyricsVerdict.verdictOf(instrumental)),
                "an instrumental result must report its verdict");
        LyricsResult noLyrics = LyricsResult.empty("没有可用歌词")
                .withSelection("", TrackLyricsVerdict.selectionKey(TrackLyricsVerdict.NO_LYRICS));
        check(TrackLyricsVerdict.NO_LYRICS.equals(TrackLyricsVerdict.verdictOf(noLyrics)),
                "a no-lyrics result must report its verdict");
        LyricsResult ordinary = new LyricsResult(
                List.of(new LyricsLine(0L, 0L, "Line", List.of())), "LRCLIB", "", false
        ).withSelection("lrclib", "type-first|auto");
        check(TrackLyricsVerdict.verdictOf(ordinary).isEmpty(),
                "an ordinary lyric result carries no verdict");
        check(TrackLyricsVerdict.verdictOf(null).isEmpty(), "a missing result carries no verdict");
    }
}
