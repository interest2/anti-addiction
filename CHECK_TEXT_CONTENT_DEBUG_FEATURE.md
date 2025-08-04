# checkTextContentOptimized 方法调试变量功能

## 功能概述

在调试悬浮窗中新增了显示 `checkTextContentOptimized` 方法中 `currentInterface` 和 `forceCheck` 变量的功能，用于实时监控文本检测方法的执行状态。

## 实现细节

### 1. Share.java 变量定义

在 `Share.java` 中新增了 2 个调试变量：

```java
// checkTextContentOptimized 方法调试变量
public static String currentInterface = ""; // 当前界面状态
public static boolean forceCheck = false; // 是否强制检查
```

### 2. FloatService.java 中的变量更新

在 `FloatService.java` 的 `checkTextContentOptimized` 方法中更新这些变量：

#### 2.1 forceCheck 变量更新
在方法开始时更新 `forceCheck` 变量：
```java
void checkTextContentOptimized(boolean forceCheck) {
    Share.forceCheck = forceCheck; // 更新调试变量
    // ... 其他代码
}
```

#### 2.2 currentInterface 变量更新
在界面判断逻辑中更新 `currentInterface` 变量：
```java
// 简化界面判断逻辑：只检测目标词
String currentInterface = hasTargetWord ? "target" : "other";
Share.currentInterface = currentInterface; // 更新调试变量
```

### 3. 调试悬浮窗显示

在 `DebugFloatingWindowManager.java` 的 `updateDebugContent()` 方法中新增了显示逻辑：

```java
// checkTextContentOptimized 方法调试变量
debugInfo.append("🔍 文本检测变量:\n");
debugInfo.append("  当前界面: ").append(Share.currentInterface).append("\n");
debugInfo.append("  强制检查: ").append(Share.forceCheck ? "是" : "否").append("\n");
```

## 显示格式

调试悬浮窗中会以以下格式显示这些变量：

```
🔍 文本检测变量:
  当前界面: target
  强制检查: 否
```

## 变量说明

### currentInterface
- **类型**: String
- **可能的值**: 
  - `"target"`: 检测到目标词，当前界面为目标界面
  - `"other"`: 未检测到目标词，当前界面为其他界面
  - `""`: 未初始化或未执行检测

### forceCheck
- **类型**: boolean
- **可能的值**:
  - `true`: 强制检查模式（通常由定时器触发）
  - `false`: 正常检查模式（通常由界面变化触发）

## 使用说明

1. 启动应用并启用无障碍服务
2. 在设置页面点击"切换调试悬浮窗"按钮
3. 调试悬浮窗会显示在屏幕底部
4. 文本检测变量信息会每2秒自动更新一次
5. 当 `checkTextContentOptimized` 方法被调用时，这些变量会实时更新

## 调试价值

这些变量可以帮助开发者：

1. **监控检测状态**: 实时了解文本检测的结果
2. **追踪执行模式**: 区分正常检查和强制检查模式
3. **界面状态分析**: 了解当前界面是否为目标界面
4. **问题定位**: 快速定位文本检测相关的问题

## 技术特点

- **实时更新**: 变量信息每2秒自动刷新
- **状态跟踪**: 准确反映 `checkTextContentOptimized` 方法的执行状态
- **模式识别**: 清晰显示当前是正常检查还是强制检查模式
- **界面监控**: 实时显示当前界面的检测结果

## 注意事项

- 变量会在每次调用 `checkTextContentOptimized` 方法时更新
- `currentInterface` 为空字符串表示尚未执行检测
- `forceCheck` 为 `false` 表示正常检查模式，为 `true` 表示强制检查模式
- 建议结合时间戳变量一起使用，以获得更完整的调试信息 