# Lyriscope for Spotify — 模块说明

[独立应用](../README.md) · [English](#english)

把 Spotify 里的歌词卡片和整页歌词换成 Lyriscope 的 LSPosed 模块。模块在 LSPosed 列表里显示为 **Lyriscope for Spotify**。只想在单独的应用里看歌词，用[独立应用](../README.md)。

[下载模块](https://github.com/Escays/Lyriscope-Android/releases/latest) · [问题反馈](https://github.com/Escays/Lyriscope-Android/issues)

## 下哪个文件

Releases 里 **`Lyriscope-Spotify-v<版本>.apk`** 是模块；`Lyriscope-Android-v<版本>-release.apk` 是独立应用。

模块包里**不含 Spotify**，也不含已经打包好模块的 Spotify，安装了不会多出一个图标。

| 安装方式 | 需要什么 |
| --- | --- |
| LSPosed | 已 root 且 LSPosed 正常工作，在模块管理器里启用并勾选作用域。模块本身要求 Android 8.0+，再加上加载器与 Spotify 版本的条件。 |
| LSPatch | Android 9+，需要 LSPatch 管理器和一份 Spotify APK。不需要 root，但要自己打补丁。 |

模块目前认 Spotify **9.2.0.2053**、**9.1.80.2221**、**9.1.42.2058**，其他版本不会挂上歌词界面。9.2.0.2053 映射了卡片、整页、卡片排序和内嵌歌词行（槽位已通，两个可见性门控与原生 Lyrics 开关尚未重锚），**音视频切换、原生 SingAlong 在该版本尚未适配**（对应分组为 `null`，消费者自动跳过）。支持列表与每个版本对应的类名在 [`SpotifyMapping.java`](src/main/java/dev/lyriscope/spotify/SpotifyMapping.java)，加新版本的方法见 [ADAPTING.md](ADAPTING.md)。Spotify 的 APK 不在本项目里，需自行以合法方式获取。版本对上也**不代表**在所有机型、系统、加载器上都能正常工作。

实机基线是 root + LSPosed。[官方 LSPatch](https://github.com/LSPosed/LSPatch) 已归档，属于遗留方案，不要假设它能配合最新的 Android 或 Spotify。

## 用 LSPosed 装（推荐）

1. 强制停止 Spotify。
2. 安装 `Lyriscope-Spotify-v<版本>.apk`。
3. 在 LSPosed 里启用 **Lyriscope for Spotify**，作用域勾选 **Spotify（`com.spotify.music`）**。
4. 按加载器要求重启，再打开 Spotify。

这条路线**不需要重新打包 Spotify**，用的是已安装的原版。

## 用 LSPatch 装

1. 从[官方 LSPatch releases](https://github.com/LSPosed/LSPatch/releases)取 `manager.apk`，备好上面的模块包和受支持版本的 Spotify APK。
2. 新建补丁，**目标选 Spotify APK**——不要选模块 APK 自己。
3. 在「包含模块」这一步把 `Lyriscope-Spotify-v<版本>.apk` 加进去，按管理器提示选择内嵌或引用已安装模块。
4. 完成补丁，安装**生成的 Spotify APK**（它不是模块包本身）。
5. 彻底关掉那个 Spotify 再打开、登录、播放。

> 打过补丁的 Spotify 签名可能和现有安装不同，覆盖安装会被拒。先确认是否需要卸载原 Spotify——卸载会丢掉它的本地设置、下载，以及存在里面的模块设置与缓存。之后每次更新都要用同一个补丁签名。

模块不需要独立应用、通知读取权限或悬浮窗权限，也不需要配 Spotify Client ID / Secret：播放信息直接取自 Spotify 自己。

## 第一首歌与设置

放一首歌，在播放页的歌词卡片上打开整页歌词。Spotify 自己没有歌词的歌也能显示卡片，但要有歌词数据才有内容。播客、播客里的单曲不在范围内。

在歌词页打开「当前播放歌曲设置」，或长按标题/歌手区域，可以调语言、歌词来源、手动找歌词、同步。翻译、发音、费用与数据说明见[主 README](../README.md)。

- Bing、Google、DeepL、Local AI 都**只做翻译**，发音要另配 AI 提供商的连接信息、模型并打开发音显示。
- 想优先用 Local AI，就要把它**排在在线提供商前面**；只勾选它不会自动优先，要纯本地翻译得把其他在线翻译全部关掉。
- Local AI 需要 Android 9+、64 位 ARM 进程、下载好的模型和足够内存，在 LSPatch 这类宿主环境下引擎可能加载失败。歌词查询、模型下载这些在线功能仍然需要联网。
- 模块的设置、缓存、模型都存在 Spotify 那边，不会和独立应用自动共享。
- 自己的 LRC/TXT 文件在「设置 → 当前播放歌曲设置 → 歌词来源 → 导入歌词文件」里加到当前歌曲，优先级高于在线歌词，同一菜单里可以删除。

### 9.1.80.2221 上额外可用的

- 标题上方那一行歌词也用 Lyriscope 的。用 Spotify 的 **Lyrics · On/Off** 控制显隐，点歌词开整页。
- 支持的歌在整页歌词右下角会出现 Spotify 原生 SingAlong 的麦克风按钮，**OFF → LOW → HIGH → OFF** 循环，触屏出现、静置三秒隐藏。需要 Spotify 支持该曲，并且受在线播放、本地播放、音质条件限制（LOW 音质不可用）。它不能解锁功能开关或让不支持的歌变得可用。

这两条**不**适用于 9.1.42.2058，也不适用于独立应用。9.2.0.2053 的内嵌行尚未重锚可见性门控，这两条同样暂不可用。

## 更新与排错

更新前先**强制停止 Spotify**。LSPosed 下装同签名的新模块、确认启用与作用域、重启即可；LSPatch 内嵌模块的话，要用新模块**重新打一次补丁**。Spotify 自己更新后同样要确认版本是否仍受支持。

| 现象 | 先查什么 |
| --- | --- |
| 装了什么都没变 | 装的是不是模块包（不是独立应用）；Spotify 版本是否精确匹配；LSPosed 是否启用且勾了作用域／补丁里是否真的包含了模块。 |
| 装不上或更新被拒 | 新旧文件的签名是否一致。模块自身的签名与打完补丁的 Spotify 签名是两回事；本地 debug 构建和发布包用的密钥也可能不同。 |
| 没歌词或歌词不对 | 网络、所选歌词来源、手动找歌词；再清一次当前歌曲缓存。 |
| 没有麦克风按钮 | 版本是否为 9.1.80.2221；Spotify 是否支持该曲、当前播放条件是否满足。检查中或不支持的曲目会隐藏。 |
| Local AI 失败 | 进程是否 64 位、模型是否下载完、内存是否够。只选了本地翻译时，不会自动回退到在线。 |
| Spotify 打不开 | 先关掉模块/加载器的作用再试，并看加载器日志。 |

反馈请带上模块、Spotify、Android 版本，机型，**加载器与补丁方式**，复现步骤和报错，凭据打码。主 README 的[致谢与免责](../README.md)同样适用。

<details>
<summary>开发与验证</summary>

两个产品共用 `:shared` 里的歌词、ivSync、翻译、缓存与渲染。模块保持 Spotify 的包身份并使用它的登录态；LSPatch 会重新打包并签名宿主，root + LSPosed 则不需要替换宿主 APK。

从仓库根目录 `./build.sh :spotify-module:assembleRelease` 构建，产物是 `spotify-module/build/outputs/apk/release/spotify-module-release.apk`，发布时改名为 `Lyriscope-Spotify-<tag>.apk`。两个 APK 的版本都取自根目录 `gradle.properties` 的 `lyriscopeVersionName` / `lyriscopeVersionCode`。正式发布需要 `LYRISCOPE_RELEASE_*` 签名配置，本地不带配置的构建用 debug 密钥——更新时保持同一密钥。

原生菜单门控与回归覆盖见 [INLINE_MAPPING.md](INLINE_MAPPING.md)，新增 Spotify 版本的步骤与探针工具见 [ADAPTING.md](ADAPTING.md)，构建与签名见[架构说明](../ARCHITECTURE.md#build-and-verify)，验证见[测试说明](../tests/README.md)与[内嵌冒烟检查](../tests/EMBEDDED_INLINE_SMOKE.md)。

</details>

## English

The **Lyriscope for Spotify** module replaces Spotify's lyrics card and full lyrics page. Listed as **Lyriscope for Spotify** in LSPosed. For a standalone app, use [the app](../README_EN.md).

Download **`Lyriscope-Spotify-v<version>.apk`** from the [latest release](https://github.com/Escays/Lyriscope-Android/releases/latest); `Lyriscope-Android-v<version>-release.apk` is the separate app. The module contains neither Spotify nor a prepatched Spotify.

Spotify **9.2.0.2053**, **9.1.80.2221** and **9.1.42.2058** are recognised. 9.2.0.2053 maps the card, the full page, card ordering and the Now Playing inline slot (the two visibility gates and the native Lyrics toggle are not re-anchored yet): the audio/video switch and native SingAlong are not re-anchored on that build (their groups are `null`, so consumers skip them). Obtain a supported Spotify APK lawfully yourself. The baseline is rooted LSPosed; [LSPatch](https://github.com/LSPosed/LSPatch) is archived and legacy.

- **LSPosed:** force-stop Spotify, install the module APK, enable **Lyriscope for Spotify** with scope **`com.spotify.music`**, restart as the loader asks. Spotify is not repatched.
- **LSPatch:** patch the **Spotify APK** as target, add the module APK in the "include module" step, install the resulting Spotify APK. A patched host may need the old install removed first, which also erases its settings, downloads and the module's stored state.

Lyrics settings live in **Current track settings** or a long-press on the title. Bing, Google, DeepL and Local AI are translation-only; pronunciation needs a configured AI provider. Local AI needs Android 9+, a 64-bit ARM process, a downloaded model and enough memory, and only takes priority when moved ahead of online providers.

See the [English app guide](../README_EN.md) for translation costs, data use, local LRC import and credits.
