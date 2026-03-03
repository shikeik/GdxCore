package com.goldsprite.gdengine.screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.goldsprite.gdengine.PlatformImpl;

/**
 * 使用：
 * * 创建:
 * * * 实例化: ScreenManager.getInstance().addScreen(new YourScreen()).setLaunchScreen(YourScreen.class);
 * * * 渲染: ScreenManager.getInstance().render();
 * * * 重大小: ScreenManager.getInstance().resize(width, height);
 * * * 回收: ScreenManager.getInstance().dispose();
 * * 切换屏幕:
 * * * 使用 getScreenManager().goScreen(TargetScreen.class) 进入屏幕（可返回）
 * * * 使用 getScreenManager().showScreen(TargetScreen.class) 仅显示屏幕（不可返回）
 * * * 使用 getScreenManager().replaceScreen(TargetScreen.class) 替换屏幕（重新创建）
 * * 返回上个屏幕:
 * * * 在屏幕历史堆栈有屏幕时 getScreenManager().popLastScreen() 来返回上个屏幕
 * * 转场效果:
 * * * 使用 playTransition(Runnable onMiddle) 来执行带黑屏过渡的操作
 */
public class ScreenManager implements Disposable {

	// 1. 定义屏幕方向枚举
	public enum Orientation {
		Portrait, // 竖屏
		Landscape // 横屏
		}
	
	// 转场状态
	private enum TransitionState {
		NONE,
		FADE_OUT, // 透明 -> 黑
		FADE_IN,   // 黑 -> 透明
		LOADING_WAIT, // 等待加载完成 (显示 LoadingOverlay)
		OVERLAY_FADE  // 无等待渐变（立即执行操作，黑幕渐出）
	}

	// 2. 定义回调接口 (底层不依赖 Android/Lwjgl)
	public static Consumer<Orientation> orientationChanger;
	public static List<Runnable> exitGame = new ArrayList<>();//声明退出游戏事件回调，需要在各平台自身实现
	
