# Lyriscope v1.0.10

- 版本：`1.0.10` (`1010`)
- 上一版：`v1.0.9`
- 对比：https://github.com/Escays/Lyriscope-Android/compare/v1.0.9...v1.0.10

## 中文

### 概述

这一版带来 **Apple Music 式长按分享歌词海报**、**KernelSU 风格 Miuix 界面深度升级**、**一键备份与恢复**、**无时间轴歌词自由阅读**，并修复了播放器卡片封面取色失效、Spotify 9.2 靠左对齐偏移等多项实机问题。

### 新功能

- **长按歌词分享海报**：在竖屏或横屏歌词页长按任意歌词行即可呼出分享海报页（带文化注释的虚线句弹出「分享海报 / 查看注释」二选一）
  - 顶部固定实时预览，预览下方无嵌套流畅滚动选词（支持 1–6 句多选、「连续选3句 / 仅当前句」快捷按钮，自动定位到长按句）
  - 4 种海报风格（纯色 / 流光 / 黑胶 / 浅纸）、3 种画幅（3:4 / 1:1 / 9:16）、居左/居中、紧凑/标准/醒目字号、可选装饰引号与封面色板
  - 底部自带原生矢量 **Spotify 角标** 与专辑名，支持一键保存相册（`Pictures/Lyriscope`）或直接系统分享
- **歌词页双指缩放字号**：在歌词页双指捏合实时缩放原文字号并自动持久化，顶部浮现字号胶囊反馈
- **一键备份与恢复**：在「设置 → 权限与数据」通过系统文件选择器将全部 8 份配置与本地数据（设置项、单曲同步偏移与背景覆盖、本地歌词、手动选词、外观主题等）导出/恢复为 JSON，导出时支持可选剔除 API Key / Token 等敏感凭据
- **主题模式、Monet 动态取色与强调色**：设置页支持「跟随系统 / 浅色 / 深色」、Android 12+ Monet 动态取色与 8 套预设强调色
- **Hook 健康状态诊断卡**：在「设置 → 语言与地区」顶部实时展示 Spotify 宿主版本、映射匹配状态与注入健康度
- **Android 13+ AGSL GPU 流体背景**：API 33+ 硬件画布启用 `RuntimeShader` 流体渲染，旧版本与软件画布自动回退

### 优化与修复

- **修复播放器 Lyriscope 卡片忽略封面取色始终显示紫色的问题**：同步接入封面取色（`SOLID_SOURCE_AUTO`）、单曲背景覆盖与画中画色彩，并将取色量化异步化 + 共享 LRU 缓存（`TrackColorResolver`）
- **修复靠左模式下歌词首字与前奏动效图标未对齐专辑左缘 / 歌名的问题**：统一在 `NativeInline` 补齐 Spotify 9.2 的 `8dp` 水平内边距（对齐 24dp 基准线），并修正长行溢出时的起始位置与边缘淡出逻辑
- **优化无时间轴（Plain）歌词体验**：纯文本歌词统一以纯白高亮呈现、无距离暗淡与顶部淡出，手动拖动阅读不再 4 秒后自动弹回开头
- **Miuix 视觉全面统一**：参照 KernelSU-Style-UI-Kit 精修设置页层级与色彩，统一剩余系统弹窗为 28dp 圆角 Miuix 底部悬浮卡片，修复 `FlowLayout` 测量导致强调色空白框的问题以及海报关闭按钮居中对齐

## English

### Summary

This release introduces **Apple Music-style long-press Lyric Quote Posters**, a **KernelSU-style Miuix settings overhaul** (System theme, Android 12+ Monet, preset accent colors, Hook Health card), **one-tap JSON Backup & Restore**, **pinch-to-zoom lyrics typography**, **untimed plain-lyrics free reading**, and **Android 13+ AGSL GPU fluid backgrounds**, along with key fixes for player card cover-tinting and Spotify 9.2 left alignment.

### What's New

- **Long-Press Lyric Quote Poster**: Long-press any lyric line (portrait or landscape) to open the poster sheet directly at that line (annotated lines offer a choice between Poster and Song Meaning)
  - Pinned live preview above a smooth non-nested 2-tab deck (1–6 line picker auto-scrolled to the pressed line + Style & Layout controls)
  - 4 styles (Solid / Fluid / Vinyl / Paper), 3 aspect ratios (3:4 / 1:1 / 9:16), left/center alignment, compact/normal/large typography, decorative quotes, cover palette swatches, and a crisp native vector **Spotify** footer badge
  - Direct MediaStore export (`Pictures/Lyriscope`) and system share sheet with zero manifest leaks
- **Pinch-to-Zoom Lyrics Typography**: Pinch directly on the lyrics page to resize original lyric text with live pill feedback and automatic persistence
- **One-Tap Settings & Data Backup / Restore**: Export and restore all 8 SharedPreferences stores (settings, per-track overrides, local lyrics, manual picks, theme choices) via Android SAF JSON files, with optional stripping of API keys and tokens
- **System Theme, Monet & Accent Key Colors**: Follow-system night mode, Android 12+ Monet dynamic color, and 8 preset Miuix accent colors
- **Hook Health Diagnostics Card**: Live Spotify host version, mapping match, and hook mount summary inside Settings
- **Android 13+ AGSL Fluid Background**: Hardware-accelerated `RuntimeShader` fluid shader with seamless fallback on older Android versions

### Improvements & Fixes

- **Fixed Player Lyriscope Card cover-tinting**: Now honours `SOLID_SOURCE_AUTO` cover extraction and per-track overrides consistently across full lyrics, PiP, and the player card, backed by an async shared LRU `TrackColorResolver`
- **Fixed Left-Aligned Inline Lyrics & Prelude Icon Alignment on Spotify 9.2**: Applied the missing 8dp horizontal inset in `NativeInline` (aligning the 16dp slot to Spotify's 24dp album/title/seekbar edge) and eliminated false left-edge fade and horizontal shift on overflowing left-aligned lines
- **Improved Untimed / Plain Lyrics Reading**: All lines render in uniform bright white without distance dimming, and manual scrolling never snaps back to line 0 after 4 seconds
- **Miuix Polish**: Refined settings surfaces and bottom floating dialogs (28dp), fixed `FlowLayout` exact-dimension measurement for accent swatches, and pixel-centered the poster close button

## 安装 / Install

- Module: `Lyriscope-Module-v1.0.10.apk` — enable the Spotify scope in LSPosed, then force-stop Spotify.
- 签名与此前版本相同，可直接覆盖升级。Same signing key as v1.0.9; upgrade in place.
