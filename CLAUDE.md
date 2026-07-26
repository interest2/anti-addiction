# 项目记忆

## 项目概述

「防沉迷提醒 APP」（`com.book.mask`，仓库名 anti-addiction / appMask）。基于无障碍服务读取前台包名与页面内容，命中目标 APP + 关键词时用悬浮窗遮挡推荐流，但不遮挡搜索栏等功能；解除遮挡需答题（算术 / 推理 / 英文阅读）或消耗「休闲时刻」免答题次数。悬浮窗提醒文字可由大模型基于用户目标动态生成。

## 技术栈与构建

- 语言/环境：Java 11（源码兼容级），Android minSdk 24（7.0）、target/compileSdk 35，Gradle Version Catalog（`gradle/libs.versions.toml`）。
- 构建 JDK 17、Android SDK 35；Android Studio 直接打开即可。
- 依赖：AndroidX / Material、RecyclerView、CardView、Gson（JSON）、Tencent MMKV（本地配置存储）。
- Maven 走阿里云镜像（见 `settings.gradle`）。release 未混淆加密（`minifyEnabled false`），刻意保持可审计。
- 版本号在 `app/build.gradle` 的 `versionCode` / `versionName`，每次发版手动递增。
- 常用命令：`./gradlew assembleDebug`、`./gradlew assembleRelease`、`./gradlew installDebug`。
- 云端服务端未开源，接口地址在 `constant/CloudConst.java`（`DOMAIN_URL`）。

## 代码结构（app/src/main/java/com/book/mask/）

- `floating/`：核心。`FloatService`（无障碍服务，总协调器）、`AppStateManager`（包名/页面/解禁状态判断）、`FloatingWindowManager`（窗口创建/暂停/恢复/移除，最重，约 690 行）、`WindowSuspensionState`（暂停原因叠加）、`PackageTransition*`（切换决策与时序）。
- `challenge/`：答题解禁。`MathChallengeManager`（编排/校验/回调）、`ChallengeViewController`（界面/焦点/输入法）、`ChallengeQuestionProvider`。
- `config/`：`CustomApp` / `CustomAppManager`（预置+自定义 APP 增删改查）、`Share`（进程内运行时状态，静态字段+Map）、输入法/包名日志管理。
- `personalize/`：MMKV 持久化。`AppSettingsManager`、`SettingsStorage`、`BackupManager`（导出/恢复）、`LeisureTimeManager`/`RelaxManager`（休闲时刻、宽松次数）、`ChallengeSettingsManager`。
- `network/`：`TextFetcher`、`LatestVersionManager`、`DeviceInfoReporter`（上报基础信息），`network/reminder/`（提醒文字多 Provider：官方云端 + OpenAI 兼容，含配置校验/密钥存储/缓存/策略，目前 `REMINDER_PROVIDER_SETTINGS_ENABLED=false` 未开放自定义 Provider UI）。
- `ui/`：`MainActivity` + 各 `*Nav`（首页/目标/设置/详情等分区导航）、`AppCardAdapter`、`SettingsDialogManager`。
- `constant/`：`Const`（时序阈值、开关、常量）、`CloudConst`（接口路径）、`QuestionConst`。
- `lifecycle/`：`AppLifecycleObserver`、`ServiceKeepAliveManager`（运行状态监测辅助，非强保活）。
- `util/`：算术、内容、日期工具。

## 关键机制（务必先读）

`0.docs/AI-关键机制.md` 详尽描述了运行机制，改动 floating/challenge/personalize 前先读。核心要点：

- 前台包名两来源：无障碍窗口事件 + 每 2 秒轮询兜底，统一进 `handleObservedPackage()`。
- 关键词匹配是 `targetWord.contains(nodeText)`（配置词包含节点文字）。微信为特例：命中包名即视为目标页，屏蔽整个 APP。
- 两类「隐藏」本质不同：正式隐藏 `removeView()`（释放资源）；暂停隐藏「暖窗口」用 `alpha=0 + FLAG_NOT_TOUCHABLE + updateViewLayout()` 保留已绘制窗口，切换/复用时原地改写 `layoutParams`（不能替换对象，答题视图持有其引用）。
- `isFloatingWindowVisible` / `Share.isFloatingWindowVisible` 表示「窗口仍挂载并受管理」，不等于用户可见。
- 暂停原因叠加（`WindowSuspensionState`）：`PAGE_TRANSITION` 与 `SYSTEM_UI`，首个出现才物理隐藏，最后一个解除才恢复。
- 大量时序阈值集中在 `Const`（300ms 复核、1.5s/2s 暖窗口保留、200ms 内容防抖等），调时序优先改这里。
- 严格/宽松解禁时长 与 休闲时刻 是两套不同机制，别混。

其余 `0.docs/` 下 `AI-*.md` 为 AI 总结的机制/诊断记录，`0.部分开发过程小结.md` 为开发过程笔记，可作背景。

## 约定

- 除非用户明确要求，本项目功能修改不新增单元测试。
- 代码用英文；文档、注释、commit 可用中文。commit 前缀 `feat:`/`fix:`/`refactor:`/`docs:`/`style:`/`update:`。
- 状态单一数据源：运行时状态集中在 `Share`（进程内）与 MMKV（持久化），别在多处各存一份导致漂移。
- 禁提交敏感文件（密钥、`.env` 等）。
