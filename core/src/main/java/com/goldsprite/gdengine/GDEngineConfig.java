package com.goldsprite.gdengine;

/**
 * GDEngine 全局配置类。
 * 项目层在启动时调用 init() 注入项目名称和版本号，
 * 取代引擎内部对具体项目 BuildConfig 的直接引用。
 */
public class GDEngineConfig {
	private static String projectName = "GDEngine";
	private static String version = "unknown";

	/** 项目层启动时调用一次，注入项目信息 */
	public static void init(String projectName, String version) {
		GDEngineConfig.projectName = projectName;
		GDEngineConfig.version = version;
	}

	public static String getProjectName() {
		return projectName;
	}

	public static String getVersion() {
		return version;
	}
}
