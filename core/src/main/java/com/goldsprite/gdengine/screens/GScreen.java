package com.goldsprite.gdengine.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.math.Vector2;
import com.goldsprite.gdengine.PlatformImpl;
import com.goldsprite.gdengine.neonbatch.NeonBatch;
import com.goldsprite.gdengine.neonbatch.NeonStage;
import com.goldsprite.gdengine.ui.widget.BaseDialog;
import java.util.Stack;
import com.goldsprite.gdengine.log.DLog;


/**
 * 创建:
 * <br/>- 设置视口, 重写该方法: initViewport(){ setViewport(...) }
 * <br/>- 添加事件处理器: getImp().addInputProcessor(...)
 */
public abstract class GScreen extends ScreenAdapter {
	protected NeonStage stage;
	protected NeonBatch batch;
	private final Vector2 viewSize = new Vector2();
	private final Vector2 viewCenter = new Vector2();
	private final Vector2 worldSize = new Vector2();
	private final Vector2 worldCenter = new Vector2();
	private final Vector2 graphicSize = new Vector2();
	private final Color clearScreenColor = Color.valueOf("#BBBBBB");
	private final Color screenBackColor = Color.valueOf("#404040");
	protected ScreenManager screenManager;
	protected InputMultiplexer imp;
	protected boolean initialized = false;
	protected boolean isWorldCameraInitialized = false;
	protected boolean visible = true;
	Vector2 tmpCoord = new Vector2();
	//绘制底色背景
	private boolean drawScreenBack = true;

	// [新增] UI 视口引用 (子类可覆盖)
	protected Viewport uiViewport;
	// [新增] 世界相机：用于游戏场景渲染，与 UI 分离
	protected OrthographicCamera worldCamera = new OrthographicCamera();
	// [新增] 世界缩放比例 (默认 1:1，数值越小世界视野越小/像素感越强)
	protected float worldScale = 1.0f;

	// [新增] 配置项：Resize 时是否自动将世界相机重置到中心
	// 默认 true 保持向后兼容，SkeletonVisualScreen 可以设为 false
	protected boolean autoCenterWorldCamera = false;

	//ExampleGScreen Logic
	// 1. 定义基准尺寸 (540p)
	protected float uiViewportScale = PlatformImpl.isAndroidUser() ? 2f : 1.3f; // 保持原本的缩放系数
	protected float viewSizeShort = 540f;
	protected float viewSizeLong = 960f;

	public ScreenManager.Orientation getOrientation() {
		return PlatformImpl.defaultOrientation;
	}

	// [新增] Dialog 管理栈
	protected final Stack<BaseDialog> dialogStack = new Stack<>();

	public void pushDialog(BaseDialog dialog) {
		dialogStack.push(dialog);
	}

	public void popDialog(BaseDialog dialog) {
		dialogStack.remove(dialog);
	}

	public boolean handleBackKey() {
		// 1. 优先关闭顶层 Modal Dialog
		if (!dialogStack.isEmpty()) {
			BaseDialog top = dialogStack.peek();
			if (top != null && top.hasParent()) { // 确保 Dialog 还在 Stage 上
				top.hide(); // 调用 hide/close，它会触发 remove -> popDialog
				return true; // 消耗 Back 键
			} else {
				// 清理无效引用
				dialogStack.pop();
				return handleBackKey(); // 递归重试
			}
		}
		return false; // 默认不处理，交由 ScreenManager 处理
	}

	/**
	 * 空参构造, 留给反射调用
	 */
	public GScreen() {
	}

	public GScreen(ScreenManager screenManager) {
		this();
		this.screenManager = screenManager;
	}

	public void create() {
	}

	public void initialize() {
		if (initialized) return;
		init();
		initialized = true;
	}

	//初始化一些配置
	private void init() {
		// 调用可重写的初始化方法，代替直接实例化
		initViewport();
		resizeWorldCamera(autoCenterWorldCamera);// 这里更新数据create才能拿到正确相机数据

		stage = new NeonStage(uiViewport);
		batch = new NeonBatch();

		create();
	}

