package dev.lyriscope.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full-fidelity JSON export and restore for Lyriscope's settings, per-track overrides, local
 * lyrics, manual picks and UI theme choices. Supports stripping API keys/tokens on export.
 */
final class SettingsBackupManager {
    private static final String FORMAT_ID = "lyriscope-backup";
    private static final int FORMAT_VERSION = 1;

    private static final String[] PREFS_NAMES = {
            AiLyricsSettings.PREFS_NAME,
            "lyrics_provider_settings",
            PlatformTranslationSettings.PREFS,
            LyricsPageSettings.PREFS,
            "local_lyrics",
            "manual_lyrics_picks_v1",
            "network_region",
            "settings_ui"
    };

    private static final Set<String> SECRET_KEYS = new HashSet<>(Arrays.asList(
            AiLyricsSettings.KEY_API_KEYS,
            AiLyricsSettings.KEY_POLLINATIONS_ACCESS_TOKEN,
            AiLyricsSettings.KEY_SPOTIFY_CLIENT_ID,
            AiLyricsSettings.KEY_SPOTIFY_CLIENT_SECRET
    ));

    static final class BackupSummary {
        final int version;
        final String exportedAt;
        final boolean includesSecrets;
        final int sectionCount;
        final int entryCount;

        BackupSummary(int version, String exportedAt, boolean includesSecrets, int sectionCount, int entryCount) {
            this.version = version;
            this.exportedAt = exportedAt;
            this.includesSecrets = includesSecrets;
            this.sectionCount = sectionCount;
            this.entryCount = entryCount;
        }
    }

    private SettingsBackupManager() {}

