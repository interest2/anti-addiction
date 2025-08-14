# 悬浮窗良好习惯提醒字体大小自定义功能

## 功能概述

在"个性设置"页面的"悬浮窗-良好习惯提醒"设置弹窗中新增了字体大小自定义功能，允许用户调整悬浮窗上良好习惯提醒文字的字体大小，提供更好的个性化体验。同时为良好习惯提醒文字添加了固定的字间距设置，提升文字的可读性。

## 功能特点

1. **设置入口**：在"个性设置"页面的"悬浮窗-良好习惯提醒"设置弹窗中
2. **字体范围**：12sp到32sp，共21个选项
3. **实时预览**：拖动滑块时实时显示当前字体大小
4. **持久保存**：使用SharedPreferences持久化保存字体大小设置
5. **立即生效**：设置保存后立即应用到悬浮窗显示
6. **默认值**：18sp（与原有设计保持一致）

## 实现细节

### 1. 数据存储
- 在 `SettingsManager` 中添加了 `KEY_FLOATING_STRICT_REMINDER_FONT_SIZE` 常量
- 提供了 `setFloatingStrictReminderFontSize()` 和 `getFloatingStrictReminderFontSize()` 方法
- 默认字体大小为18sp

### 2. UI界面
- 在设置对话框中添加了"字体大小设置"标题
- 添加了SeekBar滑块控件，范围12sp-32sp
- 添加了实时显示当前字体大小的文字提示
- 使用灰色文字保持界面简洁

### 3. 对话框管理
- 在 `SettingsDialogManager` 的 `showFloatingStrictReminderDialog()` 方法中添加字体大小设置
- 支持实时预览字体大小变化
- 保存时同时保存提醒文字和字体大小

### 4. 悬浮窗显示逻辑
- 在 `FloatService` 的 `updateStrictReminderDisplay()` 方法中应用自定义字体大小
- 使用 `setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)` 动态设置字体大小
- 在日志中记录当前使用的字体大小

### 5. 字间距设置
- 在 `floating_window_layout.xml` 中为良好习惯提醒文字添加了固定字间距
- 使用 `android:letterSpacing="0.1"` 属性设置字间距为0.1倍字体大小
- 提升文字的可读性和视觉效果

## 使用方法

1. 打开应用，进入"个性设置"页面
2. 点击"设置良好习惯提醒"按钮
3. 在弹出的对话框中：
   - 输入提醒文字（可选）
   - 拖动"字体大小设置"滑块调整字体大小
   - 实时查看字体大小数值
4. 点击"保存"按钮
5. 当悬浮窗显示时，会看到设置的自定义字体大小

## 技术实现

### 文件修改列表
- `app/src/main/java/com/book/mask/config/SettingsManager.java` - 添加字体大小存储方法
- `app/src/main/java/com/book/mask/ui/SettingsDialogManager.java` - 添加字体大小设置UI
- `app/src/main/java/com/book/mask/floating/FloatService.java` - 应用自定义字体大小
- `app/src/main/res/layout/floating_window_layout.xml` - 添加字间距设置

### 关键代码片段

```java
// 字体大小选择器
android.widget.SeekBar fontSizeSeekBar = new android.widget.SeekBar(context);
fontSizeSeekBar.setMax(20); // 12sp到32sp，共21个选项
fontSizeSeekBar.setProgress(settingsManager.getFloatingStrictReminderFontSize() - 12);

// 监听字体大小变化
fontSizeSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
    @Override
    public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
        int fontSize = progress + 12; // 12sp到32sp
        fontSizeDisplay.setText("当前字体大小: " + fontSize + "sp");
    }
});

// 应用自定义字体大小
strictReminderText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize);

// XML中的字间距设置
android:letterSpacing="0.1"

## 用户体验

### 界面设计
- **简洁明了**：字体大小设置区域与文字输入区域分离，层次清晰
- **实时反馈**：拖动滑块时立即显示字体大小数值
- **合理范围**：12sp-32sp覆盖了从较小到较大的常用字体大小
- **字间距优化**：固定0.1倍字体大小的字间距，提升文字可读性

### 操作流程
- **直观操作**：使用滑块控件，操作简单直观
- **即时预览**：实时显示当前选择的字体大小
- **统一保存**：字体大小与提醒文字一起保存，操作便捷

### 兼容性
- **向后兼容**：未设置字体大小时使用默认18sp
- **设置持久化**：重启应用后字体大小设置保持不变
- **动态应用**：悬浮窗显示时实时应用字体大小设置

## 注意事项

1. 字体大小设置与提醒文字设置一起保存，不能单独设置
2. 字体大小范围限制在12sp-32sp，确保可读性
3. 设置会立即生效，下次悬浮窗显示时就能看到
4. 默认字体大小为18sp，与原有设计保持一致
5. 字体大小设置对所有良好习惯提醒文字生效（包括默认文字和自定义文字）
6. 字间距设置为固定的0.1倍字体大小，提升文字可读性
7. 字间距设置对所有良好习惯提醒文字生效

