chore(project): 初始化GdxCore引擎库项目

从SandTank提取gdengine包为独立多模块Gradle库项目:
- core模块: 日志、屏幕管理、UI组件、资源工具等(48文件)
- netcode模块: 网络联机框架、Supabase集成(21文件)
- 新增GDEngineConfig解耦接口(替代项目层BuildConfig直接引用)
- 新增SelectionNavigator接口(替代项目层InputManager直接引用)
- ThreadedDownload.download()参数化(移除硬编码URL)
- 配置maven-publish发布到mavenLocal/JitPack
- GAV: com.github.shikeik.GdxCore:core/netcode:0.9.0