	// 3. 智能初始化视口 (接管 GScreen 的 initViewport)
	protected void initViewport() {
		float w, h;
		int screenW, screenH;
		screenW = Gdx.graphics.getWidth();
		screenH = Gdx.graphics.getHeight();
		int sLong = Math.max(screenW, screenH);
		int sShort = Math.min(screenW, screenH);
		if (getOrientation() == ScreenManager.Orientation.Landscape) {
			w = viewSizeLong;
			h = viewSizeShort;
			screenW = sLong;
			screenH = sShort;
		} else {
			w = viewSizeShort;
			h = viewSizeLong;
			screenW = sShort;
			screenH = sLong;
		}

		// 自动应用缩放系数
		uiViewport = new ExtendViewport(w * uiViewportScale, h * uiViewportScale);
		//Debug.log("1ui视口宽高: %s", getViewSize());

		uiViewport.update(screenW, screenH, true);
		//Debug.log("2ui视口宽高: %s", getViewSize());

	}

	public boolean isInitialized() {
		return initialized;
	}

	public ScreenManager getScreenManager() {
		return screenManager;
	}

	public void setScreenManager(ScreenManager screenManager) {
		this.screenManager = screenManager;
	}

	public InputMultiplexer getImp() {
		return imp;
	}

	public void setImp(InputMultiplexer imp) {
		this.imp = imp;
	}

	public Viewport getUIViewport() {
		return uiViewport;
	}

	/**
	 * 获取 UI 相机 (Viewport 绑定的相机)
	 */
	public OrthographicCamera getUICamera() {
		return (OrthographicCamera) getUIViewport().getCamera();
	}

	/**
	 * [新增] 获取世界相机
	 */
	public OrthographicCamera getWorldCamera() {
		return worldCamera;
	}

	/**
	 * [新增] 设置世界缩放比例 (影响 worldCamera 的视口数值大小)
	 */
	public void setWorldScale(float scale) {
		this.worldScale = scale;
		resizeWorldCamera(autoCenterWorldCamera);
	}

	public Vector2 getViewSize() { return viewSize.set(getUIViewport().getWorldWidth(), getUIViewport().getWorldHeight()); }
	public Vector2 getViewCenter() { return viewCenter.set(getUIViewport().getWorldWidth() / 2, getUIViewport().getWorldHeight() / 2); }
	public Vector2 getWorldSize() { return worldSize.set(getWorldCamera().viewportWidth, getWorldCamera().viewportHeight); }
	public Vector2 getWorldCenter() { return worldCenter.set(getWorldCamera().viewportWidth / 2, getWorldCamera().viewportHeight / 2); }

