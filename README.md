# Lyriscope

Spotify 的歌词增强模块：逐句高亮、译文与罗马音、歌曲研究与文化背景注释，都叠在播放页上。

[![CI](https://github.com/Escays/Lyriscope-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Escays/Lyriscope-Android/actions/workflows/android-ci.yml)

> 非官方项目。与 Spotify、ivLyrics 的作者均无隶属关系。
> 代码从 [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android) 分叉而来，之后品牌、包名、设置页、歌词来源与翻译链路都重写过，已不再跟随上游同步。

## 兼容性

| 项目 | 要求 |
| --- | --- |
| Android | 8.0（API 26）及以上 |
| 注入框架 | LSPosed（Xposed API 82+），作用域勾选 **Spotify** |
| Spotify | **9.2.0.2053**、9.1.80.2221、9.1.42.2058 |

Spotify 每次发版都会用 R8 重新混淆 `p.*` 类名，所以每个受支持的版本都要在
[`SpotifyMapping.java`](spotify-module/src/main/java/dev/lyriscope/spotify/SpotifyMapping.java)
里登记一份类名与锚点表。上表之外的版本仍然能装，但注入点解析不到时会跳过对应功能，
并在启动日志里逐个列出缺了什么，不会静默失效。适配新版本的做法见
[`spotify-module/ADAPTING.md`](spotify-module/ADAPTING.md)。

建议关闭 Spotify 的自动更新：混淆一变，注入就会落空，先确认目标版本号再升级。

## 功能

- **歌词卡片**：逐句高亮，原文 / 译文 / 罗马音三层可分别开关；长按标题打开控制面板。
- **歌词来源**：LRCLIB、lrc.red、Unison、LyricsPlus，按你设定的顺序尝试；网易云、QQ 音乐、酷狗提供平台自带译文与罗马音。
- **翻译**：自动 / Bing / Google / 自建的 OpenAI 兼容接口，可只翻当前一句。
- **歌曲研究**：点灯泡面板，按需生成歌曲简介、逐句讲解与文化背景注释。
- **手动找歌词**：四个曲库任选，整首替换歌词与时间轴，译文跟随所选曲库。
- **设置**：5 组 13 页，深色默认、浅色可切；网络与地区预设附连接体检，逐条说明哪条链路不通、不通会失去什么。

## 安装

1. 下载 `Lyriscope-Module-<版本>.apk`（Release 页面只提供模块包）。
2. 在 LSPosed 里启用 **Lyriscope**，作用域勾选 **Spotify**。
3. 强制停止 Spotify，再重新打开。

不需要重新打包 Spotify，模块在运行时注入。

## 使用

- 播放页上长按歌词区域进入设置：歌词与翻译、同步、背景、曲库。
- 长按底部的「译文」标签：只对当前歌曲或全部歌曲调整译文来源。
- 点灯泡：歌曲简介、逐句歌词与文化注释。

## 构建

需要 JDK 21 与 Android SDK（`compileSdk 36`、`minSdk 26`）。

```sh
export JAVA_HOME="/path/to/android-studio/jbr"
export ANDROID_HOME="/path/to/Android/Sdk"

./gradlew :spotify-module:assembleRelease   # 模块
./gradlew :app:assembleDebug                # 独立应用（可选）
```

模块产物：`spotify-module/build/outputs/apk/release/spotify-module-release.apk`。

仓库里还有一个功能等价的独立应用（`app/`），不需要 LSPosed，靠通知使用权读取播放状态。
它不在 Release 页面分发，只用于开发和对比调试。

## 测试

```sh
python3 tests/check_module_boundaries.py --built       # 模块边界与打包内容
python3 tests/run_settings_ui_regression.py            # 设置页契约
python3 tests/run_platform_translation_regression.py   # 平台译词对齐
```

`tests/` 下是按功能拆分的回归脚本，绝大多数不需要设备。
[`android-ci.yml`](.github/workflows/android-ci.yml) 里列出的就是每次提交都会跑的全集；
涉及真机渲染的 `app/src/androidTest` 需要 `connectedDebugAndroidTest`。

## 发布

版本号只有一个来源：根目录 `gradle.properties` 的 `lyriscopeVersionName` / `lyriscopeVersionCode`。
推一个 `v*` 标签会触发流水线，构建、校验签名与版本号，然后发布：

| 文件 | 说明 |
| --- | --- |
| `Lyriscope-Module-<tag>.apk` | 模块。Release 页面唯一的 APK。 |
| `Lyriscope-<tag>-version.json` | 版本清单，应用内「检查更新」读它。 |

发布说明由流水线生成（中英双语）；配好 `AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL` 时由模型撰写，
否则按提交记录归类生成。正式发布需要 `LYRISCOPE_RELEASE_*` 签名配置，本地不带配置的构建会退回调试密钥。

## 目录

```
app/             独立应用
shared/          两端共用：界面、渲染、歌词、翻译
spotify-module/  LSPosed 模块（注入 Spotify）
tests/           回归与体检脚本
docs/            版本说明与发布记录
```

## 致谢

思路与数据来自这些开源项目，应用内「设置 → 关于 → 致谢」也有一份可点击的列表：

- [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android) — ivLis Studio（代码基线）
- [HyperLyrics Enhanced](https://github.com/juren233/HyperLyrics-Enhanced) — juren233
- [HyperLyric](https://github.com/limczhh/HyperLyric) — limczhh
- 歌词数据：[LRCLIB](https://lrclib.net)、[lrc.red](https://lrc.red)、[LyricsPlus](https://github.com/ibratabian17/lyricsplus)、[Unison](https://github.com/better-lyrics/unison)

## 许可

上游 `ivLyrics-Android` 没有附 LICENSE 文件，本仓库同样不主张额外授权。
再分发或商用前，请自行确认原作者的授权条件。

歌词、译文、视频等第三方内容不属于本项目，使用前请自行确认版权与平台条款。
