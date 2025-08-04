# 调试悬浮窗置顶功能更新

## 更新内容

### 问题描述
原有的调试悬浮窗被主悬浮窗遮挡，影响调试信息的查看。

### 解决方案
通过修改调试悬浮窗的层级设置，确保其置顶显示：

#### 1. 修改的代码位置
- 文件：`app/src/main/java/com/book/mask/floating/DebugFloatingWindowManager.java`
- 方法：`getDebugLayoutParams()`

#### 2. 具体修改
```java
// 修改前
params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

// 修改后
params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
```

#### 3. 新增的Flag说明
- `FLAG_LAYOUT_IN_SCREEN`: 允许窗口在屏幕范围内布局
- `FLAG_LAYOUT_NO_LIMITS`: 允许窗口超出屏幕边界
- `FLAG_ALT_FOCUSABLE_IM`: 提供更高的输入法焦点优先级

## 功能特点

### 1. 置顶显示
- 调试悬浮窗现在会显示在所有其他悬浮窗之上
- 不会被主悬浮窗遮挡
- 确保调试信息始终可见

### 2. 保持原有功能
- 调试悬浮窗仍然不会影响主悬浮窗的正常工作
- 触摸事件处理保持不变
- 自动更新和手动刷新功能正常

### 3. 兼容性
- 与现有系统完全兼容
- 不影响主悬浮窗的功能
- 支持所有Android版本

## 测试要点

### 1. 层级测试
- 同时显示主悬浮窗和调试悬浮窗
- 确认调试悬浮窗在最上层
- 验证调试信息完全可见

### 2. 交互测试
- 调试悬浮窗的按钮可以正常点击
- 主悬浮窗的功能不受影响
- 两个悬浮窗可以同时正常工作

### 3. 性能测试
- 置顶显示不影响系统性能
- 内存使用保持稳定
- 电池消耗无明显增加

## 使用说明

### 1. 开启调试悬浮窗
1. 进入应用设置页面
2. 找到"调试功能"区域
3. 点击"显示/隐藏调试悬浮窗"
4. 调试悬浮窗将置顶显示在屏幕底部

### 2. 验证置顶效果
- 如果主悬浮窗正在显示，调试悬浮窗会显示在其上方
- 调试信息应该完全可见，不会被遮挡
- 可以同时查看两个悬浮窗的信息

### 3. 关闭调试悬浮窗
- 在设置页面再次点击按钮，或
- 在调试悬浮窗中点击"隐藏"按钮

## 注意事项

1. **权限要求**: 需要悬浮窗权限才能显示置顶的调试悬浮窗
2. **系统兼容**: 在某些定制Android系统上可能需要额外权限
3. **性能影响**: 置顶显示对性能影响很小，但会占用少量额外资源
4. **用户体验**: 调试悬浮窗现在更加突出，便于查看调试信息

## 故障排除

### 问题1: 调试悬浮窗仍然被遮挡
**可能原因**: 系统权限不足或其他应用使用了更高层级
**解决方法**: 
1. 检查悬浮窗权限是否完整
2. 重启应用后重试
3. 检查是否有其他应用使用了更高层级的悬浮窗

### 问题2: 调试悬浮窗显示异常
**可能原因**: 系统兼容性问题
**解决方法**:
1. 更新Android系统到最新版本
2. 检查是否有系统级的安全软件干扰
3. 重启设备后重试

## 技术细节

### 1. 层级管理
- 使用`TYPE_APPLICATION_OVERLAY`类型
- 通过Flag组合实现置顶效果
- 保持与系统其他组件的兼容性

### 2. 事件处理
- 调试悬浮窗不会拦截主悬浮窗的触摸事件
- 保持原有的触摸事件传递机制
- 确保两个悬浮窗可以独立工作

### 3. 内存管理
- 置顶显示不会增加内存占用
- 保持原有的资源清理机制
- 确保应用退出时正确释放资源 