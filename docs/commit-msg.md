feat: Phase4 - IDEConsole重构为LogPanel内核, 完成统一日志控制台

LogPanel统一日志控制台 - 第4阶段(IDEConsole替换):
- IDEConsole 内部用 LogPanel 替代手动渲染逻辑
  - 移除 logContent(VisLabel) + scrollPane + clearBtn
  - 新增 getLogPanel() 公开API供外部配置tag过滤等
  - 底部栏预览改用 LogEntry 结构化数据
- 保留折叠/展开外壳行为 (toggle / setExpanded)
- IDEConsole 代码从 130 行精简至 105 行
- 全部 39 个测试通过, 无回归
- 新增GDEngineConfig解耦接口(替代项目层BuildConfig直接引用)
- 新增SelectionNavigator接口(替代项目层InputManager直接引用)
- ThreadedDownload.download()参数化(移除硬编码URL)
- 配置maven-publish发布到mavenLocal/JitPack
- GAV: com.github.shikeik.GdxCore:core/netcode:0.9.0
