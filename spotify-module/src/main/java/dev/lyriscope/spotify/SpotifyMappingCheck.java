package dev.lyriscope.spotify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resolves every anchor in a {@link SpotifyMapping} without hooking anything.
 *
 * <p>Its only job is to answer "which part of this build's mapping no longer matches" in one log
 * line. After a Spotify update the build either maps and installs, or names the anchors that
 * moved; it never leaves a half-installed hook set behind a single class name.
 */
final class SpotifyMappingCheck {

    private interface Probe {
        void run() throws ReflectiveOperationException;
    }

    private final ClassLoader loader;
    private final List<String> missing = new ArrayList<>();

    private SpotifyMappingCheck(ClassLoader loader) {
        this.loader = loader;
    }

    /** Labels of the anchors this mapping cannot resolve, in table order; empty when complete. */
    static List<String> problems(SpotifyMapping mapping, ClassLoader loader) {
        SpotifyMappingCheck check = new SpotifyMappingCheck(loader);
        check.card(mapping.card);
        check.compose(mapping.compose, mapping.card.composer);
        check.availability(mapping.availability);
        check.presence(mapping.presence);
        check.unit(mapping.unit);
        if (mapping.metadata != null) check.metadata(mapping.metadata);
        if (mapping.inline != null) check.inline(mapping.inline, mapping.compose);
        if (mapping.order != null) check.order(mapping.order);
        if (mapping.video != null) check.video(mapping.video);
        if (mapping.karaoke != null) check.karaoke(mapping.karaoke);
        return check.missing;
    }

    private void at(String label, Probe probe) {
        try {
            probe.run();
        } catch (ReflectiveOperationException | RuntimeException error) {
            missing.add(label);
        }
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private void card(SpotifyMapping.Card card) {
        at("card.owner", () -> {
            Class<?> owner = type(card.owner);
            Class<?> composer = type(card.composer);
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (method.getName().equals(card.renderer) && Modifier.isStatic(method.getModifiers())
                        && method.getReturnType() == void.class && types.length == 10
                        && types[8] == composer && types[9] == int.class) return;
            }
            throw new NoSuchMethodException("card renderer");
        });
    }

    private void compose(SpotifyMapping.Compose compose, String composer) {
        at("compose.interop", () -> {
            Class<?> function = type(compose.function);
            Class<?> modifier = type(compose.modifier);
            Class<?> renderer = type(compose.renderOwner);
            // The factory is mounted through a dynamic proxy, so the lambda type must be an interface.
            if (!function.isInterface()) throw new NoSuchMethodException("compose function");
            renderer.getDeclaredMethod(compose.renderer, function, modifier, function,
                    type(composer), int.class, int.class);
        });
        at("compose.aspect", () -> type(compose.aspect).getDeclaredConstructor(float.class, boolean.class));
    }

    private void availability(SpotifyMapping.Availability availability) {
        at("availability.provider", () -> {
            Class<?> owner = type(availability.owner);
            Class<?> track = type(SpotifyMapping.CONTEXT_TRACK);
            Method method = availability.branch == null
                    ? owner.getDeclaredMethod(availability.method, track, String.class)
                    : owner.getDeclaredMethod(availability.method, track);
            if (!method.getReturnType().getName().equals("io.reactivex.rxjava3.core.Maybe")) {
                throw new NoSuchMethodException("availability return type");
            }
        });
        at("availability.model", () -> {
            Class<?> state = type(availability.state);
            Object seed = state.getField(availability.seed).get(null);
            Object emptyLyrics = state.getField("a").get(seed);
            type(availability.model).getConstructor(emptyLyrics.getClass(), String.class, String.class,
                    String.class, String.class, String.class);
        });
        at("availability.card", () -> type(availability.card)
                .getConstructor(type(availability.model)));
    }

    private void presence(SpotifyMapping.Presence presence) {
        at("presence.sections", () -> {
            Class<?> section = type(presence.section);
            Class<?> lyrics = type(presence.lyrics);
            if (!section.isAssignableFrom(lyrics)) throw new NoSuchMethodException("section shape");
        });
        at("presence.event", () -> {
            Class<?> track = type(SpotifyMapping.CONTEXT_TRACK);
            Class<?> event = type(presence.event);
            if (presence.eventArity == 3) {
                event.getDeclaredConstructor(track, String.class, List.class);
            } else {
                event.getDeclaredConstructor(track, String.class, List.class, List.class,
                        type(presence.eventExtra));
            }
        });
    }

