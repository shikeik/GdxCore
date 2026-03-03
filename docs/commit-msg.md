feat: Phase1 - 新增LogPanel统一日志控制台组件

LogPanel统一日志控制台 - 第1阶段(核心UI组件):
- 新增 LogPanel (extends VisTable) 统一日志控制台面板
  - 搜索栏: 实时大小写不敏感搜索 tag + message
  - 等级过滤按钮: DEBUG/INFO/WARN/ERROR 独立切换, 带颜色指示
  - Tag 过滤面板: All 模式 / 手动多选, 动态收集已出现的 tag
  - 日志显示区: HoverFocusScrollPane + VisLabel, 支持自动滚动
  - Clear 清空, AutoScroll 切换
  - 命令行占位 (Phase 2)
- 公开 API: setDefaultTagFilter / resetTagFilter / setSearchTerm / setAutoScroll
- 新增 LogPanelFilterTest: 9个筛选逻辑测试 (等级/Tag/搜索/组合过滤)
- 全部 26 个测试通过
- 新增GDEngineConfig解耦接口(替代项目层BuildConfig直接引用)
- 新增SelectionNavigator接口(替代项目层InputManager直接引用)
- ThreadedDownload.download()参数化(移除硬编码URL)
- 配置maven-publish发布到mavenLocal/JitPack
- GAV: com.github.shikeik.GdxCore:core/netcode:0.9.0
