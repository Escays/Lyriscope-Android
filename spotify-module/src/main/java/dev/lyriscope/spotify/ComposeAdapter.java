package dev.lyriscope.spotify;

import android.app.Activity;
import android.content.Context;
import android.media.session.MediaSession;
import android.util.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Map;
import dev.lyriscope.core.IvLyricsBridge;
import dev.lyriscope.core.SpotifyMetadataBridge;

/** Calls the host's own Compose AndroidView interop. No second Compose runtime is bundled. */
public final class ComposeAdapter {
    private static final String TAG = "ivLyricsPatch";
    private static volatile Interop interop;
    private static volatile InlineInterop inlineInterop;
    private static volatile Availability availability;

    private ComposeAdapter() {}

    public static void initialize(Context context) {
        IvLyricsBridge.initialize(context);
        Log.i(TAG, "ivLyrics initialized in " + context.getPackageName());
    }

    public static void onMediaSession(MediaSession session, String sessionTag) {
        // Spotify also constructs Media3 sessions for other surfaces. Bind only its music session.
        // The constructor tag is available on API 26; MediaController.getTag requires API 30.
        if (session == null || !"spotify-media-session".equals(sessionTag)) return;
        IvLyricsBridge.onMediaSession(session);
        SpotifyMetadataBridge.onMediaSession(session);
        SpotifyKaraokePlayback.onMediaSession(session);
        Log.i(TAG, "Captured in-process MediaSession");
    }

    public static void openLyrics(Activity activity) {
        IvLyricsBridge.openLyrics(activity);
        activity.finish();
    }

    public static void renderCard(SpotifyMapping mapping, Object composer) {
        try {
            Interop current = interopFor(mapping, composer);
            // Give Compose the ratio too, including its intrinsic measurement of AndroidView.
            current.render.invoke(null, current.factory, current.squareModifier, null, composer, 0, 4);
        } catch (ReflectiveOperationException error) {
            // Failing visibly is preferable to rendering the obsolete native lyric source.
            throw new IllegalStateException("ivLyrics could not mount Spotify's Compose AndroidView", error);
        }
    }

    /** Resolve optional mappings before entering any Compose groups. */
    static void prepareInline(SpotifyMapping mapping, Object composer) throws ReflectiveOperationException {
        inlineInteropFor(mapping, composer);
    }

    public static void renderInline(SpotifyMapping mapping, Object composer) throws ReflectiveOperationException {
        InlineInterop current = inlineInteropFor(mapping, composer);
        current.render.invoke(null, current.factory, current.modifier, null, composer, 0, 4);
    }

    private static InlineInterop inlineInteropFor(SpotifyMapping mapping, Object composer) throws ReflectiveOperationException {
        InlineInterop current = inlineInterop;
        if (current == null) {
            synchronized (ComposeAdapter.class) {
                current = inlineInterop;
                if (current == null) inlineInterop = current = new InlineInterop(mapping, composer);
            }
        }
        return current;
    }

    private static Interop interopFor(SpotifyMapping mapping, Object composer) throws ReflectiveOperationException {
        Interop current = interop;
        if (current == null) {
            synchronized (ComposeAdapter.class) {
                current = interop;
                if (current == null) interop = current = new Interop(mapping, composer);
            }
        }
        return current;
    }

    /** Returns null only for an unrelated branch of an R8-merged provider, or on a mapping error. */
    public static Object cardAvailability(SpotifyMapping mapping, Object provider, Object track) {
        try {
            // An R8-merged provider carries several request branches; only the lyrics branch answers.
            if (mapping.availability.branch != null
                    && provider.getClass().getField(mapping.availability.branch).getInt(provider) != 0) return null;
            Availability current = availabilityFor(mapping, track);
            Object card = createSongCard(mapping, track);
            return card == null ? current.empty : current.maybeJust.newInstance(card);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.e(TAG, "Could not supply ivLyrics card availability", error);
            return null;
        }
    }

    /** Creates a song card without consulting Spotify's lyrics or server-selected card list. */
    static Object createSongCard(SpotifyMapping mapping, Object track) throws ReflectiveOperationException {
        Availability current = availabilityFor(mapping, track);
        String uri = (String) current.uri.invoke(track);
        @SuppressWarnings("unchecked") Map<String, String> metadata = (Map<String, String>) current.metadata.invoke(track);
        // Keep non-song content outside the lyrics card; ivLyrics resolves song lyrics independently.
        if (uri == null || !uri.startsWith("spotify:track:") || !value(metadata, "parent_episode_uri").isEmpty()) {
            return null;
        }
        Object model = current.model.newInstance(current.emptyLyrics, uri, "absent",
                value(metadata, "title"), value(metadata, "artist_name"), value(metadata, "image_url"));
        return current.card.newInstance(model);
    }

    private static Availability availabilityFor(SpotifyMapping mapping, Object track) throws ReflectiveOperationException {
        Availability current = availability;
        if (current == null) {
            synchronized (ComposeAdapter.class) {
                current = availability;
                if (current == null) availability = current = new Availability(mapping, track.getClass().getClassLoader());
            }
        }
        return current;
    }

    private static String value(Map<String, String> metadata, String key) {
        String result = metadata.get(key);
        return result == null ? "" : result;
    }

