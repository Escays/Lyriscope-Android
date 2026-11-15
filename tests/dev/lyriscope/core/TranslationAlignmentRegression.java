package dev.lyriscope.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One report: Genesis's "The Fountain of Salmacis" was translated by the engine although NetEase had
 * the whole song in Chinese.
 *
 * <p>The log said the catalogue's rows "do not match the displayed lyrics (originals 0.12)" while every
 * single line compared as identical. The aligner's table could drop a displayed line but not a
 * catalogue row, so a match found in the middle was forgotten the moment the next row failed to pair,
 * and the walk back from the bottom-right corner found nothing. The catalogue's own credits row at the
 * top and its trailing rows were enough to empty the whole alignment.
 */
public final class TranslationAlignmentRegression {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        trailingCatalogueRowsDoNotEraseTheAlignment();
        splitAndJoinedRowsStillMap();
        aDifferentSongIsStillRejected();
        aFullSongWithCreditsAndTailStillAligns();
        System.out.println("translation alignment regression: " + checks + " checks passed");
    }

    /** The reported shape: credits row first, matching rows in the middle, extra rows at the end. */
    private static void trailingCatalogueRowsDoNotEraseTheAlignment() {
        List<String> base = List.of("the first line", "the second line", "the third line");
        List<PlatformLyricRow> originals = PlatformLyricRow.parseLrc(
                "[00:00.00] 作词 : someone\n"
                        + "[00:10.00]the first line\n"
                        + "[00:20.00]the second line\n"
                        + "[00:30.00]the third line\n"
                        + "[00:40.00]outro guitar\n"
                        + "[00:50.00](silence)");
        List<PlatformLyricRow> translated = PlatformLyricRow.parseLrc(
                "[00:10.00]第一行\n[00:20.00]第二行\n[00:30.00]第三行");

        PlatformTranslationAligner.Result result =
                PlatformTranslationAligner.align(base, originals, translated);
        check(result.mappedLines == 3, "all three lines must map, mapped=" + result.mappedLines);
        check(result.translatedLines == 3, "all three lines must be translated, got " + result.translatedLines);
        check(result.originalCoverage() == 1d, "the originals are identical: " + result.originalCoverage());
        check("第一行".equals(result.translations.get(0)), "the first translation lands on the first line");
        check("第三行".equals(result.translations.get(2)), "the third translation lands on the third line");
    }

    /** Row numbers are not a contract: one row may cover two displayed lines and the other way round. */
    private static void splitAndJoinedRowsStillMap() {
        List<String> base = List.of("one long line that the catalogue split", "another line");
        List<PlatformLyricRow> originals = PlatformLyricRow.parseLrc(
                "[00:10.00]one long line that the\n"
                        + "[00:12.00]catalogue split\n"
                        + "[00:20.00]another line\n"
                        + "[00:30.00]tail");
        List<PlatformLyricRow> translated = PlatformLyricRow.parseLrc(
                "[00:10.00]一行被拆开的长句\n[00:20.00]另一行");
        PlatformTranslationAligner.Result result =
                PlatformTranslationAligner.align(base, originals, translated);
        check(result.mappedLines >= 2, "a split row must still map the line it belongs to");
        check(result.translatedLines >= 2, "both lines need their translation, got " + result.translatedLines);

        List<String> shortBase = List.of("one line", "the next line");
        List<PlatformLyricRow> joined = PlatformLyricRow.parseLrc(
                "[00:10.00]one line\n[00:12.00]the next line\n[00:30.00]tail");
        PlatformTranslationAligner.Result joinedResult = PlatformTranslationAligner.align(
                shortBase, joined, PlatformLyricRow.parseLrc("[00:10.00]一行\n[00:12.00]下一行"));
        check(joinedResult.mappedLines == 2, "two catalogue rows may map to two displayed lines");
    }

    /** A low original coverage must still mean "another recording", not "worth translating anyway". */
    private static void aDifferentSongIsStillRejected() {
        List<String> base = List.of("the first line", "the second line", "the third line");
        List<PlatformLyricRow> unrelated = PlatformLyricRow.parseLrc(
                "[00:10.00]something else entirely\n[00:20.00]a different song\n[00:30.00]with other words");
        PlatformTranslationAligner.Result result = PlatformTranslationAligner.align(
                base, unrelated, PlatformLyricRow.parseLrc("[00:10.00]别的\n[00:20.00]歌\n[00:30.00]词"));
        check(result.originalCoverage() < 0.25d,
                "unrelated rows must stay below the acceptance floor: " + result.originalCoverage());
        check(result.translatedLines == 0, "and nothing may be written onto the page");
    }

    /** The reported song at its real size: 50 displayed lines against 75 catalogue rows. */
    private static void aFullSongWithCreditsAndTailStillAligns() {
        StringBuilder catalogue = new StringBuilder("[00:00.00] 作词 : Mike Rutherford\n[00:01.00] 作曲 : Tony Banks\n");
        StringBuilder chinese = new StringBuilder();
        List<String> base = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            String text = "verse " + index + " of the song";
            base.add(text);
            int seconds = 10 + index * 3;
            catalogue.append(String.format("[%02d:%02d.00]%s%n", seconds / 60, seconds % 60, text));
            chinese.append(String.format("[%02d:%02d.00]第 %d 行%n", seconds / 60, seconds % 60, index));
        }
        // The catalogue keeps going after the last displayed line, which is what emptied the old table.
        for (int index = 0; index < 23; index++) {
            catalogue.append(String.format("[%02d:%02d.00]outro %d%n", (250 + index * 3) / 60,
                    (250 + index * 3) % 60, index));
        }

        PlatformTranslationAligner.Result result = PlatformTranslationAligner.align(
                base,
                PlatformLyricRow.parseLrc(catalogue.toString()),
                PlatformLyricRow.parseLrc(chinese.toString()));
        check(result.mappedLines == 50, "every displayed line must map, mapped=" + result.mappedLines);
        check(result.translatedLines == 50, "every displayed line must be translated");
        check(result.originalCoverage() == 1d, "the originals are identical");
    }
}
