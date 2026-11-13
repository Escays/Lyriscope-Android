package dev.lyriscope.spotify;

import android.content.Context;
import android.content.Intent;

import dev.lyriscope.core.IvLyricsBridge;
import dev.lyriscope.core.IvLyricsHost;

/** Installs Spotify-specific resources and navigation before the shared engine starts. */
public final class SpotifyLyricsHost implements IvLyricsHost {
    private static final SpotifyLyricsHost INSTANCE = new SpotifyLyricsHost();

    private SpotifyLyricsHost() {}

    public static void install() {
        IvLyricsBridge.setHost(INSTANCE);
    }

    @Override public Context wrapContext(Context context) {
        return ContextBridge.wrap(context);
    }

    @Override public Intent createLyricsIntent(Context context) {
        return ContextBridge.lyricsIntent(context);
    }
}
