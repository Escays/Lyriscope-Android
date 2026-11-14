package dev.lyriscope.core;

/**
 * Whether a catalogue candidate can be the recording that is playing, judged from the credit and the
 * length alone.
 *
 * <p>A title is not an identity: instrumentals, film cues and one-word song names all collide, and the
 * catalogue answers a search with another act's song of the same name. That candidate used to be
 * accepted whenever the title matched, because the title carried enough weight to clear the acceptance
 * threshold on its own — which is how an instrumental track displayed a different band's lyrics.
 *
 * <p>The rule is deliberately narrow, so genuine matches survive:
 * <ul>
 *   <li>a wholly different act stays only if the length agrees to the second <em>and</em> the album
 *       matches — the same recording, indexed under another credit;</li>
 *   <li>a merely dissimilar act stays only if the length agrees within a few seconds;</li>
 *   <li>with no length to compare, only a clearly different act is disqualifying.</li>
 * </ul>
 */
final class RecordingIdentity {
    /** A different act with this little in common cannot be the same recording on credit alone. */
    static final double MIN_DISTINCT_ARTIST_SCORE = 0.20;
    /** Below this the names are merely dissimilar rather than plainly different. */
    static final double MIN_SIMILAR_ARTIST_SCORE = 0.35;
    /** A different credit still passes when the lengths agree to the second. */
    static final double SAME_RECORDING_DURATION_SECONDS = 3.0;
    /** A dissimilar credit fails once the lengths are this far apart. */
    static final double DIFFERENT_RECORDING_DURATION_SECONDS = 6.0;
    /** An album this close is the same album, spelled differently. */
    static final double SAME_ALBUM_SCORE = 0.5;

    private RecordingIdentity() {
    }

    /**
     * @param trackDurationMs      length of the playing track, or 0 when the player has not reported it
     * @param candidateDurationSec length the catalogue reports, or 0 when it reports none
     * @param artistScore          0..1 similarity between the two credits
     * @param albumScore           0..1 similarity between the two albums
     */
    static boolean isDifferentRecording(
            long trackDurationMs,
            double candidateDurationSec,
            double artistScore,
            double albumScore
    ) {
        if (trackDurationMs <= 0L || candidateDurationSec <= 0.0) {
            return artistScore < MIN_DISTINCT_ARTIST_SCORE;
        }
        double diff = Math.abs((trackDurationMs / 1000.0) - candidateDurationSec);
        if (artistScore < MIN_DISTINCT_ARTIST_SCORE) {
            return !(diff <= SAME_RECORDING_DURATION_SECONDS && albumScore >= SAME_ALBUM_SCORE);
        }
        return artistScore < MIN_SIMILAR_ARTIST_SCORE && diff > DIFFERENT_RECORDING_DURATION_SECONDS;
    }
}