    private static final class Interop implements InvocationHandler {
        final Method render;
        final Object factory;
        final Object squareModifier;

        Interop(SpotifyMapping mapping, Object composer) throws ReflectiveOperationException {
            ClassLoader loader = composer.getClass().getClassLoader();
            SpotifyMapping.Compose compose = mapping.compose;
            Class<?> function = Class.forName(compose.function, false, loader);
            Class<?> modifier = Class.forName(compose.modifier, false, loader);
            Class<?> owner = Class.forName(compose.renderOwner, false, loader);
            // The parameter is declared as the Composer type in the table, which R8 may leave as the
            // interface while the running instance is its implementation; never ask for the runtime class.
            Class<?> composerType = Class.forName(mapping.card.composer, false, loader);
            render = owner.getDeclaredMethod(compose.renderer, function, modifier, function,
                    composerType, int.class, int.class);
            render.setAccessible(true);
            Constructor<?> aspectRatio = Class.forName(compose.aspect, false, loader)
                    .getDeclaredConstructor(float.class, boolean.class);
            aspectRatio.setAccessible(true);
            squareModifier = aspectRatio.newInstance(1f, false);
            if (!modifier.isInstance(squareModifier)) throw new IllegalStateException("Invalid aspect ratio modifier");
            factory = Proxy.newProxyInstance(loader, new Class<?>[]{function}, this);
            Log.i(TAG, "Mounted native Compose interop " + owner.getName());
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("invoke")) return IvLyricsBridge.createPreview((Context) args[0]);
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "ivLyrics native card factory";
            throw new UnsupportedOperationException(method.toString());
        }
    }

    /** Optional inline mappings cannot prevent the existing card interop from initializing. */
    private static final class InlineInterop implements InvocationHandler {
        final Method render;
        final Object factory;
        final Object modifier;

        InlineInterop(SpotifyMapping mapping, Object composer) throws ReflectiveOperationException {
            ClassLoader loader = composer.getClass().getClassLoader();
            SpotifyMapping.Inline inline = mapping.inline;
            Class<?> function = Class.forName(mapping.compose.function, false, loader);
            Class<?> modifierType = Class.forName(mapping.compose.modifier, false, loader);
            Class<?> owner = Class.forName(mapping.compose.renderOwner, false, loader);
            Class<?> composerType = Class.forName(inline.composer, false, loader);
            render = owner.getDeclaredMethod(mapping.compose.renderer, function, modifierType, function,
                    composerType, int.class, int.class);
            render.setAccessible(true);
            Object empty = Class.forName(inline.emptyModifier, true, loader).getField("a").get(null);
            // A build may only map the slot; an unmodified width still lets the preview measure itself.
            Object sized = inline.fillMaxWidth == null ? empty
                    : Class.forName(inline.fillMaxWidth, false, loader)
                            .getMethod("f", float.class, modifierType).invoke(null, 1f, empty);
            Object padded = inline.padding == null ? sized
                    : Class.forName(inline.padding, false, loader)
                            .getMethod("y", float.class, float.class, int.class, modifierType)
                            .invoke(null, 0f, 0f, 2, sized);
            modifier = padded;
            if (!modifierType.isInstance(modifier)) throw new IllegalStateException("Invalid inline modifier");
            factory = Proxy.newProxyInstance(loader, new Class<?>[]{function}, this);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getName().equals("invoke")) return SpotifyInlineLyrics.createView((Context) args[0]);
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "ivLyrics inline lyrics factory";
            throw new UnsupportedOperationException(method.toString());
        }
    }

    private static final class Availability {
        final Method uri;
        final Method metadata;
        final Object emptyLyrics;
        final Constructor<?> model;
        final Constructor<?> card;
        final Constructor<?> maybeJust;
        final Object empty;

        Availability(SpotifyMapping mapping, ClassLoader loader) throws ReflectiveOperationException {
            SpotifyMapping.Availability availability = mapping.availability;
            Class<?> track = Class.forName(SpotifyMapping.CONTEXT_TRACK, false, loader);
            uri = track.getMethod("uri");
            metadata = track.getMethod("metadata");
            Class<?> state = Class.forName(availability.state, true, loader);
            Object seed = state.getField(availability.seed).get(null);
            emptyLyrics = state.getField("a").get(seed);
            Class<?> modelType = Class.forName(availability.model, false, loader);
            model = modelType.getConstructor(emptyLyrics.getClass(), String.class, String.class, String.class, String.class, String.class);
            card = Class.forName(availability.card, false, loader).getConstructor(modelType);
            maybeJust = Class.forName("io.reactivex.rxjava3.internal.operators.maybe.MaybeJust", false, loader).getConstructor(Object.class);
            Class<?> emptyType = Class.forName("io.reactivex.rxjava3.internal.operators.maybe.MaybeEmpty", true, loader);
            Object emptyValue = null;
            for (Field field : emptyType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == emptyType) {
                    field.setAccessible(true);
                    emptyValue = field.get(null);
                    break;
                }
            }
            if (emptyValue == null) throw new NoSuchFieldException("MaybeEmpty singleton");
            empty = emptyValue;
            Log.i(TAG, "Native lyrics availability replaced with ivLyrics song card");
        }
    }
}
