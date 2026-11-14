package dev.lyriscope.core;
import java.util.*;
/**
 * A source shown without a timeline because its own "synced" chip is off is not a catalogue gap.
 * The selection plan must skip that type, and the result must name the switch the reader can flip.
 */
public final class ProviderTypeGateRegression {
    private static LyricsProviderSettings.ProviderConfig config(
            String providerId, boolean enabled, boolean karaoke, boolean synced, boolean plain) {
        var provider = LyricsProviderSettings.provider(providerId);
        if (provider == null) throw new AssertionError("unknown provider " + providerId);
        return new LyricsProviderSettings.ProviderConfig(provider, enabled, karaoke, synced, plain);
    }

    private static LyricsProviderSettings.Snapshot snapshot(
            List<String> order, LyricsProviderSettings.ProviderConfig... configs) {
        Map<String, LyricsProviderSettings.ProviderConfig> map = new LinkedHashMap<>();
        for (var config : configs) map.put(config.provider.id, config);
        for (var provider : LyricsProviderSettings.PROVIDERS) {
            if (!map.containsKey(provider.id)) {
                map.put(provider.id, new LyricsProviderSettings.ProviderConfig(provider, false, false, true, false));
            }
        }
        return new LyricsProviderSettings.Snapshot(order, map, false, true);
    }

    private static LyricsResult plain(String providerId, String label) {
        return new LyricsResult(
                List.of(new LyricsLine(0L, 0L, "Fixture", List.of()), new LyricsLine(0L, 0L, "Line", List.of())),
                label, "", false
        ).withSelection(providerId, "type-first|auto");
    }

    private static LyricsResult timed(String providerId, String label) {
        return new LyricsResult(
                List.of(new LyricsLine(1000L, 2000L, "Fixture", List.of())), label, "", false
        ).withSelection(providerId, "type-first|auto");
    }

    private static LyricsResult karaoke(String providerId, String label) {
        return new LyricsResult(
                List.of(new LyricsLine(1000L, 2000L, "Fixture", List.of())), label, "", true
        ).withSelection(providerId, "type-first|auto");
    }

