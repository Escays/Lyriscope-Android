# Bugfix & Maintenance Log

## 2026-11-21 — 播放器页 Lyriscope 卡片未跟随封面取色（仍显示固定纯色/紫色）

### 现象
在歌词页将背景设置为「单色 + 跟随封面（`solid-background` + `SOLID_SOURCE_AUTO`）」后，歌词页背景会按当前专辑封面取色，但 Spotify 播放器页用于进入歌词页的 Lyriscope 卡片背景没有随封面取色变化，仍停留在旧的固定纯色（如默认 `#1e3a8a` 或之前保存的自定义紫色）。

### 根因分析
1. **缺少 `setStableSolidColor` 同步**：`PlayerBackgroundView.onDraw()` 优先绘制 `stableSolidColor`；当其为 `0` 且模式为 `BACKGROUND_MODE_SOLID` 时，会回退到 `drawSolid()` 使用静态 `backgroundSettings.solidColor`。此前仅有 `BaseLyricsActivity.applyStableBackgroundColor()` 为歌词页 `backgroundView` 计算 `ArtworkColorPicker.pick(...)` 并调用 `setStableSolidColor()`，`IvLyricsBridge.NativeCard` 从未调用该方法。
2. **未读取单曲背景覆盖配置**：`NativeCard.render()` 只读取全局 `settings.background`，且仅在 `appliedSettings != settings` 时更新背景配置，忽略了歌词页单曲背景覆盖（`trackBackgroundSettings(trackKey)`）及切歌时的生效配置切换。
3. **补充封面回调未接入**：`IvLyricsBridge.Engine` 中 `onLyricsArtworkLoaded()` 为空实现；当 `NowPlayingService` 尚未提供封面 `Bitmap`、封面改由 `LyricsRepository` 异步拉取时，卡片无法收到该封面并更新取色。
4. **歌词页联动缺口**：`BaseLyricsActivity.applyBackgroundSettings()` 未同步刷新 `applyStableBackgroundColor()`，且 `pictureInPictureBackgroundView` 未同步设置 `stableSolidColor`。

### 修复方案
- **`shared/src/main/java/dev/lyriscope/core/IvLyricsBridge.java`**
  - 在 `NativeCard.render()` 中按当前曲目解析生效背景配置（单曲覆盖优先于全局 `settings.background`），并在显示状态或背景字段变化时更新 `PlayerBackgroundView`。
  - 为 `NativeCard` 增加 `applyStableBackgroundColor()`：在 `BACKGROUND_MODE_SOLID` + `SOLID_SOURCE_AUTO` 下使用 `ArtworkColorPicker.pick(artwork, trackKey)` 计算并缓存每首曲目的稳定取色，调用 `background.setStableSolidColor(color)`；非该模式或隐藏背景时清零为 `0`。
  - 在 `Engine` 中实现 `onLyricsArtworkLoaded()`，缓存异步加载的封面 `loadedArtwork` / `loadedArtworkKey` 并在切歌时清理，通过 `effectiveArtwork()` / `effectiveArtworkKey()` 供 `NativeCard` 使用。
- **`shared/src/main/java/dev/lyriscope/core/BaseLyricsActivity.java`**
  - 在 `applyStableBackgroundColor()` 中同步更新 `pictureInPictureBackgroundView.setStableSolidColor(...)`。
  - 在 `applyBackgroundSettings()` 末尾调用 `applyStableBackgroundColor(...)`，确保切换背景模式或颜色来源时立即生效/清除 `stableSolidColor`。

### 验证
- 执行 `.\gradlew.bat :spotify-module:assembleRelease :shared:testDebugUnitTest`，编译与单元测试全部通过。
- 通过 `adb install -r` 安装 `spotify-module/build/outputs/apk/release/spotify-module-release.apk` 到真机，并执行 `am force-stop com.spotify.music` 以使 LSPosed 加载新模块。

