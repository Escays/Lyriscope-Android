package dev.lyriscope.core;

import android.content.SharedPreferences;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two reports from one reader, both about text that is "the same word spelled differently".
 *
 * <p>First: 王菲《臉》 searched by its traditional title matched perfectly and still showed 30%, the same
 * score as unrelated songs. The catalogue answers "脸", the comparison read two different characters,
 * and a title score of zero also triggers the "title and artist disagree" cap — so the whole list landed
 * on the same number. Second: turning 混合模式 off still called the translation engine, and the footer
 * then named the catalogue twice ("QQ音乐 + QQ音乐 + Bing翻译").
 */
public final class TraditionalMatchAndMixRegression {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        foldTableIsSane();
        traditionalTitleMatches();
        emptyArtistDoesNotFlattenTheList();
        mixOffStopsAtThePlatform();
        System.out.println("traditional match and mix regression: " + checks + " checks passed");
    }

    private static void foldTableIsSane() {
        check(HanFold.size() >= 400, "the fold table must cover the common variants, has " + HanFold.size());
        check("脸".equals(HanFold.toSimplified("臉")), "臉 folds to 脸");
        check("梦".equals(HanFold.toSimplified("夢")), "夢 folds to 梦");
        check("爱".equals(HanFold.toSimplified("愛")), "愛 folds to 爱");
        check("这".equals(HanFold.toSimplified("這")), "這 folds to 这");
        check("谁".equals(HanFold.toSimplified("誰")), "誰 folds to 谁");
        check("说".equals(HanFold.toSimplified("說")), "說 folds to 说");
        check("愿".equals(HanFold.toSimplified("願")), "願 folds to 愿");
        check("王菲".equals(HanFold.toSimplified("王菲")), "characters that do not differ stay as they are");
        check("".equals(HanFold.toSimplified(null)), "a missing title folds to nothing");
        check("脸".equals(HanFold.toSimplified("脸")), "folding is idempotent on simplified text");
    }

    /** The exact case: the same song, one spelling in the query and another in the catalogue. */
    private static void traditionalTitleMatches() {
        check(PlatformRecordingMatcher.textualSimilarity("臉", "脸") == 1d,
                "a one-character title must compare equal across the two spellings");
        check(PlatformRecordingMatcher.titleSimilarity("梦中人", "夢中人") == 1d,
                "a four-character title must compare equal across the two spellings");
        check(PlatformRecordingMatcher.titleSimilarity("脸 (Live)", "脸") > 0.9d,
                "a decorated title must still match the bare one");
        check(PlatformRecordingMatcher.searchQueries("臉", "王菲").contains("脸 王菲"),
                "the simplified spelling has to be searched as well");

        List<PlatformRecordingMatcher.Candidate> hits = new ArrayList<>();
        hits.add(new PlatformRecordingMatcher.Candidate("right", "脸", "王菲", "唱游", 240_000L));
        hits.add(new PlatformRecordingMatcher.Candidate("wrong", "飞", "王菲", "唱游", 240_000L));
        hits.add(new PlatformRecordingMatcher.Candidate("other", "脸", "张学友", "吻别", 230_000L));
        List<PlatformRecordingMatcher.Score> ranked =
                PlatformRecordingMatcher.rank("臉", "王菲", "唱游", 240_000L, hits);
        check("right".equals(ranked.get(0).candidate.id), "the recording that is playing must rank first");
        check(ranked.get(0).acceptable(), "it must pass the acceptance bar");
        check(ranked.get(0).total > 0.85d,
                "a perfect match must not read as 30%, got " + ranked.get(0).total);
        check(ranked.get(1).total < 0.62d, "an unrelated title of the same act must stay below the bar");
    }

    /** With the artist box empty there is nothing to disagree with, so nothing may be capped to 0.30. */
    private static void emptyArtistDoesNotFlattenTheList() {
        List<PlatformRecordingMatcher.Candidate> hits = List.of(
                new PlatformRecordingMatcher.Candidate("right", "脸", "王菲", "唱游", 240_000L),
                new PlatformRecordingMatcher.Candidate("wrong", "飞", "王菲", "唱游", 240_000L));
        List<PlatformRecordingMatcher.Score> ranked =
                PlatformRecordingMatcher.rank("臉", "", "", 0L, hits);
        check("right".equals(ranked.get(0).candidate.id), "the title still decides when no act was typed");
        check(ranked.get(0).total > 0.30d,
                "the best row must not be clamped to the same score as everything else, got "
                        + ranked.get(0).total);
        check(ranked.get(0).total > ranked.get(1).total,
                "two different titles must not share one score");
    }

    /** 混合模式 off: one catalogue's own translation, and no engine filling the gaps. */
    private static void mixOffStopsAtThePlatform() {
        Map<String, Object> values = new HashMap<>();
        PlatformTranslationSettings settings = new PlatformTranslationSettings(prefs(values));
        PlatformTranslationService service = new PlatformTranslationService(List.of(), settings);

        check(!service.stopsAtPlatform(), "mixing on must still allow the engine to fill gaps");
        settings.setMixSources(false);
        check(service.stopsAtPlatform(), "mixing off must keep the translation on the platform");
        check(!service.platformOnly(),
                "the 'platform only' switch is a different setting and stays untouched");

        settings.setMixSources(true);
        settings.setPlatformOnly(true);
        check(service.stopsAtPlatform(), "'platform only' keeps working with mixing on");
    }

    /** A SharedPreferences that lives in a map: enough to exercise the switches, nothing else. */
    private static SharedPreferences prefs(Map<String, Object> values) {
        return (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if ("edit".equals(method.getName())) {
                        return editor(values);
                    }
                    if ("getBoolean".equals(method.getName())) {
                        Object stored = values.get((String) args[0]);
                        return stored == null ? args[1] : stored;
                    }
                    if ("getString".equals(method.getName())) {
                        Object stored = values.get((String) args[0]);
                        return stored == null ? args[1] : stored;
                    }
                    if ("contains".equals(method.getName())) {
                        return values.containsKey(args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static SharedPreferences.Editor editor(Map<String, Object> values) {
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "putBoolean":
                        case "putString":
                        case "putInt":
                        case "putLong":
                        case "putFloat":
                        case "putStringSet":
                            values.put((String) args[0], args[1]);
                            return proxy;
                        case "remove":
                            values.remove((String) args[0]);
                            return proxy;
                        case "clear":
                            values.clear();
                            return proxy;
                        case "apply":
                            return null;
                        case "commit":
                            return true;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return null;
    }
}