	// [新增] 输入系统钩子，解耦具体输入实现
	public static Runnable inputUpdater;
	public static Supplier<Boolean> backKeyTrigger = () -> 
		Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE);

	// [新增] Loading 渲染器接口
	public interface LoadingRenderer {
		void render(float delta, float alpha);
		void setText(String text);
	}
	private LoadingRenderer loadingRenderer;

	private static ScreenManager instance;
	private final Map<Class<?>, GScreen> screens = new HashMap<>();
	private InputMultiplexer imp;
	//屏幕历史堆栈
	private final Stack<GScreen> screenHistory = new Stack<>();
	private GScreen curScreen;
	private GScreen launchScreen;
	private boolean popping;//用于标记是否为弹出历史屏幕状态
	private Viewport viewport;//统一视口

	// 转场相关变量
	private TransitionState transitionState = TransitionState.NONE;
	private float transitionDuration = 0.5f;
	private float transitionTime = 0f;
	private Runnable onTransitionMiddle;
	private Runnable onTransitionEnd;
	
	// Loading 转场相关
	private boolean loadingTaskFinished = false;
	private float loadingMinDuration = 0f;
	private float loadingElapsedTime = 0f;
	
	private ShapeRenderer shapeRenderer;

	public ScreenManager() {
		this(new InputMultiplexer());
	}

	public ScreenManager(InputMultiplexer imp) {
		this(new ScreenViewport(), imp);
	}
	public ScreenManager(Viewport viewport) {
		this(viewport, new InputMultiplexer());
	}

	public ScreenManager(Viewport viewport, InputMultiplexer imp) {
		instance = this;
		this.viewport = viewport;
		//关键 这里需要手动update才能触发worldWidth/Height初始化赋值，之后GScreen才能拿到视口数据
		viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
		initInputHandler(imp);
		
		// 初始化转场渲染器
		shapeRenderer = new ShapeRenderer();
	}


	/**
	 * 单例屏幕管理器
	 */
	public static synchronized ScreenManager getInstance() {
		return instance;
	}

	// 3. 增加切换方法
	public void setOrientation(Orientation orientation) {
		if (orientationChanger != null) {
			orientationChanger.accept(orientation);
			//curScreen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		}
		// 注意：实际视口 resize 会在 resize() 回调中被触发，这里不需要手动改 Viewport
	}

	private void initInputHandler(InputMultiplexer imp) {
		this.imp = imp;

		//设置到gdx输入管线
		if (Gdx.input.getInputProcessor() == null) Gdx.input.setInputProcessor(imp);
		
		// 移除旧的 InputAdapter，改为在 render 中轮询以支持所有输入设备 (包括手柄)
		InputAdapter defaultHandler = new InputAdapter() {
			boolean isFullscreen;

			public boolean keyDown(int keyCode) {
				if(keyCode == Input.Keys.F11) {
					isFullscreen = !isFullscreen;
					PlatformImpl.fullScreenEvent.accept(isFullscreen);
				}
				return false;
			}
		};
		// 注意：imp.addProcessor 顺序很重要。后添加的在数组末尾。
		// LibGDX InputMultiplexer 默认是按添加顺序处理 (0, 1, 2...)
		// 我们希望 Stage (UI) 先处理，然后是 InputManager (Game Logic)，最后是 defaultHandler。
		// GScreen.show() 会调用 enableInput(screenImp)，那里会把 screenImp 加到 imp 中。
		// 如果 screenImp 包含 Stage，它会被加入。
		// 为了确保 defaultHandler 不拦截其他事件，它只处理 F11，返回 false，这没问题。
		imp.addProcessor(defaultHandler);
	}

	public InputMultiplexer getImp() {
		return imp;
	}

	public Viewport getViewport() {
		return viewport;
	}

	public ScreenManager setViewport(Viewport viewport) {
		this.viewport = viewport;
		return this;
	}

	public void setLoadingRenderer(LoadingRenderer renderer) {
		this.loadingRenderer = renderer;
	}

	/**
	 * 开始一个转场效果 (淡入淡出 - Fade)
	 * <p>
	 * 适用于无需加载资源的轻量级场景切换（如菜单跳转）。
	 * 只有黑屏渐变，没有加载动画。
	 * </p>
	 * @param onMiddle 当屏幕完全变黑时执行的操作 (通常用于切换屏幕或重置关卡)
	 */
	public void playTransition(Runnable onMiddle) {
		playTransition(onMiddle, null);
	}
	
	/**
	 * 执行带加载动画的转场 (Loading)
	 * <p>
	 * 适用于需要加载资源或生成地图的场景（如进入地牢、切换楼层）。
	 * 屏幕变黑后会显示加载动画（如小人跑动）和提示文本，直到加载任务完成。
	 * </p>
	 * @param loader 异步加载任务，接受一个 finishCallback。当加载完成时必须调用此 callback。
	 * @param tipText 加载时显示的提示文本
	 * @param minDuration 最小转场持续时间 (秒)，防止加载过快导致动画闪烁。
	 */
	public void playLoadingTransition(Consumer<Runnable> loader, String tipText, float minDuration) {
		if (transitionState != TransitionState.NONE) return;
		
		this.loadingMinDuration = minDuration;
		this.loadingElapsedTime = 0f;
		this.loadingTaskFinished = false;

		if (loadingRenderer != null) {
			loadingRenderer.setText(tipText);
		}
		
		playTransition(() -> {
			// 进入 LOADING_WAIT 状态，而不是立即 FADE_IN
			transitionState = TransitionState.LOADING_WAIT;
			
			// 执行加载任务
			if (loader != null) {
				loader.accept(() -> {
					loadingTaskFinished = true;
				});
			} else {
				loadingTaskFinished = true;
			}
		});
	}

	/**
	 * 开始一个转场效果 (淡入淡出)
	 * @param onMiddle 当屏幕完全变黑时执行的操作
	 * @param onEnd 当转场完全结束时执行的操作
	 */
	public void playTransition(Runnable onMiddle, Runnable onEnd) {
		if (transitionState != TransitionState.NONE) return; // 已经在转场中

		this.transitionState = TransitionState.FADE_OUT;
		this.transitionTime = 0f;
		this.onTransitionMiddle = onMiddle;
		this.onTransitionEnd = onEnd;
	}

	/**
	 * 无等待式渐变转场 (Overlay Fade)
	 * <p>
	 * 适用于不需要等待的轻量操作（如切换地牢层级、重置地图）。
	 * 立即执行操作，然后从全黑覆盖层开始渐变淡出，期间游戏正常运行不阻塞。
	 * </p>
	 * @param action 立即执行的操作（如关卡重建）
	 */
	public void playOverlayFade(Runnable action) {
		playOverlayFade(action, transitionDuration);
	}

	/**
	 * 无等待式渐变转场 (Overlay Fade)
	 * @param action 立即执行的操作
	 * @param fadeDuration 渐变持续时间（秒）
	 */
	public void playOverlayFade(Runnable action, float fadeDuration) {
		// 立即执行操作（不等待淡出）
		if (action != null) action.run();

		// 从全黑开始渐变淡出
		this.transitionState = TransitionState.OVERLAY_FADE;
		this.transitionTime = 0f;
		this.overlayFadeDuration = fadeDuration;
	}

	/** 无等待渐变的持续时长 */
	private float overlayFadeDuration = 0.5f;
	
	public boolean isTransitioning() {
		return transitionState != TransitionState.NONE;
	}
	
	/**
	 * 渲染(已初始化的)当前屏幕
	 */
	public void render() {
		// 全局输入更新
		if (inputUpdater != null) inputUpdater.run();
		
		// 全局返回键逻辑 (转场期间禁用)
		if (transitionState == TransitionState.NONE && backKeyTrigger != null && backKeyTrigger.get()) {
			GScreen current = getCurScreen();
			boolean consumed = false;
			if (current != null) {
				consumed = current.handleBackKey();
			}
			
			if (!consumed) {
				if (!popLastScreen()) {
					if (exitGame != null && !exitGame.isEmpty()) exitGame.forEach(r -> r.run());
				}
			}
		}

		if (curScreen == null) return; // 确保 curScreen 不为 null

		if (!curScreen.isInitialized()) return;

		float delta = Gdx.graphics.getDeltaTime();
		curScreen.getUIViewport().apply();
		curScreen.render(delta);
		
		// 渲染转场效果
		renderTransition(delta);
	}
	
	/**
	 * 获取当前屏幕的 Stage (便捷方法)
	 */
	public Stage getStage() {
		if (curScreen != null) {
			return curScreen.getStage();
		}
		return null;
	}

	private void renderTransition(float delta) {
		if (transitionState == TransitionState.NONE) return;

		transitionTime += delta;
		float alpha = 0f;

		if (transitionState == TransitionState.FADE_OUT) {
			alpha = Math.min(1f, transitionTime / transitionDuration);
			if (transitionTime >= transitionDuration) {
				// Fade Out 完成，执行中间操作
				if (onTransitionMiddle != null) {
					try {
						onTransitionMiddle.run();
					} catch (Exception e) {
						System.err.println("[ScreenManager] 转场中间回调异常: " + e.getMessage());
						e.printStackTrace();
					}
					onTransitionMiddle = null; // 无论成功失败都置null，防止每帧重复抛异常
				}
				
				// 如果中间回调没有改变状态（例如改为 LOADING_WAIT），则默认切换到 FADE_IN
				if (transitionState == TransitionState.FADE_OUT) {
					transitionState = TransitionState.FADE_IN;
				}
				
				transitionTime = 0f;
				alpha = 1f; // 保持全黑一帧
			}
		} else if (transitionState == TransitionState.LOADING_WAIT) {
			// 保持全黑，显示加载动画
			alpha = 1f;
			loadingElapsedTime += delta;
			
			if (loadingElapsedTime >= loadingMinDuration && loadingTaskFinished) {
				// 加载完成且满足最小时长，开始 Fade In
				transitionState = TransitionState.FADE_IN;
				transitionTime = 0f;
			}
		} else if (transitionState == TransitionState.FADE_IN) {
			alpha = 1f - Math.min(1f, transitionTime / transitionDuration);
			if (transitionTime >= transitionDuration) {
				// Fade In 完成
				transitionState = TransitionState.NONE;
				if (onTransitionEnd != null) {
					onTransitionEnd.run();
					onTransitionEnd = null;
				}
				alpha = 0f;
			}
		} else if (transitionState == TransitionState.OVERLAY_FADE) {
			// 无等待渐变：从全黑直接淡出，不阻塞游戏
			alpha = 1f - Math.min(1f, transitionTime / overlayFadeDuration);
			if (transitionTime >= overlayFadeDuration) {
				transitionState = TransitionState.NONE;
				alpha = 0f;
			}
		}

		if (alpha > 0) {
			Gdx.gl.glEnable(GL20.GL_BLEND);
			Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
			
			Camera cam = curScreen.getUIViewport().getCamera();
			shapeRenderer.setProjectionMatrix(cam.combined);
			shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
			shapeRenderer.setColor(0, 0, 0, alpha);
			
			// 绘制一个覆盖相机的巨大矩形，确保覆盖全屏（包括可能的黑边区域，如果视口设置允许）
			// 但通常我们只需要覆盖视口区域。为了保险，画大一点。
			float w = curScreen.getUIViewport().getWorldWidth();
			float h = curScreen.getUIViewport().getWorldHeight();
			shapeRenderer.rect(cam.position.x - w, cam.position.y - h, w * 2, h * 2);
			
			shapeRenderer.end();
			
			// 如果处于 LOADING_WAIT 状态，绘制加载动画
			if (transitionState == TransitionState.LOADING_WAIT && loadingRenderer != null) {
				loadingRenderer.render(delta, alpha);
			}
			
			Gdx.gl.glDisable(GL20.GL_BLEND);
		}
	}

	/**
	 * 释放资源
	 */
	@Override
	public void dispose() {
		if (shapeRenderer != null) shapeRenderer.dispose();
		for (GScreen screen : screens.values()) {
			if (screen.isInitialized())
				screen.dispose();
		}
	}

	public void resize(int width, int height) {
		if(curScreen != null) curScreen.resize(width, height);
	}

	/**
	 * 添加并配置游戏屏幕
	 */
	public ScreenManager addScreen(GScreen screen) {
		Class<?> key = screen.getClass();
		if (!screens.containsKey(key)) {
			screens.put(key, screen);
			screen.setScreenManager(this);
			screen.setImp(new InputMultiplexer());
		}
		return this;
	}

	private void removeScreen(Class<? extends GScreen> key) {
		screens.remove(key);
	}

	/**
	 * 通过键获取游戏屏幕
	 */
	public GScreen getScreen(Class<?> key) {
		if (!screens.containsKey(key)) throw new RuntimeException("未找到此屏幕." + key.getSimpleName());
		return screens.get(key);
	}

	public boolean existsScreen(Class<?> key) {
		return screens.containsKey(key);
	}

	/**
	 * 获取当前屏幕
	 */
	public GScreen getCurScreen() {
		return curScreen;
	}

	/**
	 * 进入屏幕（入栈管理，可返回）。
	 * 如果已是当前屏幕，直接忽略（幂等操作）。
	 */
	public ScreenManager goScreen(Class<? extends GScreen> key) {
		if (!existsScreen(key)) {
			try {
				addScreen(key.getConstructor().newInstance());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return goScreen(getScreen(key));
	}

	/**
	 * 进入屏幕对象（入栈管理，可返回）。
	 * 如果已是当前屏幕，直接忽略（幂等操作）。
	 */
	public ScreenManager goScreen(GScreen screen) {
		// 相同屏幕直接忽略
		if (this.curScreen == screen) {
			return this;
		}

		_initializeScreen(screen);

		// 隐藏上个屏幕并入栈
		if (this.curScreen != null) {
			this.curScreen.hide();
			if (!popping) screenHistory.push(curScreen);
		}

		this.curScreen = screen;
		this.curScreen.show();
		return this;
	}

	/**
	 * 仅显示屏幕（不入栈，不可返回）。
	 * 用于临时显示（如模态对话框、加载界面等）。
	 */
	public ScreenManager showScreen(Class<? extends GScreen> key) {
		if (!existsScreen(key)) {
			try {
				addScreen(key.getConstructor().newInstance());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return showScreen(getScreen(key));
	}

	/**
	 * 仅显示屏幕对象（不入栈，不可返回）。
	 */
	public ScreenManager showScreen(GScreen screen) {
		_initializeScreen(screen);

		// 隐藏上个屏幕但不入栈
		if (this.curScreen != null) {
			this.curScreen.hide();
			// 注意：这里不入栈，所以 ESC/返回 会直接退出应用而不是回到上个屏幕
		}

		this.curScreen = screen;
		this.curScreen.show();
		return this;
	}

	/**
	 * 替换屏幕（销毁旧实例，创建新实例）。
	 * 用于需要完全重置状态的场景。
	 */
	public ScreenManager replaceScreen(Class<? extends GScreen> key) {
		if (existsScreen(key)) {
			getScreen(key).dispose();
			removeScreen(key);
		}
		return goScreen(key);
	}

	/**
	 * 私有方法：屏幕初始化和依赖注入。
	 */
	private void _initializeScreen(GScreen screen) {
		// 自动依赖注入
		if (screen.getScreenManager() == null) {
			screen.setScreenManager(this);
		}
		if (screen.getImp() == null) {
			screen.setImp(new InputMultiplexer());
		}
		// 如果屏幕未准备则初始化
		if (!screen.isInitialized()) {
			screen.initialize();
		}
	}

	/**
	 * 返回上个屏幕（弹出栈）。
	 * @return true 如果成功返回到栈中的屏幕，false 如果栈为空
	 */
	public boolean popLastScreen() {
		if (screenHistory.isEmpty()) return false;
		GScreen lastScreen = screenHistory.pop();
		popping = true;
		goScreen(lastScreen);  // 使用 goScreen 确保不再次入栈
		popping = false;
		return true;
	}

	public GScreen getLaunchScreen() {
		return launchScreen;
	}

	public ScreenManager setLaunchScreen(Class<? extends GScreen> key) {
		setLaunchScreen(getScreen(key));
		return this;
	}

	public ScreenManager setLaunchScreen(GScreen screen) {
		launchScreen = screen;
		if (!screen.isInitialized())
			goScreen(launchScreen);
		return this;
	}

	public void enableInput(InputMultiplexer screenImp) {
		// 追加到末尾，确保全局覆盖层 (DLog.stage, toastStage) 始终在最前面
		// 这样 FPS 按钮、Toast 等全局 UI 优先接收触摸事件
		imp.addProcessor(screenImp);
	}

	public void disableInput(InputMultiplexer screenImp) {
		imp.removeProcessor(screenImp);
	}

	/**
	 * 清空屏幕历史栈
	 */
	public void clearStack() {
		screenHistory.clear();
	}

	/**
	 * 回退到指定类型的屏幕（如果有的话）。
	 * 会一直 pop 直到栈顶是目标类型的屏幕，然后将其作为当前屏幕。
	 * @return true 如果成功回退，false 如果栈中未找到该类型屏幕
	 */
	public boolean popTo(Class<? extends GScreen> targetScreenClass) {
		if (curScreen != null && targetScreenClass.isInstance(curScreen)) return true;

		int targetIndex = -1;
		// 从栈顶向下查找
		for (int i = screenHistory.size() - 1; i >= 0; i--) {
			if (targetScreenClass.isInstance(screenHistory.get(i))) {
				targetIndex = i;
				break;
			}
		}

		if (targetIndex == -1) return false;

		// 移除目标之上的所有屏幕
		while (screenHistory.size() > targetIndex + 1) {
			screenHistory.pop();
		}

		// 此时栈顶就是目标屏幕，弹出并显示
		return popLastScreen();
	}

	/**
	 * 获取当前屏幕栈信息
	 * 格式: ScreenA -> ScreenB -> ScreenC
	 */
	public String getScreenStackInfo() {
		StringBuilder sb = new StringBuilder();
		if (screenHistory.isEmpty()) {
			//sb.append("[Empty]");
		} else {
			for (int i = 0; i < screenHistory.size(); i++) {
				sb.append(screenHistory.get(i).getClass().getSimpleName());
				sb.append(" -> ");
			}
		}
		
		if (curScreen != null) {
			sb.append(curScreen.getClass().getSimpleName());
		} else {
			sb.append("null");
		}
		
		return sb.toString();
	}
}