### 维护经验
- **多渲染面需同步背景能力**：`PlayerBackgroundView` 同时被歌词主页面（`BaseLyricsActivity.backgroundView`）、画中画（`pictureInPictureBackgroundView`）和播放器内嵌卡片（`IvLyricsBridge.NativeCard.background`）复用。凡是在 `BaseLyricsActivity` 中对 `PlayerBackgroundView` 新增外部驱动状态（如 `setStableSolidColor`、单曲覆盖等），都应同步排查另外两个宿主。
- **切歌与封面异步到达的缓存策略**：播放器卡片在切歌或补充歌词回调时会高频触发 `render()`，取色必须按 `trackKey` 缓存以避免主线程重复跑 `Palette`；同时允许无封面占位色在真封面到达后升级一次，但不要让已取色的曲目因短暂空封面被回退覆盖。

---

## 2026-11-22 — 按 KernelSU-Style-UI-Kit 的 Miuix 风格重构模块设置页

### 目标与约束
- 参考 `chenaizhang/KernelSU-Style-UI-Kit` 的 Miuix 界面规范（`top.yukonga.miuix.kmp` 色彩体系、无边框 16dp 卡片、无行间分割线、前导单色图标、顶部 Hero 状态卡、胶囊搜索框、Miuix 厚胶囊滑块、底部悬浮 28dp 弹窗与悬浮下拉菜单）。
- 模块运行在 Spotify 进程内，保持纯 Java Android View 实现（不引入 Compose 运行时），并完整兼容 `SettingsUiRegressionTest` 的全部布局与无障碍断言（`brandTitle` 30sp/weight 600/LTR、48dp 最小触控尺寸、开关圆球边界与圆度、下拉单箭头、提供方徽章居中）。

### 核心改动
- **`shared/src/main/java/dev/lyriscope/core/SettingsPalette.java`**
  - 对齐 Miuix 官方 `darkColorScheme` / `lightColorScheme` 与 KernelSU `WarningCard`/`HomeMiuix` 色彩：深色纯黑画布 `#000000` + `#242424` 卡片 + `#277AF7` 主蓝；浅色 `#F7F7F7` 画布 + `#FFFFFF` 卡片 + `#3482FF` 主蓝 + `#F0F0F0` 无边框色调控件；统一成功/警告/错误/信息提示色。
- **`shared/src/main/java/dev/lyriscope/core/SettingsKit.java`**
  - **卡片与导航行**：`group()` 移除行间分割线，新增独立无边框 16dp `card()`；`homeItem()` 按 `SettingsMiuix.kt` `ArrowPreference` 补回无底框 22dp 单色前导图标；新增 `heroStatusCard()` 复刻 `HomeMiuix.kt` `PermissionCardMiuix`（柔和底色 + 右下角裁切大水印图标 + 轻微按压缩放反馈 `attachPressSink`）。
  - **核心控件**：新增 `MiuixSliderDrawable` 与 `styleMiuixSeekBar()`，将 `Range` 滑块重构为 Miuix 26dp 厚胶囊滑块（内嵌白点指示器 + 步进触感反馈）；`button()`/`picker()`/`segmented()`/`note()`/`field()`/`stepper()`/`cards()` 全部重构为无边框 Miuix 色调容器风格（输入框仅聚焦时显示主色边框，警告卡对齐 `WarningCard`）；`MiuixSwitch` 仅在透明封面浮层保留外描边，普通卡片上保持纯粹胶囊外观；`iconButton()` 改为圆形波纹。
- **`shared/src/main/java/dev/lyriscope/core/BaseLyricsActivity.java`**
  - 设置页主内容水平边距收敛为 `12dp`，`TopAppBar` 移除硬分割线并改为大标题与导航标题连续平滑渐变过渡（`onSettingsScrolled`）。
  - 设置首页搜索栏重构为无边框 24dp 胶囊搜索框，搜索结果按分组装入 Miuix 卡片；顶部「当前曲目」升级为 KernelSU 风格 Hero 状态卡，其余分组以无分割线 Miuix 卡片呈现。
  - `showConfirmDialog()` 重构为 Miuix 底部悬浮 28dp 圆角弹窗（居中标题 + 等宽无边框 16dp 按钮）；`showSettingsChoicePopup()` 重构为无边框 16dp 悬浮菜单。
  - 将歌词源卡片、AI 提供方卡片、AI 连接卡片、颜色选择按钮与 OpenAI 连接项从旧版带边框 12dp `surface` 容器统一升级为无边框 16dp Miuix `card`。
