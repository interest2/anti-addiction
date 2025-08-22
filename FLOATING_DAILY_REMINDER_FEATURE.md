# 悬浮窗-良好习惯提醒功能

## 功能概述

在"个性设置"菜单页新增了"悬浮窗-良好习惯提醒"功能，允许用户设置自定义的日常提醒文字，这些文字将在悬浮窗上显示，提醒用户注意健康。

## 功能特点

1. **设置入口**：在"个性设置"页面的"悬浮窗-良好习惯提醒"区域
2. **输入限制**：最多50个字符
3. **显示位置**：悬浮窗中独立的绿色背景区域
4. **保存机制**：使用SharedPreferences持久化保存
5. **动态显示**：根据用户设置状态智能显示
6. **默认提示**：未设置时显示默认文字和小字提示

## 实现细节

### 1. 数据存储
- 在 `SettingsManager` 中添加了 `KEY_FLOATING_STRICT_REMINDER` 常量
- 添加了 `KEY_FLOATING_STRICT_REMINDER_SETTINGS_CLICKED` 常量记录用户点击状态
- 提供了 `setFloatingStrictReminder()` 和 `getFloatingStrictReminder()` 方法
- 提供了 `setFloatingStrictReminderSettingsClicked()` 和 `getFloatingStrictReminderSettingsClicked()` 方法

### 2. UI界面
- 在 `fragment_goal.xml` 中添加了"悬浮窗-良好习惯提醒"按钮
- 在 `floating_window_layout.xml` 中添加了 `strict_reminder_layout` 区域
- 添加了 `tv_strict_reminder_hint` 小字提示
- 使用绿色背景 (`#E8F5E8`) 和深绿色文字 (`#2E7D32`)

### 3. 对话框管理
- 在 `SettingsDialogManager` 中添加了 `showFloatingStrictReminderDialog()` 方法
- 支持输入当前已保存的文字
- 提供输入提示和长度限制
- 记录用户点击设置按钮的状态

### 4. 悬浮窗显示逻辑
- 在 `FloatService` 中添加了 `updateStrictReminderDisplay()` 方法
- 在悬浮窗显示和内容更新时自动调用
- 智能显示逻辑：
  - 未设置且未点击过设置按钮：显示默认文字 + 小字提示
  - 未设置但点击过设置按钮：显示默认文字，隐藏小字提示
  - 已设置自定义文字：显示自定义文字，隐藏小字提示

## 使用方法

1. 打开应用，进入"个性设置"页面
2. 点击"设置良好习惯提醒"按钮
3. 在弹出的对话框中输入提醒文字（如："玩手机？不如去喝水"）
4. 点击"保存"按钮
5. 当悬浮窗显示时，会看到设置的提醒文字

## 显示逻辑

### 初始状态（用户从未设置过）
- 显示默认文字："玩手机？不如去喝水"
- 显示小字提示："此内容的设置路径：个性设置-良好习惯提醒"

### 用户点击过设置按钮但未保存文字
- 显示默认文字："玩手机？不如去喝水"
- 隐藏小字提示

### 用户设置了自定义文字
- 显示用户设置的自定义文字
- 隐藏小字提示

## 技术实现

### 文件修改列表
- `app/src/main/java/com/book/mask/config/SettingsManager.java` - 添加数据存储方法和点击状态记录
- `app/src/main/java/com/book/mask/ui/GoalNav.java` - 添加按钮事件处理
- `app/src/main/java/com/book/mask/ui/SettingsDialogManager.java` - 添加对话框管理和点击状态记录
- `app/src/main/res/layout/fragment_goal.xml` - 添加设置按钮
- `app/src/main/res/layout/floating_window_layout.xml` - 添加显示区域和小字提示
- `app/src/main/java/com/book/mask/floating/FloatService.java` - 添加智能显示逻辑

### 关键代码片段

```java
// 记录用户点击设置按钮
relaxManager.setFloatingStrictReminderSettingsClicked(true);

// 智能显示逻辑
if (strictReminder.isEmpty()) {
    strictReminderText.setText("玩手机？不如去喝水");
    strictReminderLayout.setVisibility(View.VISIBLE);
    
    if (!hasClickedSettings) {
        strictReminderHint.setVisibility(View.VISIBLE);
    } else {
        strictReminderHint.setVisibility(View.GONE);
    }
} else {
    strictReminderText.setText(strictReminder);
    strictReminderHint.setVisibility(View.GONE);
    strictReminderLayout.setVisibility(View.VISIBLE);
}
```

## 注意事项

1. 用户点击过设置按钮后，如果未保存任何文字，默认文字继续显示，只隐藏小字提示
2. 提醒文字最多支持50个字符
3. 设置会立即生效，下次悬浮窗显示时就能看到
4. 提醒区域使用绿色主题，与健康提醒的概念相符
5. 小字提示使用浅绿色，与主文字形成层次感 