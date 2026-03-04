# GdxCore v1.2.0 SelectableLabel & ConsoleTextField — UI 输入系统重构报表

> 版本: 5.0  
> 日期: 2026-03-04  
> 分支: main (57d38d1 → 5f16632)  
> 涉及版本: 1.1.0 → 1.2.0
> Tag: 1.2.0

---

## 一、概述

本次开发新增 **2 个通用 UI 组件** 并集成到现有日志系统中，目标是为 DebugConsole / LogPanel / ChatOverlay 提供统一的文本选择、复制、命令输入体验：

1. **SelectableLabel** — 可选中文本标签，长按弹出上下文菜单（复制全部 / 全选），支持 Ctrl+A/C 快捷键，蓝色高亮叠加层。
2. **ConsoleTextField** — 通用控制台输入栏，封装 VisTextField + 历史翻阅 + Enter 提交 + TextFieldPasteMenu 长按菜单。
3. **LogPanel 集成** — logLabel 替换为 SelectableLabel，commandField 全套替换为 ConsoleTextField（删除手动历史管理逻辑），searchField 附加 TextFieldPasteMenu。
4. **DebugConsole 集成** — introLabel / infoLabel 替换为 SelectableLabel，支持长按复制。

---

## 二、文件变更统计

### 新增文件 (2 个)

| 文件 | 行数 | 说明 |
|------|------|------|
| `ui/widget/SelectableLabel.java` | 257 | 可选中文本标签，长按上下文菜单 + Ctrl+A/C |
| `ui/widget/ConsoleTextField.java` | 199 | 通用控制台输入栏，历史翻阅 + Enter 提交 + TextFieldPasteMenu |

### 修改文件 (3 个)

| 文件 | 改动量 | 说明 |
|------|--------|------|
| `log/LogPanel.java` | +19 -62 | logLabel→SelectableLabel, commandField→ConsoleTextField, 删除 navigateHistory/executeCommandInput, searchField 附加 TextFieldPasteMenu |
| `log/DebugConsole.java` | +4 -2 | introLabel/infoLabel 类型替换为 SelectableLabel |
| `gradle.properties` | +1 -1 | 1.1.0 → 1.2.0 |

### 总计

- **新增**: +456 行
- **修改**: +24 / -65 行
- **涉及文件**: 5 个

---

## 三、组件设计

### 3.1 SelectableLabel

继承 `VisLabel`，完全兼容原有 API。

**核心功能**：
- 长按 350ms 弹出上下文菜单（`showContextMenu()`）
- 菜单项: "复制全部" / "全选" / "取消选择"
- 全选状态下 `draw()` 覆盖蓝色高亮叠加层 (`SELECTION_COLOR = 0.2, 0.5, 1.0, 0.3`)
- PC 端支持 Ctrl+A 全选 + Ctrl+C 复制
- 点击空白区或菜单外自动关闭弹窗 + 取消选择

**实现细节**：
- 弹窗使用 `VisTable` 动态添加到 `Stage`，基于触摸坐标定位
- `copyToClipboard()` 调用 `Gdx.app.getClipboard().setContents()`
- 键盘事件通过 `InputListener.keyDown()` 拦截

### 3.2 ConsoleTextField

继承 `VisTable`，内嵌 `VisTextField`。

**封装功能**：
- ↑↓ 历史命令翻阅（`navigateHistory()`），上翻时保存当前输入 (`pendingInput`)
- Enter 触发 `Consumer<String> onSubmit` 回调
- 自动调用 `TextFieldPasteMenu.attach(textField)` 为 Android 提供长按菜单
- 内置 VisTextField 自带: 光标移动、Home/End、Ctrl+A/C/V/X、鼠标拖选

**公开 API**：
- `setOnSubmit(Consumer<String>)` / `setMessageText(String)` / `getText()` / `setText()` / `clear()` / `focus()`
- `setMaxHistory(int)` / `getHistory()` / `getTextField()`

---

## 四、集成变更

### 4.1 LogPanel

| 变更点 | 旧代码 | 新代码 |
|--------|--------|--------|
| 日志标签 | `VisLabel logLabel` | `SelectableLabel logLabel` |
| 命令输入 | `VisTextField commandField` + 手动 InputListener + Send 按钮 + `navigateHistory()` + `executeCommandInput()` | `ConsoleTextField consoleInput` + `setOnSubmit(this::executeCommandInput)` |
| 搜索栏 | 无上下文菜单 | `TextFieldPasteMenu.attach(searchField)` |
| 删除代码 | — | `commandHistory` / `historyIndex` 字段, `navigateHistory()` 方法 |

### 4.2 DebugConsole

| 变更点 | 旧代码 | 新代码 |
|--------|--------|--------|
| INTRO 标签 | `VisLabel introLabel = new VisLabel("", "small")` | `SelectableLabel introLabel = new SelectableLabel("", "small")` |
| INFO 标签 | `VisLabel infoLabel = new VisLabel("", "small")` | `SelectableLabel infoLabel = new SelectableLabel("", "small")` |

向后兼容：`SelectableLabel` 继承 `VisLabel`，`setText()` / `setWrap()` 等 API 100% 兼容。
