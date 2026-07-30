# 项目记忆

## 项目概述

「防沉迷提醒 APP」（`com.book.mask`，仓库名 anti-addiction / appMask）。通过无障碍服务读取前台包名与页面内容，命中「目标 APP + 关键词」时以悬浮窗遮挡推荐流，保留搜索栏等正常功能；解除遮挡需答题（算术 / 推理 / 英文阅读）或消耗「休闲时刻」免答题次数。悬浮窗提醒文字可由大模型基于用户目标动态生成。

## 技术栈与构建

- 源码兼容 Java 11；Android minSdk 24（7.0）、target/compileSdk 35；依赖经 Gradle Version Catalog（`gradle/libs.versions.toml`）管理。
- 构建需 JDK 17、Android SDK 35，Android Studio 直接打开即可。
- 核心依赖：AndroidX / Material、RecyclerView、CardView、Gson、Tencent MMKV（本地配置存储）。
- Maven 走阿里云镜像（`settings.gradle`）；release 不混淆（`minifyEnabled false`），刻意保持可审计。
- 版本号在 `app/build.gradle`（`versionCode` / `versionName`），每次发版手动递增。
- 常用命令：`./gradlew assembleDebug` / `assembleRelease` / `installDebug`。
- 服务端未开源，接口域名见 `constant/CloudConst.java`（`DOMAIN_URL`）。

## 代码结构（`app/src/main/java/com/book/mask/`）

- `floating/`：核心。`FloatService`（无障碍服务，总协调器）、`AppStateManager`（包名 / 页面 / 解禁状态判断）、`FloatingWindowManager`（窗口创建 / 暂停 / 恢复 / 移除，最重）、`WindowSuspensionState`（暂停原因叠加）、`PackageTransition*`（切换决策与时序）。
- `challenge/`：答题解禁。`MathChallengeManager`（编排 / 校验 / 回调）、`ChallengeViewController`（界面 / 焦点 / 输入法）、`ChallengeQuestionProvider`。
- `config/`：`CustomApp` / `CustomAppManager`（预置 + 自定义 APP 增删改查）、`Share`（进程内运行时状态，静态字段 + Map）、输入法 / 包名日志管理。
- `personalize/`：MMKV 持久化。`AppSettingsManager`、`SettingsStorage`、`BackupManager`（导出 / 恢复）、`LeisureTimeManager` / `RelaxManager`（休闲时刻、宽松次数）、`ChallengeSettingsManager`。
- `network/`：`TextFetcher`、`LatestVersionManager`、`DeviceInfoReporter`（上报基础信息）；`reminder/` 为提醒文字多 Provider 体系，按职责分包：`config/`（Provider 配置校验、密钥存储、预设目录）、`content/`（文本缓存与策略）、`provider/`（官方云端 + OpenAI 兼容 Provider 与 HTTP 客户端），根目录为 `ReminderTextRepository` 等契约。自定义 Provider UI 由 `Const.REMINDER_PROVIDER_SETTINGS_ENABLED` 控制（当前开启）。
- `ui/`：`MainActivity` + 各 `*Nav`（首页 / 目标 / 设置 / 详情等分区导航）、`AppCardAdapter`、`SettingsDialogManager`。
- `constant/`：`Const`（时序阈值、开关、常量）、`CloudConst`（接口路径）、`QuestionConst`。
- `lifecycle/`：`AppLifecycleObserver`、`ServiceKeepAliveManager`（运行状态监测辅助，非强保活）。
- `util/`：算术、内容、日期工具。

## 关键机制（改动前务必先读）

改动 `floating` / `challenge` / `personalize` 前，先读 `0.docs/AI-关键机制.md`。核心要点：

- 前台包名有两个来源：无障碍窗口事件 + 每 2 秒轮询兜底，统一汇入 `handleObservedPackage()`。
- 关键词匹配为 `targetWord.contains(nodeText)`（配置词包含节点文字）；微信为特例，命中包名即视为目标页，屏蔽整个 APP。
- 两类「隐藏」本质不同：正式隐藏 `removeView()` 释放资源；暂停隐藏「暖窗口」以 `alpha=0 + FLAG_NOT_TOUCHABLE + updateViewLayout()` 保留已绘制窗口，复用时原地改写 `layoutParams`（不可替换对象，答题视图持有其引用）。
- `isFloatingWindowVisible` / `Share.isFloatingWindowVisible` 表示「窗口仍挂载并受管理」，不等于用户可见。
- 暂停原因叠加（`WindowSuspensionState`）：`PAGE_TRANSITION` 与 `SYSTEM_UI`，首个出现才物理隐藏，最后一个解除才恢复。
- 时序阈值集中在 `Const`（如 300ms 复核、1.5s / 2s 暖窗口保留、200ms 内容防抖），调时序优先改这里。
- 严格 / 宽松解禁时长 与 休闲时刻 是两套独立机制，勿混。

`0.docs/` 下其余 `AI-*.md` 为机制 / 诊断记录，`0.部分开发过程小结.md` 为开发笔记，均作背景参考。

## 约定

- 除非明确要求，功能修改不新增单元测试。
- 代码用英文；文档、注释、commit 可用中文，commit 前缀 `feat:` / `fix:` / `refactor:` / `docs:` / `style:` / `update:`。
- 状态单一数据源：运行时状态集中于 `Share`（进程内）与 MMKV（持久化），勿多处各存一份导致漂移。
- 禁止提交敏感文件（密钥、`.env` 等）。