    static String suggestedFileName() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
        return "lyriscope-backup-" + stamp + ".json";
    }

    static String exportJson(Context context, boolean includeSecrets) throws JSONException {
        Context app = context.getApplicationContext();
        Context host = app == null ? context : app;
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_ID);
        root.put("version", FORMAT_VERSION);
        root.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()));
        root.put("includesSecrets", includeSecrets);

        JSONObject stores = new JSONObject();
        for (String name : PREFS_NAMES) {
            SharedPreferences prefs = host.getSharedPreferences(name, Context.MODE_PRIVATE);
            JSONObject serialized = serializePrefs(name, prefs.getAll(), includeSecrets);
            if (serialized.length() > 0) {
                stores.put(name, serialized);
            }
        }
        root.put("stores", stores);
        return root.toString(2);
    }

    static BackupSummary inspect(String jsonText) throws JSONException {
        JSONObject root = parseValidatedRoot(jsonText);
        JSONObject stores = root.getJSONObject("stores");
        int sectionCount = 0;
        int entryCount = 0;
        Set<String> allowed = new HashSet<>(Arrays.asList(PREFS_NAMES));
        Iterator<String> storeKeys = stores.keys();
        while (storeKeys.hasNext()) {
            String name = storeKeys.next();
            if (!allowed.contains(name)) continue;
            JSONObject entries = stores.optJSONObject(name);
            if (entries == null || entries.length() == 0) continue;
            sectionCount++;
            entryCount += entries.length();
        }
        return new BackupSummary(
                root.optInt("version", FORMAT_VERSION),
                root.optString("exportedAt", ""),
                root.optBoolean("includesSecrets", false),
                sectionCount,
                entryCount
        );
    }

    static BackupSummary restoreJson(Context context, String jsonText) throws JSONException {
        JSONObject root = parseValidatedRoot(jsonText);
        JSONObject stores = root.getJSONObject("stores");
        Context app = context.getApplicationContext();
        Context host = app == null ? context : app;
        Set<String> allowed = new HashSet<>(Arrays.asList(PREFS_NAMES));
        boolean includesSecrets = root.optBoolean("includesSecrets", false);
        int sectionCount = 0;
        int entryCount = 0;

        for (String name : PREFS_NAMES) {
            if (!allowed.contains(name)) continue;
            JSONObject entries = stores.optJSONObject(name);
            if (entries == null) continue;
            SharedPreferences prefs = host.getSharedPreferences(name, Context.MODE_PRIVATE);
            Map<String, Object> preservedSecrets = new LinkedHashMap<>();
            if (!includesSecrets && AiLyricsSettings.PREFS_NAME.equals(name)) {
                Map<String, ?> existing = prefs.getAll();
                for (String key : SECRET_KEYS) {
                    if (existing.containsKey(key)) preservedSecrets.put(key, existing.get(key));
                }
            }
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            int written = applyEntries(editor, entries);
            for (Map.Entry<String, Object> secret : preservedSecrets.entrySet()) {
                putRaw(editor, secret.getKey(), secret.getValue());
            }
            editor.apply();
            if (written > 0) {
                sectionCount++;
                entryCount += written;
            }
        }
        return new BackupSummary(
                root.optInt("version", FORMAT_VERSION),
                root.optString("exportedAt", ""),
                includesSecrets,
                sectionCount,
                entryCount
        );
    }

    private static JSONObject parseValidatedRoot(String jsonText) throws JSONException {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            throw new JSONException("Empty backup");
        }
        JSONObject root = new JSONObject(jsonText);
        if (!FORMAT_ID.equals(root.optString("format", ""))) {
            throw new JSONException("Unsupported backup format");
        }
        if (root.optJSONObject("stores") == null) {
            throw new JSONException("Missing stores");
        }
        return root;
    }

    private static JSONObject serializePrefs(String storeName, Map<String, ?> values, boolean includeSecrets)
            throws JSONException {
        JSONObject out = new JSONObject();
        if (values == null) return out;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || value == null) continue;
            if (!includeSecrets && AiLyricsSettings.PREFS_NAME.equals(storeName)) {
                if (SECRET_KEYS.contains(key)) continue;
                if (AiLyricsSettings.KEY_AI_PROVIDER_PROFILES.equals(key) && value instanceof String) {
                    value = stripKeysFromJsonMap((String) value, "apiKey");
                } else if ("openai_connections_v1".equals(key) && value instanceof String) {
                    value = stripKeysFromJsonArray((String) value, "apiKey");
                }
            }
            JSONObject typed = encodeValue(value);
            if (typed != null) out.put(key, typed);
        }
        return out;
    }

    private static String stripKeysFromJsonMap(String raw, String field) {
        try {
            JSONObject obj = new JSONObject(raw);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                JSONObject child = obj.optJSONObject(keys.next());
                if (child != null && child.has(field)) child.put(field, "");
            }
            return obj.toString();
        } catch (JSONException ignored) {
            return raw;
        }
    }

    private static String stripKeysFromJsonArray(String raw, String field) {
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject child = array.optJSONObject(index);
                if (child != null && child.has(field)) child.put(field, "");
            }
            return array.toString();
        } catch (JSONException ignored) {
            return raw;
        }
    }

    private static JSONObject encodeValue(Object value) throws JSONException {
        JSONObject node = new JSONObject();
        if (value instanceof Boolean) {
            node.put("t", "b");
            node.put("v", (Boolean) value);
            return node;
        }
        if (value instanceof Integer) {
            node.put("t", "i");
            node.put("v", (Integer) value);
            return node;
        }
        if (value instanceof Long) {
            node.put("t", "l");
            node.put("v", (Long) value);
            return node;
        }
        if (value instanceof Float) {
            node.put("t", "f");
            node.put("v", ((Float) value).doubleValue());
            return node;
        }
        if (value instanceof String) {
            node.put("t", "s");
            node.put("v", (String) value);
            return node;
        }
        if (value instanceof Set<?>) {
            JSONArray array = new JSONArray();
            for (Object item : (Set<?>) value) {
                if (item instanceof String) array.put(item);
            }
            node.put("t", "ss");
            node.put("v", array);
            return node;
        }
        return null;
    }

    private static int applyEntries(SharedPreferences.Editor editor, JSONObject entries) {
        int count = 0;
        Iterator<String> keys = entries.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject node = entries.optJSONObject(key);
            if (node == null) continue;
            String type = node.optString("t", "");
            switch (type) {
                case "b":
                    editor.putBoolean(key, node.optBoolean("v", false));
                    count++;
                    break;
                case "i":
                    editor.putInt(key, node.optInt("v", 0));
                    count++;
                    break;
                case "l":
                    editor.putLong(key, node.optLong("v", 0L));
                    count++;
                    break;
                case "f":
                    editor.putFloat(key, (float) node.optDouble("v", 0d));
                    count++;
                    break;
                case "s":
                    editor.putString(key, node.optString("v", ""));
                    count++;
                    break;
                case "ss": {
                    JSONArray array = node.optJSONArray("v");
                    Set<String> set = new LinkedHashSet<>();
                    if (array != null) {
                        for (int index = 0; index < array.length(); index++) {
                            String item = array.optString(index, null);
                            if (item != null) set.add(item);
                        }
                    }
                    editor.putStringSet(key, set);
                    count++;
                    break;
                }
                default:
                    break;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static void putRaw(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set<?>) editor.putStringSet(key, (Set<String>) value);
    }
}