- **`shared/src/main/java/dev/lyriscope/core/LocalAiSettingsView.java`**
  - 复用 `SettingsKit.styleMiuixSeekBar()`，使本地 AI 模型质量滑块与其他设置滑块视觉统一。

### 验证
- 执行 `.\gradlew.bat :spotify-module:assembleRelease :app:assembleDebug :shared:testDebugUnitTest`，全部通过。
- 通过 `adb install -r` 安装 `spotify-module-release.apk` 并重启 Spotify。

### 维护经验
- **保留测试契约**：修改设置首页或控件样式时，`SettingsUiRegressionTest` 会严格校验 `settingsHomePage.getChildAt(0)`（30sp / weight 600 / LTR）、`MiuixSwitch` 反射字段 `position`/`thumbScale` 与圆形滑块像素比例、`picker()` 单个右侧图标、`provider` 24dp/34dp 居中徽章，以及所有可交互控件 >= 48dp。
- **Miuix 视觉层次靠色差而非描边**：Miuix 在深色（`#000000` -> `#242424` -> `#333333`）与浅色（`#F7F7F7` -> `#FFFFFF` -> `#F0F0F0`）中均依靠清晰的面层色差和 16dp 圆角区分层级，避免给常规卡片、按钮、下拉框叠加 1dp 描边，以免破坏整体通透感。


---

## 2026-11-22 - 架构优化、歌词摘录海报、快捷手势、备份恢复、靠左对齐与无时间轴歌词体验升级

### 需求与改动总览
本次迭代一次性落地 5 项核心功能与体验优化：
1. **底层与设置页综合优化**：
   - 统一剩余原生 `AlertDialog` 为 Miuix 28dp 底部悬浮弹窗（`MiuixDialogs.java`）。
   - 新增「跟随系统」深浅色模式（`THEME_SYSTEM`）、Android 12+ Monet 动态取色与 5 套预设强调色（`SettingsPalette.java` + 系统设置页）。
   - 新增 LSPosed Hook 健康状态诊断卡片（`SpotifyXposedHooks.java` 上报至 `IvLyricsBridge.HookHealth`，在设置 → 系统中展示宿主版本、Hook 状态与卡片挂载状态）。
   - 封面取色异步化与共享 LRU 缓存（`TrackColorResolver.java`），避免主线程 Palette 量化卡顿，保证歌词页 / 画中画 / 播放器卡片 / 摘录海报色彩完全一致。
   - Android 13+（API 33+）流体背景接入硬件加速 AGSL `RuntimeShader`（`PlayerBackgroundView.java`），旧系统与软件画布自动回退至 Canvas 渐变方案。
2. **歌词摘录海报 + 歌词页快捷手势**：
   - 新增 `LyricPosterRenderer.java`（1080px Canvas 离屏渲染，支持纯色 / 流体 / 黑胶 / 拍立得 4 种风格、3:4 / 1:1 / 9:16 比例、译文/注音/水印开关，通过 MediaStore 直接导出至 `Pictures/Lyriscope` 并支持系统分享，无需在模块 Manifest 声明 `FileProvider`）。
   - 新增 `LyricPosterDialog.java`（Miuix 底部表单，支持 1–6 行多选、实时预览与 5 组封面提取色板切换）。
   - `LyricsView.java` 接入双指缩放（`ScaleGestureDetector`）实时调节原文字号并持久化，配顶部悬浮字号胶囊反馈；歌词页操作栏新增「海报」与「场景预设（沉浸纯享 / 外语学习 / 夜间护眼）」一键切换。
3. **设置与数据一键备份 / 恢复**：
   - 新增 `SettingsBackupManager.java`，通过系统 SAF（`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`）将全部 8 个 `SharedPreferences`（含 AI 配置、歌词源、单曲微调、本地歌词、手动选词、主题外观等）无损导出/导入为类型化 JSON，并支持导出时可选剔除 API Key / Token / Spotify Client Secret 等敏感凭据。
