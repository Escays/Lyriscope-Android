package dev.lyriscope.core;

/**
 * Where this project lives, in one place.
 *
 * <p>Updates used to be checked against the upstream ivLyrics repository, which meant the standalone
 * app offered to install someone else's APK. Everything that points at "us" now reads from here, so
 * moving the repository is a one-line change instead of a search across the codebase.
 */
final class ProjectLinks {
    /** GitHub "owner/name". Change this one value when the repository moves. */
    static final String REPOSITORY = "Escays/Lyriscope-Android";

    static final String HOME = "https://github.com/" + REPOSITORY;
    static final String RELEASES = HOME + "/releases";
    static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    static final String RELEASE_DOWNLOAD_PATH = "/" + REPOSITORY + "/releases/download/";

    private ProjectLinks() {
    }
}
