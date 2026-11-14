package dev.lyriscope.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LyricsProviderSelectionPlan {
    /** The selection key a reader's own catalogue pick and a manual search both carry. */
    static final String MANUAL_SELECTION = "manual";

    private static final String[] TYPES = new String[]{
            LyricsProviderSettings.TYPE_KARAOKE,
            LyricsProviderSettings.TYPE_SYNCED,
            LyricsProviderSettings.TYPE_PLAIN
    };

    final List<LyricsProviderSettings.ProviderConfig> providers;
    final List<Attempt> attempts;

    private LyricsProviderSelectionPlan(
            List<LyricsProviderSettings.ProviderConfig> providers,
            List<Attempt> attempts
    ) {
        this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
        this.attempts = Collections.unmodifiableList(new ArrayList<>(attempts));
    }

    static LyricsProviderSelectionPlan create(
            LyricsProviderSettings.Snapshot settings,
            Set<String> availableSyncDataProviders
    ) {
        if (settings == null) {
            return new LyricsProviderSelectionPlan(Collections.emptyList(), Collections.emptyList());
        }
        Set<String> syncProviders = availableSyncDataProviders == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(availableSyncDataProviders);
        List<LyricsProviderSettings.ProviderConfig> providers = preferredProviders(settings, syncProviders);
        List<Attempt> attempts = new ArrayList<>();
        // Generating lrc.red word timing first needs every source checked for karaoke lyrics.
        if (settings.typeFirst || settings.automaticWordSyncFallback()) {
            for (String type : TYPES) {
                for (LyricsProviderSettings.ProviderConfig config : providers) {
                    if (canParticipate(config, type, syncProviders)) {
                        attempts.add(new Attempt(config, type));
                    }
                }
            }
        } else {
            for (LyricsProviderSettings.ProviderConfig config : providers) {
                for (String type : TYPES) {
                    if (canParticipate(config, type, syncProviders)) {
                        attempts.add(new Attempt(config, type));
                    }
                }
            }
        }
        return new LyricsProviderSelectionPlan(providers, attempts);
    }

    /**
     * The richer lyric type this result's own source could have returned but the reader switched off.
     * A source shown without a timeline while its "synced" chip is off is not a catalogue gap: the
     * status line has to say so and offer the switch, or the reader searches elsewhere for nothing.
     * A per-track pick and a manual search both bypass the type switches, so neither is flagged.
     */
    static String blockedRicherType(
            LyricsResult result,
            LyricsProviderSettings.Snapshot settings
    ) {
        if (result == null || result.lines.isEmpty() || result.karaoke || settings == null) {
            return "";
        }
        if (MANUAL_SELECTION.equals(result.selectionPolicyKey)) {
            return "";
        }
        LyricsProviderSettings.ProviderConfig config = settings.config(result.providerId);
        if (config == null || !config.enabled) {
            return "";
        }
        if (LyricsProviderSettings.PROVIDER_LRC_RED.equals(config.provider.id)) {
            // lrc.red generates word timing on demand, so its karaoke chip never withholds a timeline.
            return "";
        }
        if (!hasTimedLines(result.lines)) {
            if (config.provider.synced && !config.synced) return LyricsProviderSettings.TYPE_SYNCED;
            if (config.provider.nativeKaraoke && !config.karaoke) return LyricsProviderSettings.TYPE_KARAOKE;
            return "";
        }
        return config.provider.nativeKaraoke && !config.karaoke ? LyricsProviderSettings.TYPE_KARAOKE : "";
    }

    private static boolean hasTimedLines(List<LyricsLine> lines) {
        for (LyricsLine line : lines) {
            if (line != null && line.isTimed()) return true;
        }
        return false;
    }

    static boolean canApplyIvLyricsSyncToCachedResult(
            LyricsResult result,
            LyricsProviderSettings.Snapshot settings
    ) {
        if (result == null || result.lines.isEmpty() || result.karaoke || settings == null) {
            return false;
        }
        LyricsProviderSettings.ProviderConfig config = settings.config(result.providerId);
        return config != null
                && config.enabled
                && config.karaoke;
    }

    static boolean shouldRecheckCachedResult(
            LyricsResult result,
            LyricsProviderSettings.Snapshot settings,
            String resolvedIsrc
    ) {
        if (result == null || result.lines.isEmpty() || settings == null
                || MANUAL_SELECTION.equals(result.selectionPolicyKey)) {
            return false;
        }
        if (canApplyIvLyricsSyncToCachedResult(result, settings)) return true;
        if (!settings.preferSyncDataProvider || TrackSnapshot.normalizeIsrc(resolvedIsrc).isEmpty()) return false;
        // Native ISRC can arrive after a karaoke provider has already populated the cache.
        // Check OpenDB once for that new identity before accepting the previous provider.
        if (!TrackSnapshot.normalizeIsrc(resolvedIsrc).equals(result.isrc)) return true;
        LyricsProviderSettings.ProviderConfig current = settings.config(result.providerId);
        return current == null || !result.karaoke;
    }

    static String preferredIvLyricsSyncProviderId(
            LyricsProviderSettings.Snapshot settings,
            Set<String> availableSyncDataProviders
    ) {
        if (settings == null || availableSyncDataProviders == null || availableSyncDataProviders.isEmpty()) {
            return "";
        }
        List<LyricsProviderSettings.ProviderConfig> preferred = syncDataProvidersInPriorityOrder(
                settings.enabledProviders(),
                availableSyncDataProviders
        );
        return preferred.isEmpty() ? "" : preferred.get(0).provider.id;
    }

    private static List<LyricsProviderSettings.ProviderConfig> preferredProviders(
            LyricsProviderSettings.Snapshot settings,
            Set<String> syncProviders
    ) {
        List<LyricsProviderSettings.ProviderConfig> providers = new ArrayList<>(settings.enabledProviders());
        if (!settings.preferSyncDataProvider || syncProviders.isEmpty()) {
            return providers;
        }
        List<LyricsProviderSettings.ProviderConfig> preferred = syncDataProvidersInPriorityOrder(
                providers,
                syncProviders
        );
        Set<String> preferredIds = new LinkedHashSet<>();
        for (LyricsProviderSettings.ProviderConfig config : preferred) {
            preferredIds.add(config.provider.id);
        }
        List<LyricsProviderSettings.ProviderConfig> remaining = new ArrayList<>();
        for (LyricsProviderSettings.ProviderConfig config : providers) {
            if (!preferredIds.contains(config.provider.id)) {
                remaining.add(config);
            }
        }
        preferred.addAll(remaining);
        return preferred;
    }

    private static List<LyricsProviderSettings.ProviderConfig> syncDataProvidersInPriorityOrder(
            List<LyricsProviderSettings.ProviderConfig> providers,
            Set<String> syncProviders
    ) {
        List<LyricsProviderSettings.ProviderConfig> lrclib = new ArrayList<>();
        List<LyricsProviderSettings.ProviderConfig> others = new ArrayList<>();
        for (LyricsProviderSettings.ProviderConfig config : providers) {
            if (!config.karaoke || !syncProviders.contains(config.provider.id)) {
                continue;
            }
            if (LyricsProviderSettings.PROVIDER_LRCLIB.equals(config.provider.id)) {
                lrclib.add(config);
            } else {
                others.add(config);
            }
        }
        lrclib.addAll(others);
        return lrclib;
    }

    private static boolean canParticipate(
            LyricsProviderSettings.ProviderConfig config,
            String type,
            Set<String> syncProviders
    ) {
        if (config == null || !config.enabled || !config.allows(type)) {
            return false;
        }
        if (LyricsProviderSettings.TYPE_KARAOKE.equals(type)) {
            return config.provider.nativeKaraoke
                    || syncProviders.contains(config.provider.id);
        }
        if (LyricsProviderSettings.TYPE_SYNCED.equals(type)) {
            return config.provider.synced;
        }
        return config.provider.plain;
    }

    static final class Attempt {
        final LyricsProviderSettings.ProviderConfig config;
        final String type;

        Attempt(LyricsProviderSettings.ProviderConfig config, String type) {
            this.config = config;
            this.type = type;
        }

        String key() {
            return config.provider.id + ":" + type;
        }
    }
}
