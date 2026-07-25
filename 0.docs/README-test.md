<div align="center">
  <img src="./app/src/main/ic_launcher-playstore.png" width="120" alt="防沉迷提醒图标">
  <h1>防沉迷提醒</h1>
  <p><strong>用一层恰到好处的阻力，减少无意识刷推荐流。</strong></p>
  <p>
    <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 7.0+"></a>
    <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&amp;logoColor=white" alt="Java 11"></a>
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="MIT License"></a>
  </p>
</div>

## 项目简介

防沉迷提醒是一款开源的安卓 APP，对各休闲 APP 用悬浮窗精细化遮挡推荐内容，但顶部搜索栏、底部其他菜单不做限制。  
即保留了“搜索引擎”的功能同时，规避了漫无目的的沉迷。 
想关闭悬浮窗就得做一道难度适中的算术题（也提供推理题）。

**适用范围**  
小红书、知乎、抖音、B 站……，预置支持 6 个APP，还可手动添加其他 APP。  

> [!IMPORTANT]
> 本项目需要“显示在其他应用上层”和“无障碍服务”权限才能工作。启用前请阅读下方的[权限、隐私与联网说明](#权限隐私与联网说明)。

## 核心功能

- **推荐流遮挡**：根据应用包名和页面关键词识别目标页面，并显示可配置大小的悬浮遮罩。
- **答题后解禁**：支持算术题、推理题；算术题可调整难度。
- **严格 / 宽松模式**：可为不同应用设置单次解禁时长，以及宽松模式的每日可用次数。
- **逐应用配置**：独立控制监测开关、关键词、提示文字来源和悬浮窗大小。
- **自定义应用**：除预置应用外，可通过应用名称、包名和页面关键词添加其他 Android 应用。
- **目标提醒**：支持设置目标标签、目标日期和日常提醒，让悬浮窗展示更具针对性的提示。
- **故障排查工具**：提供键盘白名单、包名日志和悬浮窗状态重置等辅助功能。

## 工作原理

```text
页面发生变化
    ↓
无障碍服务读取当前应用与窗口内容
    ↓
匹配已启用的应用包名和目标关键词
    ↓
显示悬浮遮罩
    ↓
完成题目 → 临时解禁 → 到期后恢复遮挡
```

项目只对已配置且已启用的应用进行判断。页面关键词、解禁时长和遮罩样式均可按应用调整。

## 获取与安装

### 下载 APK

从以下任一发布页下载最新的 `app-release.apk`：

- [GitHub Releases](https://github.com/interest2/anti-addiction/releases)
- [Gitee Releases](https://gitee.com/interest2/anti-addiction/releases)

由于 APK 不通过应用商店分发，Android 可能要求你临时允许浏览器或文件管理器“安装未知应用”。请只从可信发布页下载安装包。

### 从源码构建

构建环境：

- Android Studio（推荐使用当前稳定版）
- JDK 17（Gradle / Android Gradle Plugin 运行环境）
- Android SDK 35

项目参数：

| 项目 | 版本 |
| --- | --- |
| Minimum SDK | 24（Android 7.0） |
| Target / Compile SDK | 35 |
| Android Gradle Plugin | 8.9.0 |
| Gradle Wrapper | 8.11.1 |
| Java 源码兼容级别 | 11 |



## 使用方法

1. 安装并首次打开应用。
2. 按系统引导授予“显示在其他应用上层”权限。
3. 在系统无障碍设置中启用“防沉迷提醒”服务。
4. 返回首页，为需要管理的应用开启监测并设置关键词、解禁时长和悬浮窗样式。
5. 打开目标应用，进入匹配页面后确认遮罩能够正常出现。
6. 如需临时关闭遮罩，点击关闭按钮并按提示完成题目。

如果遮罩未出现，可参考 apk 所在页面提到的博客文章。

## 权限、隐私与联网说明

### Android 权限

| 权限 / 能力 | 用途 |
| --- | --- |
| 显示在其他应用上层 | 在目标页面显示遮罩和答题界面 |
| 无障碍服务 | 获取窗口变化事件、当前应用包名和页面文本，以判断是否命中规则 |
| 网络访问与网络状态 | 获取云端提示文字、题目、版本信息并上报运行信息 |
| 唤醒锁、前台服务 | 提高监测服务在后台运行时的稳定性 |
| 开机完成广播 | 用于服务保活相关逻辑 |

无障碍服务配置声明了窗口内容读取能力（`canRetrieveWindowContent=true`）。这是一项高敏感权限，请在理解用途后自行决定是否启用；不使用时可以随时在系统设置中关闭。

### 当前版本的网络行为

客户端的核心页面识别与遮挡逻辑在本地执行，但部分功能依赖项目维护者的服务端。按照当前源码，应用启动及无障碍服务初始化时会向服务端发送设备与运行信息，包括：

- 设备品牌、型号、设备代号、Android 版本、SDK 版本和 CPU 架构；
- Android ID；
- 应用版本、版本号、包名；
- 网络类型、连接状态和时间戳。

请求云端提示文字或题目时，还可能发送 Android ID、应用版本、用户设置的目标标签、题目类型或阅读长度。源码中未发现通讯录、短信或定位权限。

服务端地址集中配置在 `app/src/main/java/com/book/mask/config/Const.java`。服务端代码当前未开源，因此自行部署或分发修改版前，应审查网络逻辑、替换服务端地址，并根据实际数据处理方式提供隐私政策。

## 技术栈

- Java 11
- AndroidX / Material Components
- AccessibilityService + WindowManager
- MMKV（本地配置存储）
- Gson（JSON 序列化）
- Gradle Version Catalog

## 项目结构

```text
app/src/main/
├── java/com/book/mask/
│   ├── config/      # 应用规则、常量与自定义应用管理
│   ├── floating/    # 无障碍服务、悬浮窗与答题流程
│   ├── lifecycle/   # 生命周期与服务保活
│   ├── network/     # 云端文字、题目及设备信息请求
│   ├── setting/     # 用户设置与解禁策略
│   ├── ui/          # Activity、导航页和对话框
│   └── util/        # 算术、日期和网络工具
├── res/             # 布局、图标、主题及无障碍配置
└── AndroidManifest.xml
```



## 许可证

本项目基于 [MIT License](./LICENSE) 开源。你可以使用、修改和分发本项目，但须保留原许可证与版权声明。
