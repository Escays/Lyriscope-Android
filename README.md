# Lyriscope

Spotify Android 原生歌词增强 LSPosed 模块：逐字卡拉 OK、双语译文与罗马音、歌曲文化研究注释、Apple Music 式长按分享歌词海报、封面取色与 AGSL 流体背景，以及全新 KernelSU 风格 Miuix 设置界面。

[![CI](https://github.com/Escays/Lyriscope-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Escays/Lyriscope-Android/actions/workflows/android-ci.yml)
[![Release](https://img.shields.io/github/v/release/Escays/Lyriscope-Android?label=Release&color=277AF7)](https://github.com/Escays/Lyriscope-Android/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84)](https://github.com/Escays/Lyriscope-Android/releases)

> **非官方开源项目**，与 Spotify、ivLyrics 原作者均无隶属关系。  
> 代码早期分叉自 [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android)，现已独立演进并重写了品牌、包名、Miuix 设置界面、渲染管线、歌词源与翻译链路。

---

## 兼容性

| 项目 | 要求 |
| --- | --- |
| **Android** | 8.0（API 26）及以上（Monet 取色需 Android 12+，AGSL GPU 流体背景需 Android 13+） |
| **注入框架** | LSPosed（Xposed API 82+），作用域仅勾选 **Spotify** |
| **Spotify 版本** | **9.2.0.2053**、9.1.80.2221、9.1.42.2058 |

> Spotify 每次发版都会用 R8 重新混淆 `p.*` 类名，每个受支持版本均在 [`SpotifyMapping.java`](spotify-module/src/main/java/dev/lyriscope/spotify/SpotifyMapping.java) 中登记精确锚点。  
> 建议关闭 Spotify 自动更新；升级后可在 **「设置 → 语言与地区」顶部的 Hook 健康诊断卡** 一眼确认当前宿主版本与各注入点状态。适配新版本流程见 [`spotify-module/ADAPTING.md`](spotify-module/ADAPTING.md)。

---

## 核心特性

### 播放页与歌词页体验
- **内联歌词与播放器卡片**：播放页专辑封面下方内联显示实时歌词（支持原文 / 译文 / 注音自由组合），靠左模式下歌词首字与前奏动效图标均与专辑左缘、歌名精准齐平；Lyriscope 歌词卡片自动跟随封面取色，与全屏歌词页、画中画保持同源一致。
- **逐字卡拉 OK 与流体视觉**：支持逐字高亮与弹跳动效、间奏提示；Android 13+ 流体背景采用硬件加速 **AGSL `RuntimeShader`** 渲染，旧系统自动平滑回退。
- **Apple Music 式长按分享歌词海报**：在竖屏或横屏歌词页**长按任意歌词行**即可呼出海报制作器：
  - 顶部固定实时预览，下方无嵌套流畅滚动选词（支持 1–6 句多选与快捷连续选句）；
  - 4 套排版风格（纯色 / 流光 / 黑胶 / 浅纸）、3 种画幅（3:4 / 1:1 / 9:16）、居左/居中、紧凑/标准/醒目字号、装饰引号与封面色板；
  - 底部自带原生矢量 **Spotify 角标**，一键保存至相册 `Pictures/Lyriscope` 或直接分享。
- **双指缩放字号**：歌词页直接双指捏合实时调节原文字号并持久化，顶部浮现字号胶囊提示。
- **无时间轴歌词友好阅读**：手动应用或拉取到纯文本（无时间轴）歌词时，全部歌词以纯白清晰呈现、无距离暗淡，自由拖动浏览不会自动弹回开头。

### 多源歌词、翻译与歌曲研究
- **多曲库优先级**：支持 LRCLIB、lrc.red、Unison、LyricsPlus，以及 **网易云音乐、QQ 音乐、酷狗音乐** 平台原生译文与罗马音；支持手动跨曲库搜词与本地 `.lrc` / 纯文本导入。
- **多引擎翻译与注音**：支持自动 / Bing / Google / 自定义 OpenAI 兼容接口（Gemini、ChatGPT、Claude、Groq、OpenRouter、Perplexity 等）及离线本地模型；支持日语汉字注音（Furigana）。
- **歌曲研究与文化注释**：点击右上角灯泡面板，按需生成歌曲背景简介、逐句赏析与文化注释。

### KernelSU 风格 Miuix 设置与数据管理
- **现代 Miuix 设计语言**：参考 [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit) 重构全部设置页与 28dp 底部悬浮弹窗；支持 **跟随系统 / 浅色 / 深色**、**Android 12+ Monet 动态取色** 与 **8 套预设强调色**。
- **Hook 健康状态诊断卡**：在设置内直接展示 Spotify 宿主版本、映射匹配状态与各功能注入情况。
- **一键备份与恢复**：在 **「设置 → 权限与数据」** 中通过系统文件选择器导出 / 导入完整 JSON 备份（包含全部设置、单曲同步偏移/背景覆盖、本地歌词、手动选词与外观主题），导出时可选剔除 API Key 与 Token 等敏感凭据。

---

## 安装与使用

### 安装步骤
1. 前往 [Releases](https://github.com/Escays/Lyriscope-Android/releases/latest) 下载 `Lyriscope-Module-v<版本>.apk`（Release 仅分发模块安装包）。
2. 安装后在 **LSPosed 管理器** 中启用 **Lyriscope**，作用域勾选 **Spotify**。
3. 强制停止 Spotify 后重新打开即可生效（无需重打包 Spotify）。

### 常用交互手势
- **进入设置**：播放页长按歌词区域，或歌词页右上角菜单进入设置。
- **分享歌词海报**：歌词页**长按任意一句歌词**（带文化注释虚线的句子会提供「分享海报 / 查看注释」二选一）。
- **调节歌词字号**：歌词页**双指缩放**实时微调字号。
- **切换单曲/全局译文**：长按底部「译文」按钮，左右滑动切换当前歌曲与全局策略。
- **歌曲文化注释**：点击歌词页右上角灯泡图标。

---

## 本地构建与测试

环境要求：JDK 21、Android SDK（`compileSdk 36.1`、`minSdk 26`）、Python 3、Node.js。

```bash
export JAVA_HOME="/path/to/android-studio/jbr"
export ANDROID_HOME="/path/to/Android/Sdk"

# 构建 LSPosed 模块
./gradlew :spotify-module:assembleRelease

# 构建独立调试应用（可选，不随 Release 分发）
./gradlew :app:assembleDebug

# 单元测试与 Android Lint
./gradlew :shared:testDebugUnitTest :shared:lintDebug :app:lintDebug :spotify-module:lintDebug
```

模块输出路径：`spotify-module/build/outputs/apk/release/spotify-module-release.apk`。

### 回归测试

```bash
python3 tests/check_module_boundaries.py --built --variant release
python3 tests/run_settings_ui_regression.py
python3 tests/run_inline_hooks_regression.py
python3 tests/run_render_idle_regression.py
```

完整 CI 检查清单见 [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)。

---

## 发布流程

版本号唯一来源为根目录 [`gradle.properties`](gradle.properties) 中的 `lyriscopeVersionName` 与 `lyriscopeVersionCode`。推送 `v*` 标签会自动触发 [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)，完成测试、签名构建与资产发布：

| 发布产物 | 说明 |
| --- | --- |
| `Lyriscope-Module-<tag>.apk` | 已签名的 LSPosed 模块安装包 |
| `Lyriscope-<tag>-version.json` | 版本校验清单，供应用内更新检查使用 |

---

## 目录结构

```text
shared/          核心共享层：歌词渲染、海报生成、Miuix 设置、多源取词、翻译与备份
spotify-module/  LSPosed 注入层：Spotify 版本映射、Compose 桥接与 Hook 诊断
app/             独立开发调试应用（通知使用权模式，不随 Release 分发）
tests/           模块边界检查与离线回归测试套件
docs/            更新日志与维护排障记录
```

---

## 致谢

本项目在构思、界面风格或歌词数据上参考并受益于以下开源项目与服务：

- [ivLyrics](https://github.com/ivLis-Studio/ivLyrics-Android) — ivLis Studio（原始代码基线）
- [KernelSU-Style-UI-Kit](https://github.com/chenaizhang/KernelSU-Style-UI-Kit) — chenaizhang（Miuix 设置界面风格参考）
- [HyperLyrics Enhanced](https://github.com/juren233/HyperLyrics-Enhanced) — juren233
- [HyperLyric](https://github.com/limczhh/HyperLyric) — limczhh
- 歌词数据：[LRCLIB](https://lrclib.net)、[lrc.red](https://lrc.red)、[LyricsPlus](https://github.com/ibratabian17/lyricsplus)、[Unison](https://github.com/better-lyrics/unison)

## 许可说明

上游 `ivLyrics-Android` 未附带 LICENSE 文件，本仓库同样不主张额外授权。再分发或用于商业用途前，请自行确认原作者的授权条款。歌词、译文等第三方内容版权归各自权利方所有。
