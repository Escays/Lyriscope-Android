## Lyriscope for Spotify 1.0.8

这一版三件事：**跟上 Spotify 9.2**、**修好 Perplexity**、**换了正式签名**。

### 装之前先看这条：签名变了，要卸载重装

1.0.1–1.0.6 的包一直是 Android 调试证书签的（私钥随 SDK 分发，密码就是 `android`）。这版起换成正式的发布密钥，所以**旧版不能直接覆盖**：

1. 先卸载旧的 Lyriscope（模块内设置会一起清掉）
2. 装这一版
3. LSPosed 里重新勾选作用域 Spotify，强制停止 Spotify 再打开

这是一次性的。以后所有版本都用这把密钥，可以直接覆盖升级。

### Spotify 9.2.0.2053

Spotify 每次发版都会把 `p.*` 类名重新混淆一遍，所以模块以前是「按版本写死一批类名」，每适配一版就要在十几个文件里改字面量，还容易漏。现在换了个做法：**类名集中到一张表**（`SpotifyMapping`），一个版本一行、按功能分组，钩子代码只向表取名字，不再出现 `p.xxxx`。

- 已收录：9.2.0.2053、9.1.80.2221、9.1.42
- 某个版本没有的功能，表里就是 `null`，调用方跳过它，而不是靠 `boolean oldVersion` 分支
- 安装前会逐个解析锚点（`SpotifyMappingCheck`），缺什么就在一条日志里点名，不会静默失效
- 9.2.0 里卡片渲染器被 R8 合并进了一个 lambda 容器类，模型类也换了；内嵌槽位所在的修饰符原来写死了 `24.dp` 高度，会把译文行裁掉，现在按内容测量

9.2.0 的两个可见性门槛和原生 Lyrics 开关仍然没有映射，所以**预览跟随 Spotify 自己的决定**去组合那个槽位，模块不再单独判断 Canvas / 车机 / 混音。

### Perplexity 换到 Agent API

Perplexity 的 Sonar Chat Completions 已于 2026-09-27 停服。之前选了 Perplexity 的用户，翻译和歌曲研究其实是**必然失败**的，只是报错看起来像网络问题。

- 请求改走 `/v1/agent`（`input` + `max_output_tokens` + `store:false`，联网靠 `web_search` 工具）
- 流式返回改读 typed 事件（`response.output_text.delta` / `response.completed`）——注意它失败时也返回 HTTP 200，靠 `status` 判断
- 模型列表改从 `/v1/models` 取，删掉了内置的 `sonar*` 目录和那条「使用内置模型列表」的提示
- 已经存下来的 `sonar-pro` 这类旧 id 会自动归一成 `perplexity/sonar`，不用重选
- 搜索结果里的 `[web:n]` 引用标记会被剔掉，不再混进歌词

其它提供商不受影响。

### 省电：暂停时不画没变化的帧

歌词浮层是常驻界面，之前的播放时钟每个 vsync 都跑一遍，即使画面完全没变。这一版：

- 暂停页改成 500ms 问一次（Spotify 原生卡片在可见时追帧、隐藏时 250ms 一次）
- 新增「已绘制帧复用」：只要影响画面的输入没变，就沿用上一帧，不重绘
- 进度条末端移动不到半个像素就不重绘；进度标签同样门控
- 暂停的唱片不再重绘，模糊封面漂移降到 12fps

帧复用的问题是「万一少算了一个输入，就会有一行歌词停在旧状态」。合成测试和编译都过了，但真正的验收是手机上滑一遍：暂停、拖动、跳转、切歌各看一次。

### 下载

| 文件 | 是什么 |
| --- | --- |
| `Lyriscope-Spotify-v1.0.8.apk` | LSPosed 模块。装完在 LSPosed 里启用、作用域勾选 Spotify |
| `Lyriscope-Android-v1.0.8-release.apk` | 独立应用。不需要 LSPosed，桌面上有图标，靠通知使用权 + 悬浮窗权限把歌词叠在其他应用上层；设置与缓存跟模块那份互相独立 |

### 校验

```
sha256  e0db20b666827986e71e4284bd05b56cfd79f6c2a13516ac7e07945eef81a15e  Lyriscope-Spotify-v1.0.8.apk
sha256  9c13c79cfd75abbbbe904f691191b9f1cb5a3c0f401b0bea77dd749d0cd7bc03  Lyriscope-Android-v1.0.8-release.apk
```

两包同一把密钥，证书 SHA-256 指纹：`61426a819a74a2b3f6cf5ce112b2a45d4f6b12376d584531df07f98ef27c811d`。
