# 弹窗标题布局调整

## 修改内容

### 标题调整
- **修改前**：标题显示为 `"APP名称 - 单次解禁时长"`
- **修改后**：标题只显示 `"APP名称"`

### 内容布局调整
- **新增**：在按钮上方添加"单次解禁时长"文字说明
- **样式**：灰色文字，14sp字体大小，居中对齐

### 按钮大小优化
- **字体大小**：从16sp减小到14sp
- **内边距**：从16dp减小到12dp
- **按钮间距**：从12dp减小到8dp

### 按钮布局优化
- **并排展示**：严格模式和宽松模式按钮水平并排
- **等宽布局**：两个按钮各占50%宽度
- **间距优化**：按钮间4dp间距，整体16dp下边距

## 视觉效果对比

### 修改前
```
┌─────────────────────────┐
│ 小红书 - 单次解禁时长     │
├─────────────────────────┤
│                         │
│      [严格模式]          │
│                         │
│      [宽松模式]          │
│                         │
│        [取消]            │
└─────────────────────────┘
```

### 修改后
```
┌─────────────────────────┐
│        小红书             │
├─────────────────────────┤
│                         │
│      单次解禁时长         │
│                         │
│  [严格模式] [宽松模式]    │ ← 按钮并排
│                         │
│    ──────────────────    │
│                         │
│  宽松模式一天次数设置     │
│                         │
│  [输入框] [保存]         │
│                         │
│        [取消]            │
└─────────────────────────┘
```

## 技术实现

### 代码修改
```java
// HomeFragment.java
android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(requireContext())
    .setTitle(appName)  // 只显示APP名称
    .setView(dialogView)
    .setNegativeButton("取消", null)
    .create();
```

### 布局修改
```xml
<!-- dialog_time_setting.xml -->
<LinearLayout>
    <!-- 新增：单次解禁时长说明 -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="单次解禁时长"
        android:textSize="14sp"
        android:textColor="#666666"
        android:layout_marginBottom="16dp" />
    
    <!-- 并排按钮布局 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="16dp">

        <Button 
            android:id="@+id/btn_strict_mode"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="严格模式"
            android:textSize="14sp"
            android:padding="12dp"
            android:backgroundTint="#4CAF50"
            android:textColor="#FFFFFF"
            android:layout_marginEnd="4dp" />
        
        <Button 
            android:id="@+id/btn_casual_mode"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="宽松模式"
            android:textSize="14sp"
            android:padding="12dp"
            android:backgroundTint="#FF9800"
            android:textColor="#FFFFFF"
            android:layout_marginStart="4dp" />

    </LinearLayout>
    
    <!-- 其他内容... -->
</LinearLayout>
```

## 用户体验改进

### 设计优势
1. **标题简洁**：只显示APP名称，更加简洁明了
2. **层次清晰**：功能说明独立显示，层次更清晰
3. **视觉平衡**：标题和内容分离，视觉更平衡
4. **信息突出**：APP名称在标题中更加突出
5. **按钮紧凑**：按钮大小适中，不会显得过于庞大
6. **布局高效**：按钮并排展示，节省垂直空间

### 交互体验
- **更直观**：用户一眼就能看出是哪个APP的设置
- **更清晰**：功能说明独立显示，不会与标题混淆
- **更现代**：符合当前移动应用的设计趋势
- **更紧凑**：按钮大小合适，点击区域适中
- **更高效**：并排布局节省空间，操作更便捷

## 功能保持

- **功能不变**：所有原有功能保持不变
- **交互不变**：按钮点击行为完全一致
- **逻辑不变**：弹窗显示逻辑完全一致
- **样式优化**：只是视觉布局的优化 