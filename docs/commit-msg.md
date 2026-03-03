feat: Phase0 - 新增LogEntry结构化日志条目, DLog双写支持

LogPanel统一日志控制台 - 第0阶段(数据层):
- 新增 LogEntry 结构化日志条目 (level/tag/time/message/formatted + matchesSearch)
- DLog.GdxUiOutput 双写: logMessages + logEntries 并行存储
- 新增 DLog.getLogEntries() / clearAllLogs() 静态方法
- DebugConsole / IDEConsole 清除按钮统一改用 clearAllLogs()
- 新增 JUnit 4.13.2 测试依赖, 17个单元测试全部通过
- 新增GDEngineConfig解耦接口(替代项目层BuildConfig直接引用)
- 新增SelectionNavigator接口(替代项目层InputManager直接引用)
- ThreadedDownload.download()参数化(移除硬编码URL)
- 配置maven-publish发布到mavenLocal/JitPack
- GAV: com.github.shikeik.GdxCore:core/netcode:0.9.0
