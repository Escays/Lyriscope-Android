package dev.lyriscope.spotify;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The single table describing one Spotify build's obfuscated surface.
 *
 * <p>R8 renames every {@code p.*} class on each release, so nothing in the hooks may name one
 * directly: a hook asks this table for the class or method it needs. Adding a build means adding
 * one constant below and registering it, never threading a version flag through the hooks.
 *
 * <p>A feature group is {@code null} when that build has no such feature (9.1.42 has no inline
 * lyrics, card ordering, video switch or native karaoke). Consumers skip a null group instead of
 * testing a version boolean. Behaviour contracts live in INLINE_MAPPING.md; this file only says
 * where each contract lives inside a given build.
 */
public final class SpotifyMapping {

    /** Android and Spotify framework names; identical on every build and therefore not mapped. */
    public static final String CONTEXT_TRACK = "com.spotify.player.model.ContextTrack";
    public static final String METADATA_TRACK = "com.spotify.metadata.proto.Metadata$Track";
    public static final String METADATA_BATCH = "com.spotify.metadata.cosmos.proto.MetadataCosmos$MultiResponse";
    public static final String FULLSCREEN = "com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity";
    public static final String PLAYBACK_QUALITY = "com.spotify.player.model.PlaybackQuality";
    public static final String PLAYER_STATE = "com.spotify.player.model.AutoValue_PlayerState";

    /** The build every mapping in this table was recorded against. */
    public final String version;

    /** Kotlin's Unit instance used as the neutral result of composed slots. */
    public final String unit;
    public final Card card;
    public final Compose compose;
    public final Availability availability;
    public final Presence presence;
    public final Metadata metadata;
    public final Inline inline;
    public final Order order;
    public final Video video;
    public final Karaoke karaoke;

    private SpotifyMapping(String version, String unit, Card card, Compose compose, Availability availability,
                           Presence presence, Metadata metadata, Inline inline, Order order,
                           Video video, Karaoke karaoke) {
        this.version = version;
        this.unit = unit;
        this.card = card;
        this.compose = compose;
        this.availability = availability;
        this.presence = presence;
        this.metadata = metadata;
        this.inline = inline;
        this.order = order;
        this.video = video;
        this.karaoke = karaoke;
    }

    /** Static renderer that draws the Now Playing lyrics card through a Compose composer. */
    public static final class Card {
        /** Class owning the static card renderer. */
        public final String owner;
        /** Name of that static renderer. */
        public final String renderer;
        /** Composer type the renderer receives as its ninth argument. */
        public final String composer;

        Card(String owner, String renderer, String composer) {
            this.owner = owner;
            this.renderer = renderer;
            this.composer = composer;
        }
    }

    /** Host Compose entry points used to mount an AndroidView slot without bundling Compose. */
    public static final class Compose {
        public final String function;
        public final String modifier;
        public final String renderOwner;
        /** Name of the AndroidView composable on {@link #renderOwner}. */
        public final String renderer;
        /** Aspect-ratio modifier factory that sizes the card slot. */
        public final String aspect;

        Compose(String function, String modifier, String renderOwner, String renderer, String aspect) {
            this.function = function;
            this.modifier = modifier;
            this.renderOwner = renderOwner;
            this.renderer = renderer;
            this.aspect = aspect;
        }
    }

    /** Where the card's lyric model, availability `Maybe` and the merged provider branch live. */
    public static final class Availability {
        public final String owner;
        /** Method returning {@code Maybe<lyrics section>} for a {@code ContextTrack}. */
        public final String method;
        /** R8-merged branch ordinal guarding this provider, or null when the class is unmerged. */
        public final String branch;
        /** Class holding the empty lyrics model as a static field. */
        public final String state;
        /** Static seed field of {@link #state} that owns the empty lyrics model. */
        public final String seed;
        /** Lyrics card model constructor argument type. */
        public final String model;
        /** Card type wrapping that model. */
        public final String card;

        Availability(String owner, String method, String branch, String state, String seed,
                     String model, String card) {
            this.owner = owner;
            this.method = method;
            this.branch = branch;
            this.state = state;
            this.seed = seed;
            this.model = model;
            this.card = card;
        }
    }

