# Lyriscope

A lyrics module for Spotify: line-synced highlighting, translation and romanisation, song research and cultural notes layered over the player.

[![CI](https://github.com/Escays/Lyriscope-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Escays/Lyriscope-Android/actions/workflows/android-ci.yml)

> Unofficial project. Not affiliated with Spotify or with the ivLyrics author.
> The code started as a fork of [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android); the brand, package names, settings, lyric sources and translation pipeline have since been rewritten, and it no longer tracks upstream.

[中文](README.md)

## Compatibility

| | Requirement |
| --- | --- |
| Android | 8.0 (API 26) or newer |
| Framework | LSPosed (Xposed API 82+), with **Spotify** in its scope |
| Spotify | **9.2.0.2053**, 9.1.80.2221, 9.1.42.2058 |

Spotify renames its obfuscated `p.*` classes with R8 on every release, so each supported build is registered with its own class and anchor table in
[`SpotifyMapping.java`](spotify-module/src/main/java/dev/lyriscope/spotify/SpotifyMapping.java).
Builds outside that table still install; anchors that fail to resolve simply skip their feature and are listed in the startup log instead of failing silently.
See [`spotify-module/ADAPTING.md`](spotify-module/ADAPTING.md) for adding a build.

Keep Spotify's auto-update off: when the obfuscation changes, injection lands on nothing. Check the target version before you upgrade.

## Features

- **Lyrics card**: line-by-line highlight with original, translation and romanisation on separate layers; long-press the title for the control sheet.
- **Sources**: LRCLIB, lrc.red, Unison and LyricsPlus in the order you set; NetEase, QQ Music and Kugou contribute their own translations and romanisation.
- **Translation**: automatic, Bing, Google, or your own OpenAI-compatible endpoint — including single-line translation.
- **Song research**: the bulb sheet generates a summary, line notes and cultural annotations on demand.
- **Manual search**: pick one of four catalogues to replace the lyric set and timeline; the translation follows that catalogue.
- **Settings**: five groups over thirteen pages, dark by default with a light theme; region presets plus a connection check that names the failing link and what it costs.

## Install

1. Download `Lyriscope-Module-<version>.apk` — the release page ships the module only.
2. Enable **Lyriscope** in LSPosed and tick **Spotify** in its scope.
3. Force-stop Spotify, then open it again.

Spotify itself is not patched; the module injects at runtime.

## Usage

- Long-press the lyrics area on the player to open settings: lyrics and translation, sync, background, catalogue.
- Long-press the **translation** pill to change the translation source for this song or for all songs.
- Tap the bulb for the song summary, line notes and cultural annotations.

## Build

JDK 21 and the Android SDK are required (`compileSdk 36`, `minSdk 26`).

```sh
export JAVA_HOME="/path/to/android-studio/jbr"
export ANDROID_HOME="/path/to/Android/Sdk"

./gradlew :spotify-module:assembleRelease   # module
./gradlew :app:assembleDebug                # standalone app (optional)
```

Module output: `spotify-module/build/outputs/apk/release/spotify-module-release.apk`.

The repository also holds a feature-equivalent standalone app (`app/`) that needs no LSPosed and reads playback through notification access. It is not distributed on the release page; it exists for development and side-by-side debugging.

## Tests

```sh
python3 tests/check_module_boundaries.py --built       # module boundaries and packaging
python3 tests/run_settings_ui_regression.py            # settings contracts
python3 tests/run_platform_translation_regression.py   # platform translation alignment
```

`tests/` holds per-feature regressions, almost all of which need no device. [`android-ci.yml`](.github/workflows/android-ci.yml) lists the full set that runs on every push; the on-device checks under `app/src/androidTest` need `connectedDebugAndroidTest`.

## Release

The version lives in one place: `lyriscopeVersionName` / `lyriscopeVersionCode` in the root `gradle.properties`. Pushing a `v*` tag builds, verifies the signature and version, then publishes:

| File | What it is |
| --- | --- |
| `Lyriscope-Module-<tag>.apk` | The module — the only APK on the release page. |
| `Lyriscope-<tag>-version.json` | Version manifest read by the in-app update check. |

Release notes are generated in Chinese and English by the pipeline: written by a model when `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` are configured, otherwise grouped from the commit log. Publishing needs the `LYRISCOPE_RELEASE_*` signing configuration; a local build without it falls back to the debug key.

## Layout

```
app/             standalone application
shared/          UI, rendering, lyric and translation logic used by both
spotify-module/  the LSPosed module (injects into Spotify)
tests/           regressions and probes
docs/            release notes and records
```

## Credits

Ideas and data come from these projects; the app lists them with links under **Settings → About → Credits**:

- [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android) — ivLis Studio (code baseline)
- [HyperLyrics Enhanced](https://github.com/juren233/HyperLyrics-Enhanced) — juren233
- [HyperLyric](https://github.com/limczhh/HyperLyric) — limczhh
- Lyric data: [LRCLIB](https://lrclib.net), [lrc.red](https://lrc.red), [LyricsPlus](https://github.com/ibratabian17/lyricsplus), [Unison](https://github.com/better-lyrics/unison)

## Licence

Upstream `ivLyrics-Android` ships no LICENSE file, and this repository claims no additional grant. Confirm the original author's terms before redistributing or shipping commercially.

Lyrics, translations and video belong to their own rights holders; check copyright and platform terms before use.
