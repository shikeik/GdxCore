feat: Phase2 - 新增CommandRegistry命令系统, LogPanel命令输入栏

LogPanel统一日志控制台 - 第2阶段(命令系统):
- 新增 CommandRegistry 运行时命令注册表
  - register / execute / getSuggestions / getHelp / hasCommand / unregister
  - 执行异常自动捕获, 命令名大小写不敏感
- LogPanel 命令输入栏: 回车/Send执行, 上下箭头浏览历史
- 内置命令: help / clear / filter / level / search
  - filter +Tag1 -Tag2 / filter all 动态切换Tag过滤
  - level warn error / level all 切换等级显示
- 新增 CommandRegistryTest: 13个测试 (注册/执行/补全/help/异常/大小写)
- 全部 39 个测试通过
- 新增GDEngineConfig解耦接口(替代项目层BuildConfig直接引用)
- 新增SelectionNavigator接口(替代项目层InputManager直接引用)
- ThreadedDownload.download()参数化(移除硬编码URL)
- 配置maven-publish发布到mavenLocal/JitPack
- GAV: com.github.shikeik.GdxCore:core/netcode:0.9.0