    /** Event that publishes the Now Playing scroll sections; injected when the server omits lyrics. */
    public static final class Presence {
        public final String section;
        public final String lyrics;
        public final String event;
        /** Parameter count of the event constructor: (track, id, sections[, extras…]). */
        public final int eventArity;
        /** Fifth constructor argument for builds that carry a section category, else null. */
        public final String eventExtra;

        Presence(String section, String lyrics, String event, int eventArity, String eventExtra) {
            this.section = section;
            this.lyrics = lyrics;
            this.event = event;
            this.eventArity = eventArity;
            this.eventExtra = eventExtra;
        }
    }

    /** Native TrackV4 metadata client used to resolve a public ISRC before lyrics providers run. */
    public static final class Metadata {
        public final String client;
        public final String request;
        public final String track;
        /** Method on {@link #client} taking a {@link #request} and returning an Observable. */
        public final String requestMethod;

        Metadata(String client, String request, String track, String requestMethod) {
            this.client = client;
            this.request = request;
            this.track = track;
            this.requestMethod = requestMethod;
        }
    }

    /** Now Playing inline lyric line, its preference gate and the native menu toggle. */
    public static final class Inline {
        public final String renderer;
        /**
         * Case field guarding the renderer inside an R8-merged lambda holder, or null when the
         * build keeps the inline slot as a method of its own. A merged holder calls one method
         * per lambda, which already identifies the slot.
         */
        public final String rendererBranch;
        /** Preference/canvas gate lambda, or null when this build's gates are not mapped yet. */
        public final String visibility;
        /** Player-restriction gate lambda, or null when this build's gates are not mapped yet. */
        public final String availability;
        public final String model;
        public final String composer;
        /** Native Lyrics On/Off menu owner, or null when the toggle is not mapped yet. */
        public final String menu;
        public final String trackGate;
        /** Renamed renderer and track-gate methods; Kotlin interface overrides keep their names. */
        public final String rendererMethod;
        public final String trackGateMethod;
        /** Compose modifier factories: Empty, fillMaxWidth and the padding combination. */
        public final String emptyModifier;
        public final String fillMaxWidth;
        public final String padding;

        Inline(String renderer, String rendererBranch, String visibility, String availability,
               String model, String composer, String menu, String trackGate,
               String rendererMethod, String trackGateMethod,
               String emptyModifier, String fillMaxWidth, String padding) {
            this.renderer = renderer;
            this.rendererBranch = rendererBranch;
            this.visibility = visibility;
            this.availability = availability;
            this.model = model;
            this.composer = composer;
            this.menu = menu;
            this.trackGate = trackGate;
            this.rendererMethod = rendererMethod;
            this.trackGateMethod = trackGateMethod;
            this.emptyModifier = emptyModifier;
            this.fillMaxWidth = fillMaxWidth;
            this.padding = padding;
        }
    }

    /** Section-list filter that decides the order of the Now Playing cards. */
    public static final class Order {
        public final String owner;
        public final String state;
        public final String section;
        public final String lyrics;
        /** Static filter method taking the state and a Set, returning an ArrayList. */
        public final String filter;

        Order(String owner, String state, String section, String lyrics, String filter) {
            this.owner = owner;
            this.state = state;
            this.section = section;
            this.lyrics = lyrics;
            this.filter = filter;
        }
    }

    /** Audio/video switch in the Now Playing toolbar. */
    public static final class Video {
        public final String renderer;
        public final String button;
        public final String updateContext;
        /** Renderer's bind method and the accessor the toolbar calls for its view. */
        public final String update;
        public final String view;

        Video(String renderer, String button, String updateContext, String update, String view) {
            this.renderer = renderer;
            this.button = button;
            this.updateContext = updateContext;
            this.update = update;
            this.view = view;
        }
    }

    /** Native SingAlong support lookup and the playlist route that decides low-quality playback. */
    public static final class Karaoke {
        public final String settings;
        public final String optional;
        public final String response;
        public final String lyricsShape;
        public final String supportShape;
        public final String mapper;
        public final String repository;
        public final String pool;
        public final String stateFlow;
        /** Concrete metadata services accepted as a support repository. */
        public final List<String> services;

        Karaoke(String settings, String optional, String response, String lyricsShape, String supportShape,
                String mapper, String repository, String pool, String stateFlow, String... services) {
            this.settings = settings;
            this.optional = optional;
            this.response = response;
            this.lyricsShape = lyricsShape;
            this.supportShape = supportShape;
            this.mapper = mapper;
            this.repository = repository;
            this.pool = pool;
            this.stateFlow = stateFlow;
            this.services = Collections.unmodifiableList(Arrays.asList(services));
        }
    }

