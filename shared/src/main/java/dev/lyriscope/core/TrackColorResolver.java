package dev.lyriscope.core;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared asynchronous cover-color resolver with a bounded LRU cache.
 *
 * <p>Keeps AndroidX Palette quantization off the main thread while guaranteeing that the full
 * lyrics page, picture-in-picture view, player card and lyric poster all agree on the exact same
 * colour for a given track cover.
 */
final class TrackColorResolver {
    interface Callback {
        void onColorResolved(String trackKey, int color, boolean fromArtwork);
    }

    private static final int MAX_ENTRIES = 64;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lyriscope-color-picker");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, ColorEntry> CACHE = new LinkedHashMap<String, ColorEntry>(MAX_ENTRIES, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ColorEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private static final class ColorEntry {
        final int color;
        final boolean fromArtwork;
        final int bitmapIdentity;

        ColorEntry(int color, boolean fromArtwork, int bitmapIdentity) {
            this.color = color;
            this.fromArtwork = fromArtwork;
            this.bitmapIdentity = bitmapIdentity;
        }
    }

    private TrackColorResolver() {}

    /** Returns the cached colour if available, or 0 if not yet resolved. */
    static synchronized int cached(String trackKey) {
        if (trackKey == null || trackKey.isEmpty()) return 0;
        ColorEntry entry = CACHE.get(trackKey);
        return entry == null ? 0 : entry.color;
    }

    /**
     * Resolves a track's stable cover colour. Delivers immediately if already cached for this
     * artwork; otherwise delivers a deterministic placeholder immediately (if uncached) and
     * computes the Palette colour on a background thread before calling back on the main thread.
     */
    static void resolve(String trackKey, Bitmap artwork, Callback callback) {
        if (callback == null || trackKey == null || trackKey.isEmpty()) return;
        boolean hasArtwork = artwork != null && !artwork.isRecycled()
                && artwork.getWidth() > 0 && artwork.getHeight() > 0;
        int identity = hasArtwork ? System.identityHashCode(artwork) : 0;

        ColorEntry existing;
        synchronized (TrackColorResolver.class) {
            existing = CACHE.get(trackKey);
        }

        if (existing != null) {
            if (!hasArtwork || (existing.fromArtwork && existing.bitmapIdentity == identity)) {
                callback.onColorResolved(trackKey, existing.color, existing.fromArtwork);
                return;
            }
            // Cached placeholder or older bitmap; show existing immediately while upgrading async.
            callback.onColorResolved(trackKey, existing.color, existing.fromArtwork);
        } else {
            int placeholder = ArtworkColorPicker.fallbackFor(trackKey);
            if (!hasArtwork) {
                synchronized (TrackColorResolver.class) {
                    CACHE.put(trackKey, new ColorEntry(placeholder, false, 0));
                }
                callback.onColorResolved(trackKey, placeholder, false);
                return;
            }
            callback.onColorResolved(trackKey, placeholder, false);
        }

        final Bitmap source = artwork;
        EXECUTOR.execute(() -> {
            if (source.isRecycled()) return;
            int picked = ArtworkColorPicker.pick(source, trackKey);
            synchronized (TrackColorResolver.class) {
                CACHE.put(trackKey, new ColorEntry(picked, true, identity));
            }
            MAIN.post(() -> callback.onColorResolved(trackKey, picked, true));
        });
    }
}
