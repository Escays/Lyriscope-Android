# 适配一个新的 Spotify 版本

Spotify 每次发版都用 R8 重新混淆一遍 `p.*` 类名。所以我们记住的不是「类名」，而是「这家代码长什么样」：静态卡片渲染器有 10 个参数、第 9 个是 composer、第 10 个是 `int`。名字会变，形状不会。

模块里只有一处写名字：`src/main/java/dev/lyriscope/spotify/SpotifyMapping.java` 的一张表。钩子代码全部向它取类名，`boolean oldVersion` 这种全流程传参已经删掉了。

| 层 | 文件 | 职责 |
| --- | --- | --- |
| 表 | `SpotifyMapping.java` | 一个版本一行，按功能分组（card / compose / availability / presence / metadata / inline / order / video / karaoke） |
| 预检 | `SpotifyMappingCheck.java` | 安装前逐个解析锚点，缺什么就在一条日志里点名 |
| 消费者 | `SpotifyXposedHooks` 等 10 个文件 | 只读表，不再出现 `"p.xxxx"` 字面量 |

一个分组是 `null`，表示这个版本没有该功能（9.1.42 没有内嵌歌词行、卡片排序、视频开关和原生 SingAlong）。消费者跳过 null，而不是判断版本。

## 一、拿到 APK，先记指纹

```
sha256sum Spotify-<version>.apk
```

`versionName` 以 `adb shell dumpsys package com.spotify.music | grep versionName` 或 `aapt2 dump badging` 为准；不确定就装到手机上在「关于」里看。把版本号和 SHA-256 加到 `INLINE_MAPPING.md` 顶部——以后回查「这版到底对过什么」全靠它。

## 二、用探针找候选类名

`tools/mapping_probe.py` 直接读 APK 里的 DEX，不需要 jadx / apktool：

```sh
# 每个预设是什么形状
python spotify-module/tools/mapping_probe.py anchors

# 逐个预设跑，--pkg p 把结果限制在 Spotify 的混淆包
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p anchor card-renderer
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p anchor availability
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p anchor compose-interop
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p anchor presence-event
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p anchor order-filter
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p anchor track-gate

# 字符串是最好的交叉验证：资源键、URI 前缀、协议字段
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p anchor card-tag
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p strings npv_lyrics_toggle
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p strings key_lyrics_on_npv_visible
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p strings has_lyrics

# 形状太宽时先定 Composer：每个 composable 都以 (…, Composer, int[, int]) 结尾，
# 出现频率最高的那个 p.xxx 就是 Composer，第二名通常是 Modifier
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p composer

# 再用 Composer 收窄卡片渲染器（9 个参数 + Composer + int，共 10 个）
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p compose-card p.yyyy

# 看某个类的方法和字段，确认字段序号（a/b/c/e/f）没变
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p class p.abcd

# 任意形状查询
python spotify-module/tools/mapping_probe.py --apk new.apk --pkg p shape "static ArrayList(*,Set)"
```

形状语法：`<static|instance|ctor|any> <返回类型>(<参数>,…)`，`*` 是通配。别名有 `void int boolean float long`、`ContextTrack String Object List Set ArrayList Map Maybe Observable Flowable Function1`；也可以直接写完整描述符 `Lp/abcd;`。

工具会跳过 `$r8$lambda$` 之类编译器生成的方法——它们不会承载你的钩子。

## 三、填表

复制最接近的一行改字段，不要新增 `oldVersion` 分支：

```java
private static final SpotifyMapping V9_1_9x = new SpotifyMapping(
        "<versionName>",
        "p.xxxx",                                              // Kotlin Unit 单例
        new Card("p.xxxx", "a", "p.xxxx"),                     // 渲染器 / 方法名 / composer
        new Compose("kotlin.jvm.functions.Function1", "p.xxxx", "p.xxxx", "b", "p.xxxx"),
        new Availability("p.xxxx", "d", null, "p.xxxx", "j", "p.xxxx", "p.xxxx"),
        new Presence("p.xxxx", "p.xxxx", "p.xxxx", 5, "p.xxxx"),
        new Metadata("p.xxxx", "p.xxxx", "p.xxxx", "b"),
        new Inline("p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx",
                "c1", "e", "p.xxxx", "p.xxxx", "p.xxxx"),
        new Order("p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx", "c"),
        new Video("p.xxxx", "p.xxxx", "p.xxxx", "a", "getView"),
        new Karaoke("p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx",
                "p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx", "p.xxxx"));
```

