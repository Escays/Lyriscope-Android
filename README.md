# Lyriscope for Spotify

Spotify 歌词增强：一个 **LSPosed 模块**，外加一个可独立运行的同款应用。

本项目从 [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android) 分叉而来，但已经改得面目全非——品牌、包名、设置页、歌词卡片交互、来源与翻译链路都换过。所以它不再跟随上游同步，也不再自称 ivLyrics。

> 非官方项目，与 Spotify、ivLyrics 作者均无隶属关系。

[English](README_EN.md)

## 它做了什么

**歌词卡片（叠在 Spotify 播放页上）**

- 逐句高亮，原文 / 译文 / 罗马音分层显示
- 长按歌曲标题打开底部卡片：歌词与翻译、同步、视频背景、背景、曲库选择
- 长按译文可单独翻译某一句，或切换翻译引擎
- 点灯泡面板：歌曲简介、逐句歌词、文化背景注释

**歌词来源与翻译**

- 播放歌词：LRCLIB、lrc.red、Unison、LyricsPlus 等，按你设定的顺序尝试
- 平台译词：网易云、QQ 音乐、酷狗（平台自带译文与罗马音时一并取用）
- 手动找歌词：四个曲库任选，整首替换歌词与时间轴
- 翻译引擎：自动 / Bing / Google / 你自己的 OpenAI 兼容接口

**设置**

- HyperOS 风格的设置页，深色为默认，浅色可切
- 网络与地区预设（中国大陆 / 海外 / 自定义）＋ 连接体检，逐项说明哪条链路不通、不通会失去什么
- 全局搜索、分组导航、底部致谢

## 安装

**作为 LSPosed 模块（推荐）**

1. 安装 `Lyriscope-Spotify-*.apk`
2. 在 LSPosed 中启用 **Lyriscope for Spotify**，作用域勾选 **Spotify**
3. 强制停止 Spotify 后重新打开

不需要重新打包 Spotify——模块在运行时注入。

**独立应用**

安装 `Lyriscope-*.apk` 直接使用。独立应用有自己的设置与缓存，与模块内那份互不影响。

## 构建

需要 JDK 21 与 Android SDK（compileSdk 见 `app/build.gradle`）。

```sh
export JAVA_HOME="/path/to/android-studio/jbr"
export ANDROID_HOME="/path/to/Android/Sdk"

./gradlew :spotify-module:assembleRelease :app:assembleDebug
```

产物：

- 模块：`spotify-module/build/outputs/apk/release/spotify-module-release.apk`
- 独立应用：`app/build/outputs/apk/debug/app-debug.apk`

## 验证

```sh
python tests/check_module_boundaries.py --built      # 模块边界与打包内容
python tests/run_platform_translation_regression.py  # 平台译词对齐
```

`tests/` 下还有一批按功能拆分的回归脚本，多数不依赖设备，`.github/workflows/android-ci.yml` 里列的就是全部。

## 发布

版本号只有一个来源：根目录 `gradle.properties` 的 `lyriscopeVersionName` / `lyriscopeVersionCode`，两个 APK 都从这里取。打 `v*` 标签会触发发布流水线，产物命名固定：

| 文件 | 是什么 |
| --- | --- |
| `Lyriscope-Android-<tag>-release.apk` | 独立应用。**只有它**的文件名含 `-release`，应用内更新按这个标记挑包。 |
| `Lyriscope-Spotify-<tag>.apk` | Spotify 模块。标签里的 `-` 会换成 `_`，避免 `v1.2.3-release-candidate` 这类标签把它变成更新优先包。 |
| `Lyriscope-<tag>-version.json` | 版本清单，应用内「检查更新」读它。 |

发布说明由流水线生成（中文 + 英文），配好 `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` 时由模型撰写，否则用提交记录生成。正式发布需要 `LYRISCOPE_RELEASE_*` 签名配置，本地不带配置的构建用调试密钥。

## 目录

```
app/             独立应用
shared/          两端共用的界面、渲染、歌词与翻译逻辑
spotify-module/  LSPosed 模块（注入 Spotify）
tests/           回归与体检脚本
```

## 与上游的关系

代码基线是 `ivLis-Studio/ivLyrics-Android`，之后的所有改动都在本仓库完成。上游没有 LICENSE 文件，本仓库同样不主张额外授权；若要再分发或商用，请先自行确认原作者的授权条件。

被借鉴过思路的开源项目，在应用的 **设置 → 致谢** 里也有一份可点击的列表：

- [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android) — ivLis Studio
- [HyperLyrics Enhanced](https://github.com/juren233/HyperLyrics-Enhanced) — juren233
- [HyperLyric](https://github.com/limczhh/HyperLyric) — limczhh
- [LRCLIB](https://lrclib.net)、[lrc.red](https://lrc.red)、[LyricsPlus](https://github.com/ibratabian17/lyricsplus)、[Unison](https://github.com/better-lyrics/unison)

歌词、译文、视频等第三方内容不属于本项目，使用前请自行确认版权与平台条款。
