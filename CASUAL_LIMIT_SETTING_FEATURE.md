# 宽松模式一天次数设置功能

## 功能描述

在点击卡片后的弹窗中，新增了宽松模式一天次数设置功能，允许用户修改自定义APP的`relaxedLimitCount`变量。

## 功能特性

### 界面元素
1. **分隔线**：将原有功能与新功能分开
2. **标题**："宽松模式一天次数设置"
3. **输入框**：用于输入1-3之间的数字
4. **保存按钮**：保存修改的设置

### 输入验证
- **数字验证**：只允许输入数字
- **范围验证**：只允许输入1-3之间的数字
- **长度限制**：最多输入1位数字
- **空值检查**：不允许空输入

### 权限控制
- **预定义APP**：支持自定义次数设置（覆盖默认值）
- **自定义APP**：支持修改并保存到本地存储

## 技术实现

### 布局修改

#### dialog_time_setting.xml
```xml
<!-- 分隔线 -->
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="#E0E0E0"
    android:layout_marginBottom="16dp" />

<!-- 宽松模式一天次数设置 -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="宽松模式一天次数设置"
    android:textSize="14sp"
    android:textColor="#333333"
    android:textStyle="bold"
    android:layout_marginBottom="8dp" />

<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:layout_marginBottom="12dp">

    <EditText
        android:id="@+id/et_relaxed_limit_count"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:hint="输入1-3的数字"
        android:inputType="number"
        android:maxLength="1"
        android:padding="8dp"
        android:textSize="14sp"
        android:background="@android:drawable/edit_text"
        android:layout_marginEnd="8dp" />

    <Button
        android:id="@+id/btn_save_relaxed_limit"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="保存"
        android:textSize="12sp"
        android:padding="8dp"
        android:backgroundTint="#2196F3"
        android:textColor="#FFFFFF" />

</LinearLayout>
```

### 代码修改

#### SettingsManager.java - 新增方法
```java
/**
 * 获取预定义APP的自定义次数设置
 */
public Integer getCustomRelaxedLimitCount(String packageName) {
    String key = "custom_relaxed_limit_" + packageName;
    int value = prefs.getInt(key, -1);
    return value == -1 ? null : value; // 返回null表示使用默认值
}

/**
 * 设置预定义APP的自定义次数设置
 */
public void setCustomRelaxedLimitCount(String packageName, int count) {
    String key = "custom_relaxed_limit_" + packageName;
    prefs.edit().putInt(key, count).apply();
}

/**
 * 清除预定义APP的自定义次数设置（恢复默认值）
 */
public void clearCustomRelaxedLimitCount(String packageName) {
    String key = "custom_relaxed_limit_" + packageName;
    prefs.edit().remove(key).apply();
}
```

#### Const.java - CustomApp类
```java
public static class CustomApp implements App {
    private final String appName;
    private final String packageName;
    private final String targetWord;
    private int relaxedLimitCount; // 改为非final，支持修改
    
    // ... 其他方法 ...
    
    public void setRelaxedLimitCount(int relaxedLimitCount) {
        this.relaxedLimitCount = relaxedLimitCount;
    }
}
```

#### CustomAppManager.java
```java
/**
 * 保存自定义APP的更改（公共方法）
 */
public void saveCustomAppsChanges() {
    saveCustomApps();
}
```

