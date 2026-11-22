package dev.lyriscope.spotify;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.lyriscope.core.IvLyricsBridge;
import dev.lyriscope.core.BaseLyricsActivity;
import dev.lyriscope.core.SpotifyMetadataBridge;

/** Native Spotify UI hooks for classic Xposed loaders, including LSPosed and LSPatch. */
public final class SpotifyXposedHooks implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TAG = "Lyriscope";
    private static final String HOST = "com.spotify.music";
    private static volatile boolean installed;
    private static String modulePath;

    @Override public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
        ContextBridge.initialize(modulePath);
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam packageParam) {
        if (!HOST.equals(packageParam.packageName) || !HOST.equals(packageParam.processName)) return;
        final ClassLoader loader = packageParam.classLoader;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Context context = (Context) param.args[0];
                if (!HOST.equals(context.getPackageName()) || installed) return;
                synchronized (SpotifyXposedHooks.class) {
                    if (installed) return;
                    try {
                        String version = context.getPackageManager().getPackageInfo(HOST, 0).versionName;
                        SpotifyMapping mapping = SpotifyMapping.of(version);
                        if (mapping == null) {
                            Log.e(TAG, "Unsupported Spotify " + version
                                    + "; mapped builds " + SpotifyMapping.versions());
                            IvLyricsBridge.setHookHealth(new IvLyricsBridge.HookHealth(
                                    version, false, false, false, false, null));
                            return;
                        }
                        ContextBridge.initialize(modulePath);
                        List<String> missing = SpotifyMappingCheck.problems(mapping, loader);
                        if (!missing.isEmpty()) {
                            Log.w(TAG, "Mapping gaps for " + version + ": " + missing);
                        }
                        SpotifyMetadataBridge.configure(mapping);
                        SpotifyKaraokeTransport.install(loader);
                        SpotifyKaraokePlayback.install(loader, mapping.karaoke);
                        SpotifyLyricsHost.install();
                        SpotifyWebViewSupport.install();
                        IvLyricsBridge.initialize(context);
                        installHooks(loader, mapping);
                        IvLyricsBridge.setHookHealth(new IvLyricsBridge.HookHealth(
                                version, true, mapping.inline != null, mapping.karaoke != null,
                                mapping.metadata != null, missing));
                        installed = true;
                        Log.i(TAG, "Native lyrics hooks installed for Spotify " + version);
                    } catch (Throwable error) {
                        failure("INSTALL", error);
                    }
                }
            }
        });
        Log.i(TAG, "Module loaded for Spotify main process");
    }

    private static void installHooks(ClassLoader loader, SpotifyMapping mapping) throws ReflectiveOperationException {
        List<XC_MethodHook.Unhook> installedHooks = new ArrayList<>();
        boolean committed = false;
        try {
        Class<?> cardOwner = Class.forName(mapping.card.owner, false, loader);
        Class<?> composer = Class.forName(mapping.card.composer, false, loader);
        Method cardRenderer = null;
        for (Method method : cardOwner.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (method.getName().equals(mapping.card.renderer) && Modifier.isStatic(method.getModifiers()) &&
                method.getReturnType() == void.class && types.length == 10 && types[8] == composer && types[9] == int.class) {
                if (cardRenderer != null) throw new NoSuchMethodException("Ambiguous native lyrics card renderer");
                cardRenderer = method;
            }
        }
        if (cardRenderer == null) throw new NoSuchMethodException("Native lyrics card renderer not found");
        installedHooks.add(XposedBridge.hookMethod(cardRenderer, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    ComposeAdapter.renderCard(mapping, param.args[8]);
                    param.setResult(null);
                } catch (Throwable error) { failure("CARD_RENDER", error); }
            }
        }));

        Class<?> trackType = Class.forName(SpotifyMapping.CONTEXT_TRACK, false, loader);
        Class<?> availabilityOwner = Class.forName(mapping.availability.owner, false, loader);
        Method availability = mapping.availability.branch == null
            ? availabilityOwner.getDeclaredMethod(mapping.availability.method, trackType, String.class)
            : availabilityOwner.getDeclaredMethod(mapping.availability.method, trackType);
        if (!availability.getReturnType().getName().equals("io.reactivex.rxjava3.core.Maybe")) {
            throw new NoSuchMethodException("Native lyrics availability return type changed");
        }
        installedHooks.add(XposedBridge.hookMethod(availability, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                SpotifyKaraokeEligibility.onCardProvider(mapping, param.thisObject, param.args[0]);
                SpotifyMetadataBridge.onContextTrack(param.args[0]);
                Object replacement = ComposeAdapter.cardAvailability(mapping, param.thisObject, param.args[0]);
                if (replacement != null) param.setResult(replacement);
            }
        }));

        // Keep the host's registered component and Android lifecycle. Only the instantiated
        // Activity implementation changes; SpotifyLyricsActivity supplies its own wrapped resources.
        installedHooks.add(XposedHelpers.findAndHookMethod(Instrumentation.class, "newActivity", ClassLoader.class,
            String.class, Intent.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!SpotifyMapping.FULLSCREEN.equals(param.args[1])) return;
                    Intent intent = (Intent) param.args[2];
                    intent.putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
                    intent.putExtra(BaseLyricsActivity.EXTRA_OPEN_LYRICS_PAGE, true);
                    param.setResult(new SpotifyLyricsActivity());
                    Log.i(TAG, "Native full lyrics component replaced with SpotifyLyricsActivity");
                }
            }));

        installedHooks.addAll(XposedBridge.hookAllConstructors(MediaSession.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                // Public MediaSession constructors begin with (Context, String tag).
                if (param.args.length < 2 || !(param.args[0] instanceof Context)
                        || !(param.args[1] instanceof String)) return;
                try { ComposeAdapter.onMediaSession((MediaSession) param.thisObject, (String) param.args[1]); }
                catch (Throwable error) { failure("MEDIA_SESSION", error); }
            }
        }));

        int parserHooks = 0;
        for (String name : new String[]{SpotifyMapping.METADATA_TRACK, "com.spotify.metadata.proto.Metadata$Track"}) {
            Class<?> type = Class.forName(name, false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == type && method.getParameterTypes().length == 1) {
                    installedHooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!param.hasThrowable()) SpotifyMetadataBridge.onTrackMetadata(param.getResult());
                        }
                    }));
                    parserHooks++;
                }
            }
        }
        if (parserHooks != 2) throw new NoSuchMethodException("Expected exactly two native track metadata parsers");
        Class<?> batch = Class.forName(SpotifyMapping.METADATA_BATCH, false, loader);
        for (Method method : batch.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == batch &&
                method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == byte[].class) {
                installedHooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!param.hasThrowable()) SpotifyMetadataBridge.onMetadataBatch(param.getResult());
                    }
                }));
            }
        }
        if (mapping.metadata != null) {
            Class<?> metadataClient = Class.forName(mapping.metadata.client, false, loader);
            installedHooks.addAll(XposedBridge.hookAllConstructors(metadataClient, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) SpotifyMetadataBridge.onMetadataClient(param.thisObject);
                }
            }));
            // Also observe existing instances if dependency construction predates Application.attach.
            installedHooks.add(XposedHelpers.findAndHookMethod(metadataClient, "b",
                Class.forName(mapping.metadata.request, false, loader), new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        SpotifyMetadataBridge.onMetadataClient(param.thisObject);
                    }
                }));
        }
        installedHooks.add(SpotifyLyricsCardPresence.install(loader, mapping));
        if (mapping.karaoke != null) {
            // The native server can omit the card entirely; capture its repository independently.
            installedHooks.addAll(XposedBridge.hookAllConstructors(availabilityOwner, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) SpotifyKaraokeEligibility.onCardProvider(mapping, param.thisObject, null);
                }
            }));
        }
        if (mapping.order != null) installedHooks.add(SpotifyLyricsOrder.install(loader, mapping));
        if (mapping.inline != null) installedHooks.addAll(SpotifyInlineLyrics.install(loader, mapping));
        if (mapping.video != null) installedHooks.addAll(SpotifyVideoButton.install(loader, mapping));
        committed = true;
        } finally {
            if (!committed) {
                for (XC_MethodHook.Unhook hook : installedHooks) hook.unhook();
            }
        }
    }

    private static void failure(String stage, Throwable error) {        // No host object, authentication response, request or exception message is logged.
        Log.e(TAG, stage + "_FAILED " + error.getClass().getSimpleName());
    }
}
