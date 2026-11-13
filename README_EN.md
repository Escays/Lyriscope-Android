# Lyriscope for Spotify

Lyrics, enhanced, inside Spotify: an **LSPosed module** plus a standalone app built from the same code.

The code started as a fork of [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android), but it has moved far enough that it no longer tracks upstream and no longer calls itself ivLyrics — the brand, package names, settings, card interaction, sources and translation pipeline have all changed.

> Unofficial project. Not affiliated with Spotify or with the ivLyrics author.

[中文](README.md)

## What it does

**The lyrics card, over Spotify's player**

- Line-by-line highlight with original, translation and romanisation on separate layers
- Long-press the track title for the bottom sheet: lyrics and translation, sync, video background, background, catalogue
- Long-press a translation to translate that single line, or to switch engines
- The bulb sheet: song meaning, line-by-line notes, cultural annotations

**Sources and translation**

- Playback lyrics from LRCLIB, lrc.red, Unison, LyricsPlus and others, in the order you set
- Platform translations from NetEase, QQ Music and Kugou, with the platform's own romanisation when it has one
- Manual search across four catalogues — replaces the whole lyric set and timeline
- Translation engines: automatic, Bing, Google, or your own OpenAI-compatible endpoint

**Settings**

- A HyperOS-style settings screen, dark by default, light available
- Region presets (mainland China / overseas / custom) and a connection check that says which link is down and what it costs you
- Global search, grouped navigation, a credits section at the bottom

## Install

**As an LSPosed module (recommended)**

1. Install `Lyriscope-Spotify-*.apk`
2. Enable **Lyriscope for Spotify** in LSPosed and tick **Spotify** in its scope
3. Force-stop Spotify and open it again

Spotify itself is not patched — the module injects at runtime.

**Standalone app**

Install `Lyriscope-*.apk` and use it directly. It keeps its own settings and cache, separate from the module's copy.

## Build

JDK 21 and the Android SDK are required (see `app/build.gradle` for compileSdk).

```sh
export JAVA_HOME="/path/to/android-studio/jbr"
export ANDROID_HOME="/path/to/Android/Sdk"

./gradlew :spotify-module:assembleRelease :app:assembleDebug
```

Outputs:

- module: `spotify-module/build/outputs/apk/release/spotify-module-release.apk`
- standalone: `app/build/outputs/apk/debug/app-debug.apk`

## Verification

```sh
python tests/check_module_boundaries.py --built      # module boundaries and packaging
python tests/run_platform_translation_regression.py  # platform translation alignment
```

`tests/` also holds a set of smaller regressions, most of which need no device.

## Layout

```
app/             standalone application
shared/          UI, rendering, lyric and translation logic used by both
spotify-module/  the LSPosed module (injects into Spotify)
tests/           regressions and probes
```

## Relationship to upstream

The baseline is `ivLis-Studio/ivLyrics-Android`; everything since has been written here. Upstream ships no LICENSE file, and this repository claims no additional grant — check the original author's terms before redistributing or shipping commercially.

The projects this one learned from are also listed, with links, under **Settings → Credits** in the app:

- [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android) — ivLis Studio
- [HyperLyrics Enhanced](https://github.com/juren233/HyperLyrics-Enhanced) — juren233
- [HyperLyric](https://github.com/limczhh/HyperLyric) — limczhh
- [LRCLIB](https://lrclib.net), [lrc.red](https://lrc.red), [LyricsPlus](https://github.com/ibratabian17/lyricsplus), [Unison](https://github.com/better-lyrics/unison)

Lyrics, translations and video belong to their own rights holders; confirm copyright and platform terms before you use them.