	public Vector2 getGraphicSize() {
		return graphicSize.set(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
	}

	// [重命名] 原 screenToWorldCoord -> screenToUICoord
	// 明确语义：这是转换到 UI Viewport 坐标系
	public Vector2 screenToUICoord(float x, float y) {
		return screenToUICoord(tmpCoord.set(x, y));
	}

	public Vector2 screenToUICoord(Vector2 screenCoord) {
		// 将反转后的屏幕坐标转换为世界坐标 (UI Viewport)
		Vector3 worldCoordinates = new Vector3(screenCoord.x, screenCoord.y, 0);
		getUIViewport().unproject(worldCoordinates);
		// 获取转换后的世界坐标
		screenCoord.x = worldCoordinates.x;
		screenCoord.y = worldCoordinates.y;
		return screenCoord;
	}

	// [新增] 真正的 screenToWorldCoord (使用 worldCamera)
	public Vector2 screenToWorldCoord(float x, float y) {
		return screenToWorldCoord(new Vector2(x, y));
	}

	public Vector2 screenToWorldCoord(Vector2 screenCoord) {
		if (worldCamera == null) return screenCoord;
		Vector3 worldCoordinates = new Vector3(screenCoord.x, screenCoord.y, 0);
		// 使用 worldCamera 进行转换，注意需要传入当前的屏幕视口参数
		worldCamera.unproject(worldCoordinates, getUIViewport().getScreenX(), getUIViewport().getScreenY(), getUIViewport().getScreenWidth(), getUIViewport().getScreenHeight());
		screenCoord.x = worldCoordinates.x;
		screenCoord.y = worldCoordinates.y;
		return screenCoord;
	}

	public Vector2 worldToScreenCoord(Vector2 worldCoord) {
		// 将反转后的屏幕坐标转换为世界坐标
		Vector3 screenCoordinates = new Vector3(worldCoord.x, worldCoord.y, 0);
		getUIViewport().project(screenCoordinates);
		// 获取转换后的世界坐标
		worldCoord.x = screenCoordinates.x;
		worldCoord.y = screenCoordinates.y;
		return worldCoord;
	}

	public Vector2 screenToViewCoord(float x, float y, Viewport viewport) {
		return screenToViewCoord(tmpCoord.set(x, y), viewport);
	}

	public Vector2 screenToViewCoord(Vector2 screenCoord, Viewport viewport) {
		// 将反转后的屏幕坐标转换为世界坐标
		Vector3 viewCoordinates = new Vector3(screenCoord.x, screenCoord.y, 0);
		viewport.unproject(viewCoordinates);
		// 获取转换后的世界坐标
		screenCoord.set(viewCoordinates.x, viewCoordinates.y);
		return screenCoord;
	}

	public Color getClearScreenColor() {
		return clearScreenColor;
	}

	public Color getScreenBackColor() {
		return screenBackColor;
	}

	protected boolean isDrawScreenBack() {
		return drawScreenBack;
	}

	protected void setDrawScreenBack(boolean drawScreenBack) {
		this.drawScreenBack = drawScreenBack;
	}

	protected void drawScreenBack() {
		stage.getBatch().setProjectionMatrix(uiViewport.getCamera().combined);
		stage.getBatch().begin();
		stage.getBatch().drawRect(0, 0, getViewSize().x, getViewSize().y, 0, 0, screenBackColor, true);
		stage.getBatch().end();
	}

	@Override
	public void render(float delta) {
		ScreenUtils.clear(getClearScreenColor());

		if (drawScreenBack)
			drawScreenBack();

		render0(delta);
	}

	public void render0(float delta) {
	}

	protected void resizeWorldCamera(boolean centerCamera) {
		if (worldCamera != null) {
			// 只更新视口大小 (Zoom 也会被保留，因为 zoom 是 camera 的属性，不随 viewportWidth 改变)
			worldCamera.viewportWidth = getUIViewport().getWorldWidth() * worldScale;
			worldCamera.viewportHeight = getUIViewport().getWorldHeight() * worldScale;

			if(centerCamera) {
				// 只有明确要求居中时才重置位置
				worldCamera.position.set(
					worldCamera.viewportWidth/2f,
					worldCamera.viewportHeight/2f, 0);
			}
			worldCamera.update();
		}
	}

	@Override
	public void resize(int width, int height) {
		if (getUIViewport() != null) {
			// 1. 更新 UI 视口 (自动居中 UI 相机), 左下角为0,0
			getUIViewport().update(width, height, true);

			resizeWorldCamera(autoCenterWorldCamera);
		}
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public void show() {
		visible = true;
		getScreenManager().enableInput(getImp());

		getScreenManager().setOrientation(getOrientation());

		//切换时刷新屏幕视口
		resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
	}

	@Override
	public void hide() {
		visible = false;
		getScreenManager().disableInput(getImp());
	}

	/**
	 * 获取 UI Stage。
	 * 子类如果使用了 Stage (例如 MainMenuScreen, GameScreen), 应该重写此方法返回其 Stage。
	 * 默认返回 null。
	 */
	public Stage getStage() {
		return stage;
	}

	@Override
	public void dispose() {
	}

}