4. **靠左模式下歌词首字与专辑左缘 / 歌名精准对齐**：
   - **根因**：`ComposeAdapter.java` 给 Spotify 已对齐的 Inline 插槽额外叠加了 `8f` 水平 Compose padding；同时 `MainLyricPreviewView.java` 在长行溢出时将 `startX` 右移了 `edgeFadeWidth`（约 20dp），且 `applyEdgeFadeMask()` 在静止/起始位置仍对左侧边缘做透明淡出。
   - **修复**：`ComposeAdapter.java` 内联水平留白改为 `0f`；`MainLyricPreviewView.java` 靠左溢出时固定 `startX = left`，`applyEdgeFadeMask` 仅在文字真正超出左/右边界时才淡出对应侧；`IvLyricsBridge.NativeCard` 调整 `sidePaddingSp(8f)` 与卡片标题对齐；`BaseLyricsActivity.java` 移除独立预览容器多余的 `dp(12)` 横向 padding。
5. **无时间轴歌词（Plain Lyrics）自由浏览与纯白无暗淡显示**：
   - **问题**：手动应用或拉取到无时间轴的纯文本歌词时，所有行均为 `startTimeMs == 0 && endTimeMs == 0`，`LyricsView` 默认仅高亮第 0 行并暗淡其余行，且手动拖动 4 秒后触发 `MANUAL_SCROLL_HOLD_MS` 自动弹回开头，播放进度更新或拖动进度条也会重置滚动位置。
   - **修复**：`LyricsView.java` 在 `setResult()` 中判定 `untimedLyrics = !hasTimedLines(lines)`：
     - 拖动/惯性滚动后不再触发 `MANUAL_SCROLL_HOLD_MS` 与 `scheduleReturnToPlayback()` 回弹，且 `setPosition()` 对无时间轴歌词直接跳过滚动重置与无效重绘。
     - 新增 `LineVisualState.STATIC_ACTIVE`（`highlighted=true, animating=false, completedColorOpacity=1f`）并令无时间轴模式 `inactiveColor = activeColor`，走单次 `canvas.drawText` 快路径将全部歌词（含译文/注音）统一以纯白高亮渲染、无距离暗淡与顶部渐变淡出。
     - 放宽无时间轴可见行渲染窗口（`+4` 行），并使 `activeLineIndex()` 返回当前滚动中心行，方便摘录海报直接选中当前阅读段落。

### 关键文件变更
- 新增：
  - `shared/src/main/java/dev/lyriscope/core/TrackColorResolver.java`
  - `shared/src/main/java/dev/lyriscope/core/MiuixDialogs.java`
  - `shared/src/main/java/dev/lyriscope/core/SettingsBackupManager.java`
  - `shared/src/main/java/dev/lyriscope/core/LyricPosterRenderer.java`
  - `shared/src/main/java/dev/lyriscope/core/LyricPosterDialog.java`
- 修改：
  - `spotify-module/src/main/java/dev/lyriscope/spotify/ComposeAdapter.java`
  - `spotify-module/src/main/java/dev/lyriscope/spotify/SpotifyXposedHooks.java`
  - `shared/src/main/java/dev/lyriscope/core/MainLyricPreviewView.java`
  - `shared/src/main/java/dev/lyriscope/core/PlayerBackgroundView.java`
  - `shared/src/main/java/dev/lyriscope/core/SettingsPalette.java`
  - `shared/src/main/java/dev/lyriscope/core/IvLyricsBridge.java`
  - `shared/src/main/java/dev/lyriscope/core/LyricsView.java`
  - `shared/src/main/java/dev/lyriscope/core/BaseLyricsActivity.java`

### 验证结果
- `./gradlew :spotify-module:assembleRelease :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac :shared:testDebugUnitTest`：`BUILD SUCCESSFUL`（全部编译与单元测试通过）。
- `python tests/check_module_boundaries.py --built --variant release`：`MODULE_BOUNDARIES_PASSED assertions=53`（无模块边界或 Manifest 泄露）。
- 已通过 `adb -s 1ad1e964 install -r` 安装最新签名 `spotify-module-release.apk` 并重启 `com.spotify.music`。

