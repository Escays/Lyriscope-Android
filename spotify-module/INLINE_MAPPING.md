# Spotify 9.1.80.2221 inline lyrics mapping

Verified against the locally saved host APK on 2026-09-07. Its manifest contains
version `9.1.80.2221`; SHA-256:
`f4826f9a9148b15d4a7adeb10a01603a6eca2ff29d16c9d59a320439b4ff17da`.
This document records hook contracts, not device playback validation.

> **9.2.0.2053 maps the card path and the inline slot, not the inline gates.**
> The renderer moved into an R8-merged lambda holder (`p.kpc0`, one method per
> lambda, so no case field is needed) and the model became `p.yyf0`; both are in
> `SpotifyMapping.V9_2_0`. The two visibility gates and the native Lyrics On/Off
> toggle are still unmapped and stay `null`, so the preview follows the host's own
> decision to compose the slot instead of enforcing `key_lyrics_on_npv_visible`,
> Canvas, automobile and mixing itself. The slot modifier must not impose a fixed
> height: cached lyric-row modifiers (`Modifier.height(24.dp)`) clip the
> translation, `p.nyl.a` lets the preview measure both rows.
> Fingerprint of the APK these mappings were verified against:
> `177b548316a45ca4f02176b78d23f02c2f4dd54aff269f3d0decd00738aab083`
> (`tests/run_mapping_audit.py --version 9.2.0.2053` passes 18 anchors).

| Host mapping | Native behavior | ivLyrics behavior |
| --- | --- | --- |
| `p.jm7.invokeSuspend`, branch `a == 1` | Native lyric result is `p.mx80`, permitted Connect automobile mode (`!b || d`), and audio player (`!c`) must all pass. | Return the player restrictions without requiring native lyric data. |
| `p.pca0.a` | Shares the `jm7` availability with the inline element and context menu. | Both consumers receive the independent availability result. |
| `p.cp5.invokeSuspend`, branch `a == 2` | Combines Canvas (`b`), saved preference (`c`), availability (`d`) and mixing (`e`): `!b && c && d && !e`. | Preserve Canvas, preference and availability. Mixing alone does not hide a song; the shared preview excludes DJ speech. |
| `p.dam0` | Owns `key_lyrics_on_npv_visible`. | Continue observing the host preference; no preference write or default change. |
| `p.ziw0` → `p.m6o0` → `p.b9p0.apply` | Converts `pca0.a` plus the saved preference into an optional boolean, then builds `npv_lyrics_toggle`. | Keep the native menu item and action. |
| `p.d2w0.e(ContextTrack)` inside `p.b9p0.apply` | A second menu guard requires track metadata `has_lyrics` and no `parent_episode_uri`. | Accept a `spotify:track:` without a parent episode. Override this method only within the synchronous menu call, using a per-thread depth that also clears on exceptions. Other consumers retain native lyric eligibility. |
| `p.mk0.invokeSuspend`, branch `a == 12` | Always returns `p.lba0`, including an empty model when native lyrics are absent. | The renderer already handles that empty model; fabricated Spotify lyric data is unnecessary. |
| `p.nba0.c1`, branch `a == 0` | Renders `p.lba0` in the slot above the song title. | Mount the shared ivLyrics preview through the host Compose AndroidView interop. |

`tests/run_inline_hooks_regression.py` compiles the production hook classes with
synthetic host, Android and Xposed surfaces. It covers absent/native lyrics,
the two menu gates, unrelated hook branches, nested/failed menu scopes,
saved preference toggling, mixing, video/audio transitions, host reattachment
and rendering an empty native lyric model. `tests/run_inline_gate_regression.py`
covers the full flag truth table. Neither test starts an emulator or device.

## Where these contracts live in a build

The table above is behaviour, not names. The names for one build sit in
`dev.lyriscope.spotify.SpotifyMapping`: one constant per `versionName`, grouped
by feature, with `null` groups meaning the build has no such feature. Hooks read
that table and never spell a `p.*` name. `SpotifyMappingCheck` resolves every
anchor before installation and logs the labels it could not resolve, so a
mismatched build names what drifted instead of failing silently.

Adding a build, or re-checking these contracts on a new one, is documented in
[ADAPTING.md](ADAPTING.md). `tools/mapping_probe.py` reads the host APK's DEX
directly and prints candidate classes for each contract's shape, which is how
the `p.*` names in this document were found without a decompiler.