再加进 `BUILDS`。每个字段的语义写在 `SpotifyMapping` 的注释里，行为契约写在 `INLINE_MAPPING.md`。

## 四、看预检日志

装模块、重启 Spotify，logcat 里找一个 TAG：

```
Lyriscope: Native lyrics hooks installed for Spotify 9.1.9x
Lyriscope: Mapping gaps for 9.1.9x: [inline.menu, karaoke.settings]
```

有 gaps 不可怕，那正是还要回去补的字段名。全空说明表填完了。如果版本号不在表里，日志会是：

```
Lyriscope: Unsupported Spotify 9.1.9x; mapped builds [9.1.80.2221, 9.1.42.2058]
```

## 五、回归

全是 JVM 测试，不需要设备（先 `./build.sh`，脚本要读已编译的 class）：

```sh
python tests/run_inline_gate_regression.py
python tests/run_card_presence_regression.py
python tests/run_inline_hooks_regression.py
python tests/run_inline_lifecycle_regression.py
python tests/run_module_hook_cache_regression.py
python tests/run_module_metadata_fanout_regression.py
python tests/run_karaoke_controller_regression.py
python tests/check_module_boundaries.py
```

注意这些脚本自己会合成 `p.*` 桩类。如果你换了受支持的版本，桩类里写死的旧名字会让它们的语义对不上号——那不是表填错了，是测试夹具要同步。改夹具时保持断言（行为）不变，只换名字。

## 五之二、离线审计映射表

填完表先别装手机，拿 APK 直接验一遍——`tests/run_mapping_audit.py` 会把 `SpotifyMapping.java` 解析出来，按 `SpotifyMappingCheck` 同样的规则在 DEX 里逐个解析锚点：

```sh
python tests/run_mapping_audit.py --apk Spotify-<version>.apk
python tests/run_mapping_audit.py --apk Spotify-<version>.apk --version 9.2.0.2053
```

输出到 `FAIL` 的每一行就是还要回去补的锚点，和实机上 `Mapping gaps` 日志一一对应，但不用装包、不用重启、不用连设备。

## 六、实机验证

按 `tests/EMBEDDED_INLINE_SMOKE.md` 过：卡片出现并渲染歌词、整页、当前播放歌曲设置、暂停/切歌/拖进度、视频背景、内嵌那行歌词（只有支持的版本才有）。构建通过和夹具通过不代表真机可用。

## 七、收尾

- `spotify-module/README.md` 的支持版本那一行
- `INLINE_MAPPING.md` 顶部的版本与 SHA-256，以及新版本漂移过的锚点
- `SpotifyMapping.versions()` 自动跟着 `BUILDS` 走，不用手改

## 9.2.0.2053 实测记录：跨版本线时「形状」也会变

9.1.80.2221 → 9.2.0.2053 不是一个补丁版本，`p.*` 全部重排（这在预期内）。真正的信息是**三个锚点连形状都变了**：

| 现象 | 9.1.80 | 9.2.0.2053 | 应对 |
| --- | --- | --- | --- |
| AndroidView 的 lambda 参数 | `kotlin.jvm.functions.Function1` | `p.x340`（R8 去虚化出的单方法接口，`invoke(Object)Object`） | 形状查询要先用 `composer` 定 Composer，再按 `(lambda, Modifier, lambda, Composer, int, int)` 找 |
| 卡片渲染器 | 独占类 `p.g5a0`，方法 `a` | 并入 40 方法的大类 `p.pwk1`，方法 `c` | 用 `#LyricsCardTag` 字符串反查 owner，再按位置签名确认 |
| 方法名 | 渲染器 `a`、AndroidView `b` | 渲染器 `c`、AndroidView `a` | `Card.renderer` / `Compose.renderer` 必须进表，不能写死 |

位置签名仍然一一对应，这是能快速确认的关键：`(调色板, 同型×4, Modifier, 同型, lambda, Composer, int)`。

已映射（`tests/run_mapping_audit.py` 18 个锚点全过）：

```
card        p.pwk1.c     compose  p.x340 / p.ozm0 / p.lo5.a / p.n67
availability p.t0g0.d    state p.lsf0.j + a:p.gch   model p.xrf0   card p.wep0
presence    p.ne00(5)    section p.ofp0   order p.sfp0.c(state p.m8c1)
inline      p.kpc0.g     model p.yyf0   composer p.y4k   unit p.l5i1   modifier p.nyl.a
```