### 经验教训与后续维护注意
- **Java 嵌套类与 `LinkedHashMap.Entry` 遮蔽陷阱**：在匿名 `new LinkedHashMap<K, Entry>(...)` 内部，非限定名 `Entry` 会解析为 `LinkedHashMap.Entry` 而非外层自定义嵌套类，导致 `removeEldestEntry(Map.Entry<K, Entry>)` 签名不匹配；应避免将缓存项命名为 `Entry`（改用 `ColorEntry` 等明确名称）。
- **无时间轴渲染须兼顾快路径与零空转**：`LyricsView.drawTextRow()` 对 `!rendersTimedFill()` 行直接使用 `group.inactiveColor` 绘制。切勿把全部无时间轴行标为 `animating = true`（会触发每帧重绘与特效计算）；使用 `STATIC_ACTIVE (highlighted=true, animating=false, completedColorOpacity=1f)` 并令 `inactiveColor = activeColor`，既能保证纯白无暗淡，又能保持最低 CPU/GPU 占用；同时必须在 `setPosition()` 中短路无时间轴歌词，避免 `manualScrollActive=true` 导致 60Hz 播放回调持续触发无效重绘。
- **模块内图片分享尽量走 `MediaStore`**：LSPosed 模块不得在宿主 Manifest 注入 `FileProvider` 或额外存储权限，通过 `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`（Android 10+）生成的 `content://` Uri 可同时完成相册保存与 `Intent.ACTION_SEND` 分享，严格满足模块边界检查。


---

## 2026-11-22 - 实机反馈精修：海报交互与排版重构、Spotify 9.2 精准对齐、FlowLayout 空白框修复

### 问题与反馈
实机测试后发现 4 处体验与视觉问题：
1. **快捷场景功能鸡肋 & 歌词页按钮过多**：希望删除场景预设，并取消歌词页底部的「海报」按钮，改为类似 Apple Music 的**长按任意歌词行**唤出歌词分享海报页。
2. **歌词摘录海报弹窗与排版问题**：
   - 旧版弹窗将歌词列表 `ScrollView` 嵌套在外层 `ScrollView` 内，导致选择歌词区域无法滚动且高度极小；且位于最底部导致预览图滚出视野。
   - 海报角标印着 `Lyriscope` 而非 `Spotify`，且短歌词字号偏小、四周大面积留白。
3. **靠左模式下专辑下方歌词首字与前奏图标均向左突出约 8dp**：
   - **根因**：经实机 1200×2608@480dpi（400dp 宽）像素测量，Spotify `9.2.0.2053` 播放页 Inline 歌词槽左边界在 **16dp**，而专辑封面、歌名、歌手、进度条左边界统一在 **24dp**（相差 **8dp**）。查阅 `SpotifyMapping.V9_2_0` 发现 `9.2` 的 `inline.padding` 映射为 `null`，导致 `ComposeAdapter` 中的 Compose padding 未生效。
4. **「设置 → 语言与地区」中「强调色」下方出现一块空白黑框**：
   - **根因**：`SettingsKit.FlowLayout.onMeasure()` 强行用 `(AT_MOST, UNSPECIFIED)` 测量所有子 View，忽略了显式 `LayoutParams.width/height`；普通 `View` 圆形色块无固有尺寸，全部被测成 `0×0`，留下一块空白占位。

### 修复方案
- **Apple Music 式长按唤出海报 & 删除场景预设**：
  - `LyricsView.java` 新增 `OnLineLongPressListener`，长按任意歌词行带触觉反馈直接触发海报页并锚定该行；带文化注释的虚线行长按提供「分享歌词海报 / 查看文化注释」二选一菜单。
  - `BaseLyricsActivity.java` 移除 `lyricsActionRow` 中的「海报」「场景」按钮与 `showLyricsScenePresetDialog` / `applyLyricsScenePreset`，同时为竖屏与横屏歌词视图接入长按监听。
- **海报弹窗与渲染器全面重构**：
  - `LyricPosterDialog.java` 改为 88% 屏高 Miuix 底部表单，彻底消除嵌套 `ScrollView`：顶部固定圆角实时预览舞台 + 紧接其下的双标签栏（默认「选择歌词 (N/6)」独占整个下半屏流畅滚动并自动定位至长按句，支持「连续选3句 / 仅当前句」；「海报样式」含风格、画幅、居左/居中、字号档位、封面色板与元素开关）+ 底部固定保存/分享栏。
  - `LyricPosterRenderer.java` 底部角标改为原生 Canvas 绘制的 **Spotify 圆形三弧线标志 + `Spotify` 字样**（右侧可选附带专辑名）；按选中句数与字数自适应大字号（短句可达 104px/1080px），新增居左/居中、紧凑/标准/醒目字号与装饰大引号 `“`，精修纯色/流光/黑胶/浅纸 4 套排版。
