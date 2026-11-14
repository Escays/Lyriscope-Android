# 回归脚本清理：现状与下一步

这些脚本是上游留下的、按"从真实源码里抠出方法体再断言"的方式写的。产品代码一直在动，脚本里抠出来的依赖没跟上，于是**跑起来就红，但红的原因不是产品坏了**——这会让每次真回归都被噪声淹没。

本轮先把能确定的修掉，剩下的写清下一步，避免下次再重新考古。

## 现状

| 脚本 | 现在的状态 | 原因 |
| --- | --- | --- |
| `run_lyrics_center_regression.py` | ✅ 通过（49207 条断言） | 读取 `git show` 输出时用系统默认编码解码，Windows 上是 GBK，遇到 UTF-8 源码直接抛 `UnicodeDecodeError`。已统一 `encoding="utf-8", errors="replace"`。 |
| `run_render_idle_regression.py` | ⚠️ 能编译能跑，但内部 `PageSlideRegression` 断言失败 | ① 脚本还在调用已删除的 `bluetoothSyncOffsetMs`（已修）；② 传给 java 的 classpath 用 `:` 拼接，Windows 上应为 `;`（已改用 `os.pathsep`）；③ 剩余失败是 `browser login open, back-close: same handle clicks...`，属于**另一套行为模型**，需要判断是实现漂移还是期望过期。 |
| `run_renderer_overlap_regression.py` | ⚠️ 编译失败 | 编译期缺 `android.view`（已补 `android_jar()`）；仍缺 fixture 依赖的成员：`annotationLongPressListener`、`pressedTarget`、`pressedAnnotationLine()`，以及 `performHapticFeedback`（fixture 没继承 `View`）。抠方法体时没把字段/辅助方法一起抠出来。 |
| `run_settings_ui_regression.py` | ❌ `check 1284` | 期望模型写死"只有 `track!=null && base 有行 && 无任何 loading` 时重新生成按钮才可用"，而生产里的 `updateLyricsRegenerateActions()` 已改。**这里是产品行为变更了，脚本没跟上**，需要确认新行为是否就是想要的。 |

## 下一步（建议顺序）

1. **`settings_ui` 的 check 1284**：先确认"重新生成"按钮当前的启用条件是不是有意的（比如只有存在译文/注音待重生成时才可用）。确认后把 fixture 里的 `enabled` 期望改成生产逻辑，而不是改产品。改完这个脚本能同时守住 22 个语言 × 加载状态，价值最高。
2. **`renderer_overlap`**：把 `annotationLongPressListener`、`pressedTarget`、`annotationLongPressFired` 三个字段和 `pressedAnnotationLine()` 加入抽取清单；fixture 若继承不到 `View`，把 `performHapticFeedback` 换成空实现。目标是让它重新编译起来。
3. **`render_idle` 的 PageSlide 部分**：先读 `PageSlideRegression` 那条断言的原意（Spotify 浏览器登录的返回栈），再决定是实现回退还是脚本更新。这条需要人判断，不适合机械改。
4. **顺手统一**：所有脚本的 `subprocess` 都显式 `encoding="utf-8", errors="replace"`；classpath 一律走 `regression_runtime.classpath()`，不要手拼。这两条能一次性消灭一整类"在 Windows 上莫名红"。

## 原则

- 脚本红了先看**是产品变了还是脚本旧了**，两种处理方式完全不同。
- 抽取式脚本（抠真实方法体）必须在抽取清单里连字段和辅助方法一起带上，否则产品一加字段编译就崩。
- 不要在脚本里写死"某个功能必须存在"的断言（如蓝牙偏移）；功能被删时，脚本要跟着删，而不是让整个套件长期红着。