    private void unit(String unit) {
        if (unit == null) return;
        at("kotlin.unit", () -> type(unit).getField("a").get(null));
    }

    private void metadata(SpotifyMapping.Metadata metadata) {
        at("metadata.client", () -> type(metadata.client).getMethod(metadata.requestMethod, type(metadata.request)));
        at("metadata.request", () -> type(metadata.request)
                .getConstructor(String.class, boolean.class, type("kotlin.jvm.functions.Function1")));
        at("metadata.track", () -> type(metadata.track));
    }

    private void inline(SpotifyMapping.Inline inline, SpotifyMapping.Compose compose) {
        at("inline.renderer", () -> {
            Method render = type(inline.renderer).getDeclaredMethod(inline.rendererMethod, Object.class,
                    Object.class, Object.class, Object.class, Object.class);
            if (render.getParameterCount() != 5) throw new NoSuchMethodException("inline renderer");
            if (inline.rendererBranch != null
                    && type(inline.renderer).getField(inline.rendererBranch).getType() != int.class) {
                throw new NoSuchFieldException("inline branch");
            }
            type(inline.model);
            type(inline.composer);
        });
        // Gates and the native toggle are optional: a build may map the slot before re-anchoring them.
        if (inline.visibility != null) at("inline.visibility", () ->
                type(inline.visibility).getDeclaredMethod("invokeSuspend", Object.class));
        if (inline.availability != null) at("inline.availability", () ->
                type(inline.availability).getDeclaredMethod("invokeSuspend", Object.class));
        if (inline.menu != null) at("inline.menu", () -> {
            type(inline.menu).getDeclaredMethod("apply", Object.class);
            type(inline.trackGate).getDeclaredMethod(inline.trackGateMethod, type(SpotifyMapping.CONTEXT_TRACK));
        });
        at("inline.modifier", () -> {
            Class<?> modifier = type(compose.modifier);
            type(inline.emptyModifier).getField("a").get(null);
            if (inline.fillMaxWidth != null) type(inline.fillMaxWidth).getMethod("f", float.class, modifier);
            if (inline.padding != null) {
                type(inline.padding).getMethod("y", float.class, float.class, int.class, modifier);
            }
        });
    }

    private void order(SpotifyMapping.Order order) {
        at("order.filter", () -> {
            Class<?> state = type(order.state);
            Method filter = type(order.owner).getDeclaredMethod(order.filter, state, Set.class);
            if (!Modifier.isStatic(filter.getModifiers()) || filter.getReturnType() != ArrayList.class
                    || !state.isInterface()) {
                throw new NoSuchMethodException("order filter");
            }
            type(order.section);
            Class<?> lyrics = type(order.lyrics);
            if (!type(order.section).isAssignableFrom(lyrics)) throw new NoSuchMethodException("order shape");
        });
    }

    private void video(SpotifyMapping.Video video) {
        at("video.renderer", () -> {
            type(video.renderer).getDeclaredMethod(video.update, Object.class, type(video.updateContext));
            type(video.renderer).getDeclaredMethod(video.view);
            type(video.button);
        });
    }

    private void karaoke(SpotifyMapping.Karaoke karaoke) {
        at("karaoke.settings", () -> {
            Field offline = type(karaoke.settings).getDeclaredField("a");
            if (offline.getType() != boolean.class) throw new NoSuchFieldException("offline state");
        });
        at("karaoke.optional", () -> {
            type(karaoke.optional).getMethod("c");
            type(karaoke.optional).getMethod("b");
        });
        at("karaoke.shapes", () -> {
            type(karaoke.response);
            type(karaoke.lyricsShape);
            type(karaoke.supportShape);
        });
        at("karaoke.repository", () -> {
            type(karaoke.mapper).getField("c");
            type(karaoke.repository).getField("d");
        });
        at("karaoke.player", () -> {
            type(karaoke.pool).getMethod("c");
            type(karaoke.stateFlow).getMethod("e");
        });
        at("karaoke.services", () -> {
            if (karaoke.services.isEmpty()) throw new NoSuchMethodException("no service types");
            for (String service : karaoke.services) type(service);
        });
    }
}