内嵌行只映射了「槽位」：渲染器、模型、Composer、修饰符、Unit 都在表里，但它的两个可见性门控（9.1.80 的 `cp5` / `jm7`）和原生 Lyrics On/Off 菜单开关还没有重新锚定，填 `null`。槽位只会在宿主本来就要显示这一行时被组合，所以未映射门控不会把行藏起来；代价是宿主自己的开关（例如 Canvas、车载、DJ 混音）暂时管不到这行。`metadata`（ISRC 预解析）、`video`（音视频切换）、`karaoke`（SingAlong）同样还是 `null`。

## 9.2 暴露的两个通用陷阱

这两个不是「9.2 特有」，下次换版还会遇到，已经用测试锁住：

**一、Composer 的声明类型 ≠ 运行期类型。** 卡片渲染方法把第 4 个参数声明成 Composer **接口** `p.y4k`，运行时传进来的是实现类 `p.vd40`。旧代码用 `composer.getClass()` 反射找方法，9.1.80 因为 R8 把参数类型收紧成实现类碰巧能用，9.2 一升级就 `NoSuchMethodException`。规则：**永远用表里声明的类型去找方法**（`mapping.card.composer` / `inline.composer`），不要用 `getClass()`。`tests/run_compose_interop_regression.py` 用「接口 + 实现」的合成宿主锁住这条。

**二、槽位高度由我们的 Modifier 决定，而缓存修饰符可能带固定高度。** 9.2 的内嵌行是 `EXACTLY 72px`（24dp）——不是父容器限制，而是随手挑的缓存修饰符 `Modifier.height(24.dp)` 造成的，结果译文被裁掉。判断方法：把 `Compose.aspect`（1:1）临时塞进去，如果高度变成正方形，说明高度是我们的修饰符说了算，继续换候选（`p.nyl.a` 会随内容自适应，`p.g880.a` 是 `fillMaxSize` 会撑满一屏）。

## 新增的三个探针命令

适配内嵌行这类「一个类里塞了很多 composable」的代码时，类级字符串只能把范围缩到几十个方法，还需要落到方法：

```sh
# 谁在自己的方法体里引用了这个字符串（找语义标签、偏好键的精确方法）
python spotify-module/tools/mapping_probe.py --apk new.apk refs lyrics_line

# 谁持有这个类型的静态字段（找 Kotlin Unit、Modifier 之类的单例）
python spotify-module/tools/mapping_probe.py --apk new.apk statics p.ozm0

# 哪些类是「自己持有自己实例」的 Kotlin object（常量单例最稳的形态）
python spotify-module/tools/mapping_probe.py --apk new.apk singletons
```

`class` 查询现在也会打印字段类型（`a:int`、`b:java.lang.Object`），因为分支标志和它的载荷靠类型区分。


## 已知的硬编码，下次适配要手动看

这些地方故意没有进表，改表不会覆盖它们：

- **字段名**：`SpotifyInlineLyrics` 的 `a/b/c/d/e` 分支与布尔字段、`SpotifyVideoButton` 的 `c/e/f`、`SpotifyKaraokePlayback.Binding` 的 `a`。字段顺序由 R8 决定，`SpotifyMappingCheck` 只校验类型（例如 offline 必须是 `boolean`），名字要你看 `class` 查询确认。
- **Kotlin 覆写的方法名**：`invokeSuspend`、`apply` 这类继承自库接口的方法 R8 不会改名，所以它们以字面量出现在代码里是安全的；被改名的 `c1`/`e`/`c` 已经进了表。
- **`SpotifyKaraokeEligibility` 的内部字段**：`provider.c`、`mapper.a`、`mapper.c`、`repository.d`、歌词响应 `.a`/`.g`。SingAlong 是可选功能，解析失败只打一条 `Native karaoke support mapping unavailable`，不影响歌词。

## 为什么不能靠「版本号包含判断」

`9.1.9x` 和 `9.1.80` 的类名没有继承关系，混淆结果与版本号无相关性。任何 `startsWith("9.1.")` 式的猜测都只会让钩子挂到错误的类上，然后在运行期抛异常——比明确拒绝更难排查。
