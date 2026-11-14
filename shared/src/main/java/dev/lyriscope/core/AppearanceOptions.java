package dev.lyriscope.core;

/** Shared display decisions, independent of Android views and of lyrics generation. */
final class AppearanceOptions {
    static final String TITLE_ACTION = "title_tap_action";
    static final String LANDSCAPE_ORIGINAL = "landscape_show_original";
    static final String LANDSCAPE_ARTWORK = "landscape_show_artwork";
    static final String EMPTY_NOTICE = "show_lyrics_not_found";

    private AppearanceOptions() {}

    static String titleAction(String value) {
        if ("disabled".equals(value) || "album".equals(value) || "context".equals(value)) return value;
        return "player"; // Preserve the existing tap-to-return-to-Spotify default.
    }

    /** A navigation destination, never a playback command or an arbitrary external URL. */
    static String spotifyDestination(String value, boolean albumOnly) {
        String uri = value == null ? "" : value.trim();
        if (uri.startsWith("https://open.spotify.com/")) {
            String path = uri.substring("https://open.spotify.com/".length()).split("[?#]", 2)[0];
            uri = "spotify:" + path.replace('/', ':');
        }
        String[] parts = uri.split(":");
        if (parts.length != 3 || !"spotify".equals(parts[0]) || !parts[2].matches("[A-Za-z0-9]{22}")) return "";
        if (albumOnly) return "album".equals(parts[1]) ? uri : "";
        return "album".equals(parts[1]) || "playlist".equals(parts[1]) || "artist".equals(parts[1])
                || "show".equals(parts[1]) ? uri : "";
    }
}