    public static void main(String[] args) {
        // The root cause: with "synced" switched off, the plan never attempts that type for LRCLIB,
        // so the plain result wins even though the catalogue had line timing.
        var lrclibSyncedOff = config("lrclib", true, false, false, true);
        var lrclibOnly = snapshot(List.of("lrclib"), lrclibSyncedOff);
        var plan = LyricsProviderSelectionPlan.create(lrclibOnly, Set.of("lrclib"));
        if (plan.attempts.stream().anyMatch(a -> LyricsProviderSettings.TYPE_SYNCED.equals(a.type)))
            throw new AssertionError("synced chip off must not be attempted");
        if (!LyricsProviderSettings.TYPE_SYNCED.equals(
                LyricsProviderSelectionPlan.blockedRicherType(plain("lrclib", "LRCLIB plain"), lrclibOnly)))
            throw new AssertionError("plain LRCLIB lyrics must name the synced switch");

        // Switched on: nothing is being held back.
        var lrclibSyncedOn = config("lrclib", true, false, true, true);
        if (!LyricsProviderSelectionPlan.blockedRicherType(
                plain("lrclib", "LRCLIB plain"), snapshot(List.of("lrclib"), lrclibSyncedOn)).isEmpty())
            throw new AssertionError("enabled synced type must not be hinted");
        if (!LyricsProviderSelectionPlan.blockedRicherType(
                timed("lrclib", "LRCLIB synced"), snapshot(List.of("lrclib"), lrclibSyncedOn)).isEmpty())
            throw new AssertionError("timed lyrics need no type hint");

        // LRCLIB has no karaoke of its own, so a timed LRCLIB set has nothing richer to offer.
        if (!LyricsProviderSelectionPlan.blockedRicherType(
                timed("lrclib", "LRCLIB synced"), lrclibOnly).isEmpty())
            throw new AssertionError("LRCLIB has no karaoke type to offer");

        // A source with word timing does have one; with both richer types off, synced is named first.
        var lyricsPlusKaraokeOff = config("lyricsplus", true, false, true, true);
        var lyricsPlusOnly = snapshot(List.of("lyricsplus"), lyricsPlusKaraokeOff);
        if (!LyricsProviderSettings.TYPE_KARAOKE.equals(
                LyricsProviderSelectionPlan.blockedRicherType(timed("lyricsplus", "LyricsPlus synced"), lyricsPlusOnly)))
            throw new AssertionError("timed lyrics without word timing must name karaoke");
        var lyricsPlusAllOff = snapshot(List.of("lyricsplus"), config("lyricsplus", true, false, false, true));
        if (!LyricsProviderSettings.TYPE_SYNCED.equals(
                LyricsProviderSelectionPlan.blockedRicherType(plain("lyricsplus", "LyricsPlus plain"), lyricsPlusAllOff)))
            throw new AssertionError("plain lyrics must name synced before karaoke");

        // lrc.red generates word timing on demand, so its karaoke chip never withholds a timeline.
        var lrcRedKaraokeOff = snapshot(List.of("lrcred"), config("lrcred", true, false, true, true));
        if (!LyricsProviderSelectionPlan.blockedRicherType(
                plain("lrcred", "lrc.red plain"), lrcRedKaraokeOff).isEmpty())
            throw new AssertionError("lrc.red generates its own word timing");

        // Nothing to offer: karaoke already shown, source disabled, or the reader picked the source.
        if (!LyricsProviderSelectionPlan.blockedRicherType(
                karaoke("lyricsplus", "LyricsPlus karaoke"), lyricsPlusOnly).isEmpty())
            throw new AssertionError("karaoke lyrics are already the richest type");
        if (!LyricsProviderSelectionPlan.blockedRicherType(
                plain("lyricsplus", "LyricsPlus plain"),
                snapshot(List.of("lyricsplus"), config("lyricsplus", false, false, false, true))).isEmpty())
            throw new AssertionError("a disabled source has no switch to offer");
        var manual = plain("lrclib", "LRCLIB plain").withSelection("lrclib", LyricsProviderSelectionPlan.MANUAL_SELECTION);
        if (!LyricsProviderSelectionPlan.blockedRicherType(manual, lrclibOnly).isEmpty())
            throw new AssertionError("a manual pick bypasses the type switches");
        // A per-track pick enables every type for its provider, so the switches cannot hold it back.
        if (!LyricsProviderSelectionPlan.blockedRicherType(
                plain("lrclib", "LRCLIB plain"), lrclibOnly.withSelectedProvider("lrclib")).isEmpty())
            throw new AssertionError("a per-track provider pick enables all types");

        // The hint text names the source and the chip, and survives a missing translation.
        for (String lang : new String[]{"en", "zh-CN", "zh-TW", "ko", "ja"}) {
            String syncedLabel = AppI18n.t(lang, "lyrics_provider.synced");
            String karaokeLabel = AppI18n.t(lang, "lyrics_provider.karaoke");
            String synced = String.format(AppI18n.t(lang, "repo.detail.synced_type_off_format"), "LRCLIB", syncedLabel);
            String karaoke = String.format(AppI18n.t(lang, "repo.detail.karaoke_type_off_format"), "LyricsPlus", karaokeLabel);
            if (!synced.contains("LRCLIB") || !synced.contains(syncedLabel))
                throw new AssertionError("hint must name the source and the chip: " + lang);
            if (!karaoke.contains("LyricsPlus") || !karaoke.contains(karaokeLabel))
                throw new AssertionError("karaoke hint must name the chip: " + lang);
            if (synced.startsWith("repo.detail.") || karaoke.startsWith("repo.detail."))
                throw new AssertionError("hint key missing: " + lang);
            String card = String.format(AppI18n.t(lang, "lyrics_provider.type_off_hint_format"), "LRCLIB", syncedLabel);
            String action = String.format(AppI18n.t(lang, "lyrics_provider.type_off_action_format"), syncedLabel);
            if (card.startsWith("lyrics_provider.") || action.startsWith("lyrics_provider."))
                throw new AssertionError("type gate key missing: " + lang);
            if (!card.contains(syncedLabel) || !action.contains(syncedLabel))
                throw new AssertionError("type gate must quote the chip: " + lang);
        }
        if (!AppI18n.t("zh-CN", "lyrics_provider.synced").equals("同步歌词"))
            throw new AssertionError("Chinese readers see the chip the hint quotes");
        if (!AppI18n.t("en", "lyrics_provider.synced").equals("Synced Lyrics"))
            throw new AssertionError("English chip label changed");

        System.out.println("Provider type gate regression: blocked types, hints, manual picks and localized text passed");
    }
}