    private static final SpotifyMapping V9_1_80 = new SpotifyMapping(
            "9.1.80.2221",
            "p.x181",
            new Card("p.g5a0", "a", "p.ryz"),
            new Compose("kotlin.jvm.functions.Function1", "p.wmg0", "p.jy81", "b", "p.p16"),
            new Availability("p.yca0", "d", null, "p.f5a0", "j", "p.s4a0", "p.iui0"),
            new Presence("p.dvi0", "p.iui0", "p.mdw", 5, "p.mk61"),
            new Metadata("p.w1x", "p.yh", "p.fs61", "b"),
            new Inline("p.nba0", "a", "p.cp5", "p.jm7", "p.lba0", "p.ryz", "p.b9p0", "p.d2w0",
                    "c1", "e", "p.tmg0", "p.bo01", "p.tld1"),
            new Order("p.ivi0", "p.gx21", "p.dvi0", "p.iui0", "c"),
            new Video("p.dh", "p.ay6", "p.o9t", "a", "getView"),
            new Karaoke("p.xky0", "p.pgk0", "p.eve", "p.q4a0", "p.p4a0", "p.yh", "p.eda0",
                    "p.fzi0", "p.o131", "p.gea0", "p.vja0"));

    private static final SpotifyMapping V9_1_42 = new SpotifyMapping(
            "9.1.42.2058",
            null,
            new Card("p.zt01", "b", "p.gge"),
            new Compose("p.heu", "androidx.compose.ui.Modifier", "p.f34", "b", "p.xx4"),
            new Availability("p.pf30", "a", "a", "p.ea30", "i", "p.q930", "p.uba0"),
            new Presence("p.kca0", "p.uba0", "p.skr", 3, null),
            null, null,
            null,
            null,
            null);

    /**
     * 9.2 rewrote the Now Playing surface: R8 devirtualised the Compose lambda parameter into its
     * own single-method interface ({@code p.x340}) instead of reusing {@code kotlin.jvm.functions.Function1},
     * and the card renderer was merged into a 40-method class. The card path, its availability
     * provider, the section contract and the card order filter are mapped. The inline line is
     * mapped as a slot only: its renderer moved into an R8-merged lambda holder ({@code p.kpc0},
     * one method per lambda) so it needs no case field, while its two visibility gates and the
     * native Lyrics On/Off toggle are not re-anchored yet and stay null. The slot modifier must not
     * impose a height — {@code p.nyl.a} is a cached modifier that fills the width and lets the
     * preview measure its own two rows; the lyric-row modifiers cached elsewhere are fixed at 24dp
     * and clip the translation. Video switch and native karaoke are also still null. Consumers
     * skip every null they meet.
     */
    private static final SpotifyMapping V9_2_0 = new SpotifyMapping(
            "9.2.0.2053",
            "p.l5i1",
            new Card("p.pwk1", "c", "p.y4k"),
            new Compose("p.x340", "p.ozm0", "p.lo5", "a", "p.n67"),
            new Availability("p.t0g0", "d", null, "p.lsf0", "j", "p.xrf0", "p.wep0"),
            new Presence("p.ofp0", "p.wep0", "p.ne00", 5, "p.hgg1"),
            null,
            new Inline("p.kpc0", null, null, null, "p.yyf0", "p.y4k", null, null,
                    "g", null, "p.nyl", null, null),
            new Order("p.sfp0", "p.m8c1", "p.ofp0", "p.wep0", "c"),
            null,
            null);

    private static final List<SpotifyMapping> BUILDS = Collections.unmodifiableList(
            Arrays.asList(V9_2_0, V9_1_80, V9_1_42));

    /** The mapped build for an exact {@code versionName}, or null when this release is unmapped. */
    public static SpotifyMapping of(String version) {
        if (version == null) return null;
        for (SpotifyMapping build : BUILDS) {
            if (build.version.equals(version)) return build;
        }
        return null;
    }

    /** Version names this module currently maps, for logs and user-facing messages. */
    public static List<String> versions() {
        String[] names = new String[BUILDS.size()];
        for (int index = 0; index < names.length; index++) names[index] = BUILDS.get(index).version;
        return Collections.unmodifiableList(Arrays.asList(names));
    }
}
