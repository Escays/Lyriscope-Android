package dev.lyriscope.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AppearanceOptionsTest {
    @Test public void titleAndOrientationValuesFallBackToTheExistingDefaults() {
        assertEquals("player", AppearanceOptions.titleAction("unknown"));
        assertEquals("disabled", AppearanceOptions.titleAction("disabled"));
        assertEquals("context", AppearanceOptions.titleAction("context"));
    }

    @Test public void navigationOnlyAcceptsAlbumOrContextDestinations() {
        String id = "1234567890123456789012";
        assertEquals("spotify:album:" + id, AppearanceOptions.spotifyDestination("spotify:album:" + id, true));
        assertEquals("spotify:playlist:" + id, AppearanceOptions.spotifyDestination("https://open.spotify.com/playlist/" + id + "?si=abc", false));
        assertEquals("", AppearanceOptions.spotifyDestination("spotify:playlist:" + id, true));
        assertEquals("", AppearanceOptions.spotifyDestination("spotify:track:" + id, false));
        assertEquals("", AppearanceOptions.spotifyDestination("javascript:alert(1)", false));
        assertEquals("", AppearanceOptions.spotifyDestination("https://example.com/album/" + id, true));
        assertEquals("", AppearanceOptions.spotifyDestination("", false));
    }
}
