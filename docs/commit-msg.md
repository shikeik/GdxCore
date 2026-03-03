feat: Phase3 - DebugConsole集成LogPanel, LOG标签页全面升级

LogPanel统一日志控制台 - 第3阶段(DebugConsole集成):
- DebugConsole LOG标签页: logLabel+logScroll → LogPanel实例
- 移除 header 中的 AutoScroll / Clear 按钮 (已内置于 LogPanel)
- contentContainer 类型: Container<ScrollPane> → Container<Actor>
- showTab() 参数: ScrollPane → Actor
- refreshData() 移除旧 logLabel 手动更新逻辑 (LogPanel.act() 自管理)
- 保留全部特色: 抽屉动画 / FPS按钮 / INTRO+INFO标签 / 拖拽调整大小
- DebugConsole 代码从 326 行精简至 287 行
- 全部 39 个测试通过, 无回归
- 新增GDEngineConfig解耦接口(替代项目层BuildConfig直接引用)
- 新增SelectionNavigator接口(替代项目层InputManager直接引用)
- ThreadedDownload.download()参数化(移除硬编码URL)
- 配置maven-publish发布到mavenLocal/JitPack
- GAV: com.github.shikeik.GdxCore:core/netcode:0.9.0
