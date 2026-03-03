package com.goldsprite.gdengine;

/**
 * 平台差异化配置中心。
 * <p>
 * 所有与平台相关的视觉/交互参数从此处获取，外部不再直接判断 isAndroid。
 * <p>
 * 首次访问时根据 {@link PlatformImpl#isAndroidUser()} 自动选择预设值。
 * 外部可通过 {@link #override(PlatformProfile)} 或直接修改实例字段来自定义。
 *
 * <pre>
 * // 方式 1: 自动检测平台（默认行为，无需代码）
 *
 * // 方式 2: 微调参数
 * PlatformProfile.get().uiViewportScale = 1.5f;
 *
 * // 方式 3: 完全自定义
 * PlatformProfile custom = new PlatformProfile();
 * custom.uiViewportScale = 2.5f;
 * PlatformProfile.override(custom);
 * </pre>
 *
 * <b>注意</b>: {@code get()} 首次调用依赖 {@code Gdx.app} 已初始化，
 * 必须在 {@code Application.create()} 之后调用。
 */
public class PlatformProfile {

    // ══════════════ 单例 ══════════════

    private static PlatformProfile instance;

    /**
     * 获取当前平台配置实例。首次调用时自动根据平台类型创建默认配置。
     */
    public static PlatformProfile get() {
        if (instance == null) instance = createDefault();
        return instance;
    }

    /**
     * 覆盖当前配置实例（例如在 Application.create() 中注入自定义配置）。
     */
    public static void override(PlatformProfile profile) {
        instance = profile;
    }

    // ══════════════ 视口相关 ══════════════

    /** UI 视口缩放系数（控制 UI 元素在屏幕上的物理大小） */
    public float uiViewportScale = 1.3f;

    /** UI 基准短边尺寸 */
    public float baseShort = 540f;

    /** UI 基准长边尺寸 */
    public float baseLong = 960f;

    // ══════════════ 字体相关 ══════════════

    /** 全局默认 Label fontScale */
    public float defaultFontScale = 1.0f;

    /** Dialog 标题 fontScale */
    public float dialogTitleFontScale = 0.95f;

    /** 日志面板 fontScale */
    public float logPanelFontScale = 0.75f;

    /** 代码编辑器基础 fontScale */
    public float codeEditorFontScale = 1.3f;

    /** IDE Console / DebugConsole 视口缩放系数 */
    public float ideConsoleViewportScale = 2.0f;

    // ══════════════ 交互尺寸相关 ══════════════

    /** SplitPane 拖拽条厚度 */
    public float splitBarThickness = 8f;

    /** 触摸目标最小尺寸（dp） */
    public float minTouchTarget = 32f;

    // ══════════════ 工厂方法 ══════════════

    /**
     * 根据当前平台自动生成默认配置。
     * Android 端使用更大的 UI 视口、更粗的交互元素。
     */
    private static PlatformProfile createDefault() {
        PlatformProfile p = new PlatformProfile();
        if (PlatformImpl.isAndroidUser()) {
            p.uiViewportScale = 2.0f;
            p.ideConsoleViewportScale = 1.4f;
            p.splitBarThickness = 15f;
            p.minTouchTarget = 48f;
        }
        // 桌面端保持字段默认值
        return p;
    }
}