#### HomeFragment.java - showTimeSettingDialogForApp方法
```java
// 获取UI元素
EditText etRelaxedLimitCount = dialogView.findViewById(R.id.et_relaxed_limit_count);
Button btnSaveRelaxedLimit = dialogView.findViewById(R.id.btn_save_relaxed_limit);

// 设置输入框的当前值（优先使用自定义设置）
if (app instanceof Const.SupportedApp) {
    Const.SupportedApp supportedApp = (Const.SupportedApp) app;
    Integer customLimit = settingsManager.getCustomRelaxedLimitCount(supportedApp.getPackageName());
    relaxedLimitCount = customLimit != null ? customLimit : supportedApp.getRelaxedLimitCount();
} else if (app instanceof Const.CustomApp) {
    Const.CustomApp customApp = (Const.CustomApp) app;
    relaxedLimitCount = customApp.getRelaxedLimitCount();
}
etRelaxedLimitCount.setText(String.valueOf(relaxedLimitCount));

// 设置保存按钮点击事件
btnSaveRelaxedLimit.setOnClickListener(v -> {
    String inputText = etRelaxedLimitCount.getText().toString().trim();
    if (inputText.isEmpty()) {
        Toast.makeText(requireContext(), "请输入数字", Toast.LENGTH_SHORT).show();
        return;
    }
    
    try {
        int newLimitCount = Integer.parseInt(inputText);
        if (newLimitCount < 1 || newLimitCount > 3) {
            Toast.makeText(requireContext(), "请输入1-3之间的数字", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 更新APP的relaxedLimitCount
        if (app instanceof Const.SupportedApp) {
            // 对于预定义APP，保存自定义次数设置
            Const.SupportedApp supportedApp = (Const.SupportedApp) app;
            settingsManager.setCustomRelaxedLimitCount(supportedApp.getPackageName(), newLimitCount);
            Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show();
            
            // 更新APP列表显示
            updateAppCardsDisplay();
        } else if (app instanceof Const.CustomApp) {
            Const.CustomApp customApp = (Const.CustomApp) app;
            customApp.setRelaxedLimitCount(newLimitCount);
            customAppManager.saveCustomAppsChanges(); // 保存到本地存储
            Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show();
            
            // 更新APP列表显示
            updateAppCardsDisplay();
        }
        
    } catch (NumberFormatException e) {
        Toast.makeText(requireContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
    }
});
```

#### AppCardAdapter.java - 修改bind方法
```java
// 根据APP类型获取信息
if (app instanceof Const.SupportedApp) {
    Const.SupportedApp supportedApp = (Const.SupportedApp) app;
    appName = supportedApp.getAppName();
    // 优先使用自定义设置，如果没有则使用默认值
    Integer customLimit = settingsManager.getCustomRelaxedLimitCount(supportedApp.getPackageName());
    relaxedLimitCount = customLimit != null ? customLimit : supportedApp.getRelaxedLimitCount();
    packageName = supportedApp.getPackageName();
} else if (app instanceof Const.CustomApp) {
    Const.CustomApp customApp = (Const.CustomApp) app;
    appName = customApp.getAppName();
    relaxedLimitCount = customApp.getRelaxedLimitCount();
    packageName = customApp.getPackageName();
}
```

## 用户体验

### 界面设计
- **清晰分隔**：使用分隔线将功能区域分开
- **直观操作**：输入框和保存按钮并排显示
- **即时反馈**：输入验证和保存结果都有Toast提示

### 交互流程
1. 用户点击APP卡片
2. 弹窗显示，输入框显示当前次数设置（优先显示自定义设置）
3. 用户修改数字（1-3之间）
4. 点击保存按钮
5. 系统验证输入并保存
6. 显示保存结果，更新APP列表

### 错误处理
- **输入为空**：提示"请输入数字"
- **超出范围**：提示"请输入1-3之间的数字"
- **无效数字**：提示"请输入有效的数字"

## 数据持久化

### 保存机制
- **预定义APP**：保存自定义次数设置到SharedPreferences
- **自定义APP**：修改后立即保存到SharedPreferences

### 数据同步
- **实时更新**：保存后立即更新APP列表显示
- **持久化存储**：使用SharedPreferences保存自定义设置

## 功能特性

### 预定义APP支持
- **自定义覆盖**：可以覆盖枚举中的默认次数设置
- **默认值回退**：如果没有自定义设置，使用枚举默认值
- **持久化保存**：自定义设置保存到本地，重启后仍然有效

### 自定义APP支持
- **完全支持**：可以自由修改次数设置
- **即时生效**：修改后立即生效并保存

## 技术细节

### 数据优先级
1. **自定义设置**：用户通过界面设置的值
2. **默认值**：枚举中定义的默认值

### 存储键值
- **预定义APP**：`custom_relaxed_limit_包名`
- **自定义APP**：通过CustomAppManager管理

### 数据恢复
- **清除自定义设置**：调用`clearCustomRelaxedLimitCount`恢复默认值
- **自动回退**：如果自定义设置为null，自动使用默认值 