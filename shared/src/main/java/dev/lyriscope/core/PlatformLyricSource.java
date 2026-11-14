package dev.lyriscope.core;

import java.util.List;

/**
 * A platform that already holds Chinese translations for songs (NetEase Cloud Music, QQ Music).
 *
 * <p>The source only speaks in terms of recordings and rows: "which recording is this" and "what rows
 * does it carry". Matching a recording and putting rows back onto displayed lines is done by
 * {@link PlatformRecordingMatcher} and {@link PlatformTranslationAligner}, so a source cannot decide by
 * itself that a translation belongs to the current song.
 */
interface PlatformLyricSource {
    String id();

    String label();

    /** Search results for one prepared query; an empty list is a normal answer, a failure is not. */
    List<PlatformRecordingMatcher.Candidate> search(String query, int limit) throws Exception;

    PlatformLyricDocument fetch(String candidateId) throws Exception;

    /** A recording plus its original and translated rows, as the platform sent them. */
    final class PlatformLyricDocument {
        final String sourceId;
        final String sourceLabel;
        final String candidateId;
        final String title;
        final String artist;
        final String album;
        final long durationMs;
        final List<PlatformLyricRow> originalRows;
        final List<PlatformLyricRow> translationRows;
        final boolean hasRomanisation;
        /** The catalogue said outright that this recording has no words (NetEase's {@code nolyric}). */
        final boolean noLyricSignal;

        PlatformLyricDocument(
                String sourceId,
                String sourceLabel,
                String candidateId,
                String title,
                String artist,
                String album,
                long durationMs,
                List<PlatformLyricRow> originalRows,
                List<PlatformLyricRow> translationRows,
                boolean hasRomanisation
        ) {
            this(sourceId, sourceLabel, candidateId, title, artist, album, durationMs,
                    originalRows, translationRows, hasRomanisation, false);
        }

        PlatformLyricDocument(
                String sourceId,
                String sourceLabel,
                String candidateId,
                String title,
                String artist,
                String album,
                long durationMs,
                List<PlatformLyricRow> originalRows,
                List<PlatformLyricRow> translationRows,
                boolean hasRomanisation,
                boolean noLyricSignal
        ) {
            this.sourceId = sourceId == null ? "" : sourceId;
            this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
            this.candidateId = candidateId == null ? "" : candidateId;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.album = album == null ? "" : album;
            this.durationMs = Math.max(0L, durationMs);
            this.originalRows = originalRows == null
                    ? java.util.Collections.emptyList()
                    : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(originalRows));
            this.translationRows = translationRows == null
                    ? java.util.Collections.emptyList()
                    : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(translationRows));
            this.hasRomanisation = hasRomanisation;
            this.noLyricSignal = noLyricSignal;
        }

        /**
         * What this document really is: real words, a "no words, enjoy the music" notice, or nothing
         * at all. An empty body is not an error — it is the catalogue's answer, and the page must not
         * fall back to a different recording's lyrics because of it.
         */
        InstrumentalDetector.Verdict verdict() {
            if (noLyricSignal) {
                return InstrumentalDetector.Verdict.INSTRUMENTAL;
            }
            java.util.List<String> texts = new java.util.ArrayList<>();
            for (PlatformLyricRow row : originalRows) {
                if (row.hasText()) {
                    texts.add(row.text);
                }
            }
            InstrumentalDetector.Verdict verdict = InstrumentalDetector.verdictOf(texts);
            if (verdict != InstrumentalDetector.Verdict.NONE) {
                return verdict;
            }
            return texts.isEmpty() ? InstrumentalDetector.Verdict.NO_LYRICS : InstrumentalDetector.Verdict.NONE;
        }

        boolean hasTranslation() {
            for (PlatformLyricRow row : translationRows) {
                if (ChineseLyricText.isUsable(row.text, "")) {
                    return true;
                }
            }
            return false;
        }
    }
}
