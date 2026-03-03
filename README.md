# GdxCore

**纯底层引擎核心框架** — 基于 libGDX 的通用基础设施库。

## 宗旨

> **GdxCore 是一个纯粹的底层引擎框架，严禁包含任何业务逻辑或上层封装。**

### 严格边界定义

**允许包含（底层基础设施）：**
- 日志系统（DLog、DebugConsole、LogPanel）
- 屏幕管理（ScreenManager）
- UI 基础组件（通用 Widget、主题、布局工具）
- 资源管理（加载、缓存、释放）
- 数学/工具库（通用算法、数据结构）
- 平台适配（PlatformProfile）
- 命令注册表（CommandRegistry — 纯路由机制，不含具体命令实现）

**严禁包含（业务逻辑 / 上层封装）：**
- 聊天系统（ChatChannel、ChatOverlay、ChatMessage 等）
- 游戏玩法逻辑（战斗、移动、碰撞等）
- 网络联机功能（已拆分至 [GdxNetcode](../GdxNetcode)）
- 特定游戏的 UI 组件（如血条、背包面板等）
- 任何与具体游戏项目耦合的代码

### 修改原则

1. **稳定优先** — GdxCore 应当极少变动，下游项目可以放心锁定版本
2. **通用性** — 新增 API 必须对所有 libGDX 项目通用，不为某个项目定制
3. **零业务耦合** — 如果一个功能只在某个游戏中使用，它不属于这里
4. **向后兼容** — 公开 API 的破坏性变更必须走大版本号

## 模块结构

```
GdxCore/
└── core/    ← 唯一模块，发布为 com.github.shikeik.GdxCore:core
```

## 使用方式

### mavenLocal（开发环境）

```gradle
dependencies {
    api 'com.github.shikeik.GdxCore:core:1.0.0'
}
```

### JitPack（远端）

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    api 'com.github.shikeik.GdxCore:core:1.0.0'
}
```

## 版本

| 版本 | 说明 |
|------|------|
| 1.0.0 | 架构重构：移除 netcode 模块（独立为 GdxNetcode）、移除 chat 业务逻辑、确立纯框架定位 |

## License

Apache License 2.0
