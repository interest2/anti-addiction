# 主界面底部备注功能

## 功能描述

在主界面底部添加了一段备注文字，显示在底部导航栏下方。

## 界面布局

### 布局结构
```
┌─────────────────────────┐
│                         │
│      Fragment容器        │
│     (主要内容区域)       │
│                         │
│                         │
├─────────────────────────┤
│       xxxxx             │ ← 备注文字
├─────────────────────────┤
│     底部导航栏           │
│   [首页] [设置]          │
└─────────────────────────┘
```

### 布局修改

#### activity_main.xml
```xml
<androidx.constraintlayout.widget.ConstraintLayout>
    <!-- Fragment容器 -->
    <FrameLayout
        android:id="@+id/fragment_container"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@id/tv_remark" />

    <!-- 备注文字 -->
    <TextView
        android:id="@+id/tv_remark"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="xxxxx"
        android:textSize="12sp"
        android:textColor="#999999"
        android:gravity="center"
        android:padding="8dp"
        android:background="#F5F5F5"
        app:layout_constraintBottom_toTopOf="@id/bottom_navigation" />

    <!-- 底部导航栏 -->
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottom_navigation"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:layout_constraintBottom_toBottomOf="parent"
        app:menu="@menu/bottom_nav_menu" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

## 样式设计

### 文字样式
- **字体大小**：12sp（较小，不干扰主要内容）
- **文字颜色**：#999999（灰色，低调显示）
- **对齐方式**：居中对齐

### 背景样式
- **背景颜色**：#F5F5F5（浅灰色背景）
- **内边距**：8dp（适当的间距）

### 布局约束
- **位置**：Fragment容器下方，底部导航栏上方
- **宽度**：全屏宽度
- **高度**：自适应内容高度

## 功能特点

### 视觉层次
- **不干扰**：备注文字不会干扰主要功能区域
- **清晰分离**：与Fragment容器和底部导航栏都有明确的视觉分离
- **低调显示**：使用较小的字体和灰色，保持低调

### 布局适配
- **响应式**：在不同屏幕尺寸下都能正确显示
- **约束布局**：使用ConstraintLayout确保布局稳定
- **自适应**：文字内容可以自适应长度

## 技术实现

### 约束关系
1. **Fragment容器**：顶部到父容器，底部到备注文字
2. **备注文字**：底部到底部导航栏
3. **底部导航栏**：底部到父容器

### 样式属性
```xml
android:text="xxxxx"                    <!-- 备注内容 -->
android:textSize="12sp"                 <!-- 字体大小 -->
android:textColor="#999999"             <!-- 文字颜色 -->
android:gravity="center"                <!-- 居中对齐 -->
android:padding="8dp"                   <!-- 内边距 -->
android:background="#F5F5F5"            <!-- 背景颜色 -->
```

## 用户体验

### 信息展示
- **位置合适**：在主要内容区域和导航栏之间，不会遮挡任何功能
- **样式统一**：与整体界面风格保持一致
- **易于阅读**：字体大小和颜色适合阅读

### 界面平衡
- **视觉平衡**：为界面提供了良好的视觉层次
- **功能完整**：不影响任何现有功能
- **布局稳定**：使用约束布局确保布局稳定

## 扩展性

### 内容修改
- **动态内容**：可以通过代码动态修改备注内容
- **多语言支持**：可以支持不同语言的备注内容
- **条件显示**：可以根据条件显示或隐藏备注

### 样式定制
- **颜色调整**：可以根据需要调整文字和背景颜色
- **字体调整**：可以调整字体大小和样式
- **布局调整**：可以调整内边距和对齐方式

## 维护说明

### 内容更新
如需修改备注内容，只需要修改`android:text`属性：
```xml
android:text="新的备注内容"
```

### 样式调整
如需调整样式，可以修改相应的属性：
- 字体大小：`android:textSize`
- 文字颜色：`android:textColor`
- 背景颜色：`android:background`
- 内边距：`android:padding` 