- **Spotify 9.2 靠左与前奏图标精准 24dp 对齐**：
  - `IvLyricsBridge.NativeInline` 内置 `8dp` 水平内边距（`ComposeAdapter` 保持 `0f` 不叠加），使 `9.1.80` 与 `9.2.0.2053` 统一落在 `16dp + 8dp = 24dp` 基准线，歌词首字与前奏动效图标均与专辑左缘、歌名、进度条严格垂直齐平。
- **修复 FlowLayout 测量缺陷与强调色色块**：
  - `SettingsKit.FlowLayout.onMeasure()` 改为优先遵循子 View 显式 `lp.width >= 0` / `lp.height >= 0`（`MeasureSpec.EXACTLY`）。
  - `BaseLyricsActivity.java` 改用带居中白勾 `✓` 与显式尺寸的圆形色块（并移除 `ACCENT_PRESETS` 中重复的蓝色项），8 个强调色清晰展示、自动换行且无空白框。

### 验证结果
- `./gradlew :spotify-module:assembleRelease :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac :shared:testDebugUnitTest`：`BUILD SUCCESSFUL`。
- `python tests/check_module_boundaries.py --built --variant release`：`MODULE_BOUNDARIES_PASSED assertions=53`。
- 已安装至 `1ad1e964` 并重启 `com.spotify.music`。

### 经验教训
- **跨 Spotify 版本布局偏移须在共享 View 层收口**：不同 Spotify 版本的 `SpotifyMapping.Inline` 可能将可选 Modifier 字段（如 `padding`）留空为 `null`；涉及与宿主 UI 对齐的固定间距（如 16dp → 24dp 的 8dp 补偿）应放在 `IvLyricsBridge.NativeInline` 而非依赖可选 Compose Modifier 反射，确保多版本一致。
- **自定义 `ViewGroup`（如 `FlowLayout`）测量必须尊重子 `LayoutParams`**：若对固定宽高的子 `View` 强行传 `AT_MOST / UNSPECIFIED`，没有固有大小的纯背景 `View` 会塌缩为 `0×0`；务必按 `lp.width >= 0` 切换为 `EXACTLY`。
- **弹窗内绝不嵌套双层垂直 `ScrollView`**：长列表选择器与顶部实时预览共存时，应使用「顶部固定预览 + 下方 `weight=1` 单层 `ScrollView`（按需分 Tab）」，既不丢预览视野，又保证滚动顺滑。


---

## 2026-11-22 - v1.0.10 发版前精修与全量回归验证

### 修复内容
1. **修复海报弹窗右上角关闭按钮 `×` 视觉左偏 & 标题居左问题**：
   - `SettingsKit.text()` 默认设置 `TEXT_ALIGNMENT_VIEW_START`，在 API 17+ 会覆盖 `Gravity.CENTER`。
   - `LyricPosterDialog.java` 中为标题显式补充 `TEXT_ALIGNMENT_CENTER`，并将右上角关闭按钮改为 32dp 圆形容器内自绘矢量 `×`（基于 `getBounds().exactCenterX()/exactCenterY()`），确保在任意字体与 DPI 下均几何绝对居中。
2. **同步更新 4 处离线源码提取型回归测试脚手架以适配新字段与 Miuix 弹窗**：
   - `tests/RenderIdleRegression.java.in`、`tests/LyricsCenterRegression.java.in`、`tests/run_renderer_overlap_regression.py`、`tests/run_frame_layout_pool_regression.py` 补齐 `untimedLyrics` / `hasTimedLines`；
   - `tests/AiModelPickerRegression.java.in` 补齐 `MiuixDialogs` / `SettingsKit` 测试桩。
3. **升级版本至 `1.0.10` (`1010`)，重写 `README.md` 并生成 `docs/release-notes-v1.0.10.md` 准备发布 GitHub Release `v1.0.10`**。
