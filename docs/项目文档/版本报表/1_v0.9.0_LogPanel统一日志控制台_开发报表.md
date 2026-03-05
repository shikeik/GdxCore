# LogPanel 统一日志控制台 — 开发报表

> 版本: 1.0  
> 日期: 2026-03-03  
> 分支: main (6a5912f → fe0e309)

---

## 一、概述

本次开发为 GdxCore 引擎库新增 **统一日志控制台 (LogPanel)** 功能，解决原有三套日志展示系统 (DLog + DebugConsole + IDEConsole) 各自为政、无法筛选的问题。

参考业界方案 (Unity IngameDebugConsole、UE OutputLog)，实现了：
- 结构化日志存储 + 搜索/等级/Tag 多维筛选
- 运行时命令系统 (注册/执行/补全)
- 统一组件嵌入 DebugConsole 和 IDEConsole

---

## 二、开发阶段与提交记录

| 阶段 | 提交哈希 | 提交信息 |
|------|----------|----------|
| Phase 0 | fa7ea7c | feat: Phase0 - 新增LogEntry结构化日志条目, DLog双写支持 |
| Phase 1 | b45dfab | feat: Phase1 - 新增LogPanel统一日志控制台组件 |
| Phase 2 | 940c8ed | feat: Phase2 - 新增CommandRegistry命令系统, LogPanel命令输入栏 |
| Phase 3 | 2cbbec4 | feat: Phase3 - DebugConsole集成LogPanel, LOG标签页全面升级 |
| Phase 4 | fe0e309 | feat: Phase4 - IDEConsole重构为LogPanel内核, 完成统一日志控制台 |

---

## 三、文件变更统计

### 新增文件 (6 个)

| 文件 | 类型 | 行数 | 说明 |
|------|------|------|------|
| `log/LogEntry.java` | 源码 | 36 | 结构化日志条目 (level/tag/time/message/formatted + matchesSearch) |
| `log/LogPanel.java` | 源码 | 577 | 统一日志控制台面板 (搜索/等级/Tag筛选/命令输入/自动滚动) |
| `log/CommandRegistry.java` | 源码 | 155 | 运行时命令注册表 (register/execute/getSuggestions/getHelp) |
| `log/LogEntryTest.java` | 测试 | 85 | LogEntry 单元测试 (8 用例) |
| `log/DLogDataLayerTest.java` | 测试 | 158 | DLog 数据层单元测试 (9 用例) |
| `log/LogPanelFilterTest.java` | 测试 | 169 | 筛选逻辑单元测试 (9 用例) |
| `log/CommandRegistryTest.java` | 测试 | 140 | 命令系统单元测试 (13 用例) |

### 修改文件 (4 个)

| 文件 | 改动量 | 说明 |
|------|--------|------|
| `log/DLog.java` | +24 -6 | 新增 logEntries 字段、GdxUiOutput 双写、getLogEntries()/clearAllLogs() |
| `log/DebugConsole.java` | +22 -62 | LOG标签页改用 LogPanel, 移除旧 AutoScroll/Clear, Container 泛型改为 Actor |
| `ui/widget/IDEConsole.java` | +43 -65 | 内部改用 LogPanel, 保留折叠/展开外壳, 新增 getLogPanel() |
| `core/build.gradle` | +2 | 新增 JUnit 4.13.2 测试依赖 |

### 总计

- **新增**: 1320 行
- **删除**: 120 行
- **净增**: +1200 行
- **涉及文件**: 11 个 (7 新增 + 4 修改)

---

## 四、测试覆盖

| 测试类 | 用例数 | 通过 | 失败 | 覆盖范围 |
|--------|--------|------|------|----------|
| LogEntryTest | 8 | 8 | 0 | 构造函数、matchesSearch (null/空/匹配/不匹配/大小写)、toString |
| DLogDataLayerTest | 9 | 9 | 0 | 双写验证、数量一致性、等级记录、Tag传递、黑名单拦截、缓存上限、clearAllLogs、引用同一性、搜索集成 |
| LogPanelFilterTest | 9 | 9 | 0 | 等级过滤 (单/多/全)、Tag过滤 (All/指定)、搜索过滤 (message/tag)、组合过滤、空搜索 |
| CommandRegistryTest | 13 | 13 | 0 | 注册执行、无参数、未知命令、空输入、大小写、同名覆盖、异常捕获、补全建议、help、hasCommand/unregister/clearAll |
| **合计** | **39** | **39** | **0** | **100% 通过率** |

---

## 五、架构设计

```
┌───────────────────────────────────────────┐
│                 DLog                       │
│  logMessages: List<String>   (向后兼容)    │
│  logEntries:  List<LogEntry> (结构化存储)  │
│  dispatch() → GdxUiOutput.onLog() 双写     │
└──────────────────┬────────────────────────┘
                   │ 读取
                   ▼
┌───────────────────────────────────────────┐
│              LogPanel                      │
│  searchField  → 实时搜索                   │
│  levelFilters → DEBUG/INFO/WARN/ERROR 切换 │
│  tagFilter    → All / 手动多选             │
│  logLabel     → 过滤后日志显示             │
│  commandField → 命令输入 + 历史浏览        │
│  CommandRegistry → 内置 + 自定义命令       │
└──────────┬───────────────┬────────────────┘
           │               │
     嵌入   ▼         嵌入  ▼
┌──────────────┐  ┌──────────────────┐
│ DebugConsole │  │   IDEConsole     │
│ LOG标签页    │  │ 折叠/展开外壳    │
│ (Phase 3)    │  │ (Phase 4)        │
└──────────────┘  └──────────────────┘
```

---

## 六、内置命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `help` | 列出所有已注册命令 | `help` |
| `clear` | 清空日志 | `clear` |
| `filter` | Tag 过滤增减 | `filter +Combat -Quest` / `filter all` |
| `level` | 切换显示等级 | `level warn error` / `level all` |
| `search` | 设置搜索关键词 | `search 连接失败` / `search` (清除) |

游戏项目可通过 `CommandRegistry.register()` 添加自定义命令。

---

## 七、消费端使用

### DebugConsole (自动生效)
LOG 标签页已自动升级为带筛选的 LogPanel，无需额外代码。

### 游戏内独立嵌入
```java
LogPanel gameLog = new LogPanel();
gameLog.setDefaultTagFilter("Game", "Combat", "Quest");
someTable.add(gameLog).grow();
```

### IDEConsole (自动生效)
```java
IDEConsole console = new IDEConsole();
// 可选: 配置内部 LogPanel
console.getLogPanel().setDefaultTagFilter("Game", "UI");
```

### 自定义命令注册
```java
CommandRegistry.register("tp", "传送到指定坐标", args -> {
    String[] parts = args.split(" ");
    float x = Float.parseFloat(parts[0]);
    float y = Float.parseFloat(parts[1]);
    player.setPosition(x, y);
    return "已传送到 (" + x + ", " + y + ")";
});
```

---

## 八、遗留与后续

| 项目 | 优先级 | 说明 |
|------|--------|------|
| 命令自动补全 UI | P2 | Tab 键触发、下拉候选列表 |
| 相同日志折叠 (Collapse) | P2 | 参考 Unity 的重复日志合并显示 + 计数 |
| 虚拟化渲染 | P3 | 大量日志时按需渲染可见行，提升性能 |
| 日志持久化 | P3 | 自动导出到文件供离线分析 |
| FilterGroup 分组 | P2 | Tag 按业务分组 (游戏/引擎/网络) |
