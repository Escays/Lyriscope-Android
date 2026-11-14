#!/usr/bin/env python3
"""Run the production appearance persistence, cloud sync and native-lyric filtering without an SDK."""
import re
import subprocess
from regression_runtime import SHARED, REPORTS, java_tool, json_jar, classpath

def declaration(source, signature):
    start = source.index(signature)
    cursor = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[cursor] == "{") - (source[cursor] == "}")
        cursor += 1
    return source[start:cursor]

settings = (SHARED / "AiLyricsSettings.java").read_text()
renderer = (SHARED / "LyricsView.java").read_text()
constants = "\n".join("static final String " + value for value in re.findall(
    r'^    (?:private )?static final String (KEY_\w+ = "[^"]*";)', settings, re.M))
cloud = settings[settings.index("    private static final Set<String> CLOUD_SETTING_KEYS"):
                 settings.index("    static final List<Provider> PROVIDERS")]
methods = "\n".join(declaration(settings, signature) for signature in (
    "JSONObject exportCloudSettings()", "synchronized void importCloudSettings(",
    "String titleTapAction()",
    "boolean landscapeShowOriginal()", "boolean landscapeShowArtwork()",
    "boolean showLyricsNotFound()", "void setAppearanceChoice(", "void setAppearanceToggle("))
methods += "\n" + "\n".join(declaration(settings, signature) for signature in (
    "void setVinylTrackChanges(", "void setVinylRotationSpeed(", "void setVinylSimplifiedTitle(",
    "boolean albumArtOpenLp()", "void setAlbumArtOpenLp("))
filtering = declaration(renderer, "private List<DrawGroup> visibleLyricGroups(")
work = REPORTS / "appearance"
work.mkdir(parents=True, exist_ok=True)
fixture = r'''
package dev.lyriscope.core;
import java.util.*;
import org.json.*;
public final class AppearanceRegression {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("check " + checks); checks++; }
    static class SharedPreferences {
        Map<String,Object> data = new LinkedHashMap<>();
        Map<String,?> getAll() { return data; }
        String getString(String key,String fallback) { return (String)data.getOrDefault(key,fallback); }
        boolean getBoolean(String key,boolean fallback) { return (Boolean)data.getOrDefault(key,fallback); }
        Editor edit() { return new Editor(); }
        class Editor {
            Editor putString(String key,String value) { data.put(key,value); return this; }
            Editor putBoolean(String key,boolean value) { data.put(key,value); return this; }
            Editor putInt(String key,int value) { data.put(key,value); return this; }
            Editor putFloat(String key,float value) { data.put(key,value); return this; }
            void apply() {}
        }
    }
    static class Settings {
        SharedPreferences prefs = new SharedPreferences();
        Object cachedSnapshot;
        // CONSTANTS
        // CLOUD
        // SETTINGS
    }
    static class DrawGroup {
        final boolean supplement;
        DrawGroup(boolean value) { supplement=value; }
    }
    static class Renderer {
        boolean originalVisible=true;
        // FILTER
    }
    public static void main(String[] args) throws Exception {
        Settings settings = new Settings();
        check(settings.albumArtOpenLp()); // Existing installations keep artwork entry enabled.
        check(Settings.KEY_ALBUM_ART_OPEN_LP.equals("album_art_open_lp"));
        settings.importCloudSettings(new JSONObject());
        check(settings.albumArtOpenLp()); // Older backups do not turn it off.
        check(settings.titleTapAction().equals("player"));
        check(settings.landscapeShowOriginal() && settings.landscapeShowArtwork() && settings.showLyricsNotFound());
        settings.prefs.edit().putInt(Settings.KEY_BACKGROUND_BRIGHTNESS,30).apply();
        settings.setAppearanceChoice(AppearanceOptions.TITLE_ACTION,"context");
        settings.setAppearanceToggle(AppearanceOptions.LANDSCAPE_ORIGINAL,false);
        settings.setAppearanceToggle(AppearanceOptions.LANDSCAPE_ARTWORK,false);
        settings.setAppearanceToggle(AppearanceOptions.EMPTY_NOTICE,false);
        settings.setVinylTrackChanges(false);
        settings.setVinylRotationSpeed(175);
        settings.setVinylSimplifiedTitle(true);
        settings.setAlbumArtOpenLp(false);
        check(!settings.albumArtOpenLp());
        Settings reopened = new Settings();
        reopened.prefs = settings.prefs;
        check(!reopened.albumArtOpenLp());
        settings.prefs.edit().putString("api_keys","private").apply();
        JSONObject exported = settings.exportCloudSettings();
        check(!exported.has("api_keys"));
        check(exported.has(Settings.KEY_ALBUM_ART_OPEN_LP) && !exported.getBoolean(Settings.KEY_ALBUM_ART_OPEN_LP));
        Settings restored = new Settings();
        restored.importCloudSettings(new JSONObject(exported.toString()));
        check(!restored.albumArtOpenLp());
        restored.importCloudSettings(new JSONObject());
        check(!restored.albumArtOpenLp()); // Partial/older backups preserve the user's choice.
        restored.setAlbumArtOpenLp(true);
        check(restored.albumArtOpenLp());
        check(restored.exportCloudSettings().getBoolean(Settings.KEY_ALBUM_ART_OPEN_LP));
        check(restored.titleTapAction().equals("context"));
        check(!restored.landscapeShowOriginal() && !restored.landscapeShowArtwork() && !restored.showLyricsNotFound());
        check(restored.prefs.data.get(Settings.KEY_BACKGROUND_BRIGHTNESS).equals(30));
        check(restored.prefs.data.get(RecordRotation.CHANGE_KEY).equals(false));
        check(restored.prefs.data.get(RecordRotation.SPEED_KEY).equals(175));
        check(restored.prefs.data.get(RecordRotation.SIMPLE_TITLE_KEY).equals(true));
        Renderer renderer = new Renderer();
        DrawGroup original = new DrawGroup(false), pronunciation = new DrawGroup(true), translation = new DrawGroup(true);
        List<DrawGroup> groups = new ArrayList<>(Arrays.asList(original,pronunciation,translation));
        check(renderer.visibleLyricGroups(new ArrayList<>(groups)).size()==3);
        renderer.originalVisible=false;
        check(renderer.visibleLyricGroups(new ArrayList<>(groups)).equals(Arrays.asList(pronunciation,translation)));
        renderer.originalVisible=true;
        check(renderer.visibleLyricGroups(new ArrayList<>(groups)).equals(groups));
        System.out.println("Appearance persistence/cloud/filtering regression: "+checks+" checks passed");
    }
}
'''
fixture = fixture.replace("// CONSTANTS", constants).replace("// CLOUD", cloud).replace("// SETTINGS", methods).replace("// FILTER", filtering)
target = work / "AppearanceRegression.java"
target.write_text(fixture)
dependencies = classpath(work, json_jar())
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", dependencies, "-d", str(work),
                str(SHARED / "AppearanceOptions.java"), str(SHARED / "RecordRotation.java"), str(target)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies, "dev.lyriscope.core.AppearanceRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
if result.returncode:
    raise SystemExit(result.returncode)

