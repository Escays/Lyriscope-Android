package dev.lyriscope.core;

/**
 * Whether a catalogue candidate can be the recording that is playing, judged without hearing either.
 *
 * <p>A title is not an identity. Instrumentals, film cues and one-word song names collide, and a search
 * answers with another act's song of the same name — "Tone Twilight Zone" by Cornelius was matched to
 * "Twilight Zone / Twilight Tone" by The Manhattan Transfer, whose length differs by less than a second
 * by pure coincidence. The old scoring accepted it because the title carried enough weight on its own to
 * clear the acceptance threshold.
 *
 * <p>So the decision is not "how similar is this", it is "is there any evidence that this is the same
 * recording". One strong agreement is enough:
 * <ul>
 *   <li>the credited act matches ({@code artistScore} ≥ 0.60);</li>
 *   <li>the album matches, which identifies a release even when the credit is spelled differently;</li>
 *   <li>the title is identical <em>and</em> the length agrees to the second — the same recording indexed
 *       under another credit.</li>
 * </ul>
 * With none of the three there is nothing but a coincidentally similar title, and the candidate is
 * another song.
 */
final class RecordingIdentity {
    /** Above this the two credits name the same act, however the romanisation differs. */
    static final double SAME_ACT_SCORE = 0.60;
    /** Above this the two albums are the same release. */
    static final double SAME_ALBUM_SCORE = 0.50;
    /** The title has to be identical after normalisation, not merely contained. */
    static final double EXACT_TITLE_SCORE = 0.98;
    /** With an exact title, a length this close is the same recording. */
    static final double SAME_RECORDING_DURATION_SECONDS = 3.0;
    /** Below this the credits are unrelated rather than differently spelled. */
    static final double UNRELATED_ACT_SCORE = 0.35;
    /** With unrelated credits, a length gap this wide is another performance. */
    static final double DIFFERENT_RECORDING_DURATION_SECONDS = 6.0;

    private RecordingIdentity() {
    }

    /**
     * @param trackDurationMs      length of the playing track, or 0 when the player has not reported it
     * @param candidateDurationSec length the catalogue reports, or 0 when it reports none
     * @param titleScore           0..1 similarity between the two titles
     * @param artistScore          0..1 similarity between the two credits
     * @param albumScore           0..1 similarity between the two albums
     */
    static boolean isDifferentRecording(
            long trackDurationMs,
            double candidateDurationSec,
            double titleScore,
            double artistScore,
            double albumScore
    ) {
        boolean durationKnown = trackDurationMs > 0L && candidateDurationSec > 0.0;
        double diff = durationKnown ? Math.abs((trackDurationMs / 1000.0) - candidateDurationSec) : -1.0;

        boolean sameAct = artistScore >= SAME_ACT_SCORE;
        boolean sameAlbum = albumScore >= SAME_ALBUM_SCORE;
        boolean identicalTitleAndLength = durationKnown
                && titleScore >= EXACT_TITLE_SCORE
                && diff <= SAME_RECORDING_DURATION_SECONDS;
        if (!sameAct && !sameAlbum && !identicalTitleAndLength) {
            // Nothing but a similar title in common: another song that happens to share a name.
            return true;
        }
        // An unrelated credit with a length this far off is another performance, even when the album
        // name was reused (live records, tributes, compilations that repeat the title).
        return durationKnown && !sameAct
                && diff > DIFFERENT_RECORDING_DURATION_SECONDS
                && artistScore < UNRELATED_ACT_SCORE;
    }
}
