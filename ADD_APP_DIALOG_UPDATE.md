# 添加APP弹窗更新

## 更新内容

### 标题区域调整
- **标题**：保持"添加新APP"居中显示
- **新增**：在标题下方添加小字说明"xxxx"
- **样式**：小字使用12sp字体，灰色(#999999)，左对齐

### 布局结构

#### 修改前
```
┌─────────────────────────┐
│      添加新APP           │
├─────────────────────────┤
│                         │
│ APP名称                 │
│ [请输入APP名称]          │
│                         │
│ 包名                    │
│ [是英文形式，可上网搜...] │
│                         │
│ 屏蔽关键词              │
│ [常见的有：推荐、发现...] │
│                         │
│ 宽松模式次数            │
│ [默认值为1，可自定义]    │
│                         │
│        [取消] [保存]     │
└─────────────────────────┘
```

#### 修改后
```
┌─────────────────────────┐
│      添加新APP           │
│ xxxx                    │ ← 新增小字
├─────────────────────────┤
│                         │
│ APP名称                 │
│ [请输入APP名称]          │
│                         │
│ 包名                    │
│ [是英文形式，可上网搜...] │
│                         │
│ 屏蔽关键词              │
│ [常见的有：推荐、发现...] │
│                         │
│ 宽松模式次数            │
│ [默认值为1，可自定义]    │
│                         │
│        [取消] [保存]     │
└─────────────────────────┘
```

## 技术实现

### 代码修改
```xml
<!-- dialog_add_app.xml -->
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="添加新APP"
    android:textSize="20sp"
    android:textStyle="bold"
    android:gravity="center"
    android:layout_marginBottom="8dp" />

<!-- 新增：小字说明 -->
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="xxxx"
    android:textSize="12sp"
    android:textColor="#999999"
    android:gravity="start"
    android:layout_marginBottom="24dp" />
```

### 样式设计
- **字体大小**：12sp（较小，不干扰主要内容）
- **文字颜色**：#999999（灰色，低调显示）
- **对齐方式**：左对齐（gravity="start"）
- **间距调整**：
  - 标题底部间距：从24dp改为8dp
  - 小字底部间距：24dp（保持与下方内容的间距）

## 视觉效果

### 设计原则
1. **层次清晰**：标题、小字、内容区域层次分明
2. **视觉平衡**：小字不会干扰主要功能区域
3. **风格统一**：与整体界面风格保持一致
4. **信息补充**：为标题提供额外的说明信息

### 用户体验
- **信息完整**：标题下方有补充说明
- **布局合理**：小字位置不影响后续输入操作
- **视觉舒适**：字体大小和颜色适合阅读
- **功能完整**：不影响原有的添加APP功能

## 兼容性

- **向后兼容**：不影响现有的添加APP逻辑
- **布局稳定**：使用LinearLayout确保布局稳定
- **响应式设计**：适配不同屏幕尺寸
- **Material Design**：保持Material Design风格一致性 