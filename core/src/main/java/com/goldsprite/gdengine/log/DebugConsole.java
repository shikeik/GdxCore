package com.goldsprite.gdengine.log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import java.util.function.Supplier;

import com.goldsprite.gdengine.assets.ColorTextureUtils;
import com.goldsprite.gdengine.assets.VisUIHelper;
import com.goldsprite.gdengine.ui.widget.HoverFocusScrollPane;
import com.goldsprite.gdengine.ui.widget.SelectableLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;

/**
 * 调试控制台 (抽屉动画版)
 */
public class DebugConsole extends Group {
	private static DebugConsole inst;
	public enum State { COLLAPSED, EXPANDED }
	public State currentState = State.COLLAPSED;

	/** 日志面板工厂 — 下游项目可在构造前设置，注入自定义 LogPanel 替代品 */
	public static Supplier<? extends Actor> logPanelFactory;

	// UI 组件
	private VisTextButton fpsBtn;
	private VisTable panel; // 滑动面板
	private Actor logPanelActor;  // LOG 标签页面板（可被外部替换）
	private SelectableLabel infoLabel, introLabel;
	private ScrollPane infoScroll, introScroll;
	private Container<Actor> contentContainer;

	// 布局配置
	private final float SAFE_PAD; // 安全边距（从 PlatformProfile 获取）
	private final float MIN_HEIGHT = 80f;
	private float panelHeight = 300f; // 面板当前高度设定

	// 动画物理
	private float currentPanelY; // 面板当前的Y坐标
	private float targetPanelY;  // 目标Y坐标
	private final float LERP_SPEED = 15f; // 插值速度

	// 数据刷新
	private float updateTimer = 0;
	private final float REFRESH_RATE = 1/60f; // UI 刷新频率

	// 控制状态

	float resizeHandleHeight = 8;

	private final TextureRegionDrawable backDrawable, back2Drawable, dragDrawable;

	public DebugConsole() {
		inst = this;
		this.SAFE_PAD = com.goldsprite.gdengine.PlatformProfile.get().safeAreaMargin;

		// 1. 资源准备
		backDrawable = ColorTextureUtils.createColorDrawable(Color.valueOf("00000099"));
		back2Drawable = ColorTextureUtils.createColorDrawable(Color.valueOf("00000000"));
		dragDrawable = ColorTextureUtils.createColorDrawable(Color.valueOf("FFAAAAFF"));

		// 2. 初始化子组件
		createFpsButton();
		createPanel();

		// 3. 初始状态：收起
		// 初始位置设为屏幕外，避免第一帧闪烁
		// 这里的 Height 还没 Layout，先给个大数，act() 第一帧会修正
		currentPanelY = 9999f;
		targetPanelY = 9999f;

		switchState(State.COLLAPSED);
	}

	private void createFpsButton() {
		VisTextButton.VisTextButtonStyle fpsStyle = new VisTextButton.VisTextButtonStyle();
		fpsStyle.font = VisUIHelper.cnFont;
		fpsStyle.up = back2Drawable;
		fpsStyle.down = back2Drawable;
		fpsBtn = new VisTextButton("FPS: 0");
		fpsBtn.setStyle(fpsStyle);
		fpsBtn.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					switchState(State.EXPANDED);
				}
			});
		addActor(fpsBtn);
	}

	private void createPanel() {
		panel = new VisTable();
		panel.setBackground(backDrawable);
		panel.setTouchable(Touchable.enabled);

		// --- 顶部栏 ---
		Table header = new Table();
		VisTextButton btnIntro = createTabBtn("INTRO", () -> showTab(introScroll));
		VisTextButton btnLog = createTabBtn("LOG", () -> showTab(logPanelActor));
		VisTextButton btnInfo = createTabBtn("INFO", () -> showTab(infoScroll));

		// [Phase3] AutoScroll / Clear 已内置于 LogPanel，此处仅保留标签页按钮 + 关闭按钮

		VisTextButton btnClose = new VisTextButton(" X ");
		btnClose.setColor(Color.RED);
		btnClose.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					switchState(State.COLLAPSED);
				}
			});

		header.add(btnIntro).width(52).padRight(2);
		header.add(btnLog).width(52).padRight(2);
		header.add(btnInfo).width(52).padRight(2);
		header.add().expandX();
		header.add(btnClose).width(30);
		panel.add(header).growX().height(26).pad(2).row();

		// --- 内容区 ---
		introLabel = new SelectableLabel("", "small"); introLabel.setWrap(true);
		introScroll = new HoverFocusScrollPane(introLabel);
		logPanelActor = logPanelFactory != null ? logPanelFactory.get() : new LogPanel();
		infoLabel = new SelectableLabel("", "small");
		infoScroll = new HoverFocusScrollPane(infoLabel);

		contentContainer = new Container<>();
		contentContainer.fill();
		panel.add(contentContainer).grow().pad(3).row();
		showTab(introScroll);

		// --- 底部拖拽条 ---
		Image resizeHandle = new Image(dragDrawable);
		resizeHandle.setColor(Color.DARK_GRAY);
		resizeHandle.addListener(new InputListener() {
				@Override
				public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
					return true;
				}
				@Override
				public void touchDragged(InputEvent event, float x, float y, int pointer) {
					// 拖拽逻辑：计算新高度
					// 触摸点是相对于 Handle 的，我们需要 Stage 坐标来计算绝对高度
					float stageH = getStage().getHeight();
					float touchStageY = event.getStageY();

					// 新高度 = 屏幕顶部 - 触摸点Y
					float newH = stageH - touchStageY;

					// 限制范围
					if (newH < MIN_HEIGHT) newH = MIN_HEIGHT;
					if (newH > stageH - 50) newH = stageH - 50;

					panelHeight = newH;

					// 拖拽时强制更新目标位置和当前位置，保证跟手
					// 展开态的目标位置是：StageH - panelH
					float topY = stageH; // 面板顶部贴着屏幕顶
					targetPanelY = topY - panelHeight;
					currentPanelY = targetPanelY; // 直接设置，取消插值延迟
				}
			});
		panel.add(resizeHandle).growX().height(resizeHandleHeight);

		addActor(panel);
	}

	private VisTextButton createTabBtn(String text, Runnable action) {
		VisTextButton btn = new VisTextButton(text);
		btn.addListener(new ClickListener() {
				public void clicked(InputEvent event, float x, float y) { action.run(); }
			});
		return btn;
	}

	private void showTab(Actor target) {
		contentContainer.setActor(target);
		// LogPanel 自行管理滚动，无需手动设置
	}

	public static void autoSwitchState() {
		if(inst == null) return;
		inst.switchState();
	}
	private void switchState() {
		switchState(currentState == State.COLLAPSED ? State.EXPANDED : State.COLLAPSED);
	}
	private void switchState(State state) {
		this.currentState = state;
		if (state == State.COLLAPSED) {
			// 收起态：只响应 FPS 按钮点击
			setTouchable(Touchable.childrenOnly);
			fpsBtn.setVisible(true);
			fpsBtn.setTouchable(Touchable.enabled);
			panel.setTouchable(Touchable.disabled); // 面板虽在动，但收起时不交互
		} else {
			// 展开态：面板响应
			setTouchable(Touchable.childrenOnly);
			fpsBtn.setVisible(false);
			fpsBtn.setTouchable(Touchable.enabled); // 按钮还在那
			panel.setTouchable(Touchable.enabled);
		}
	}

	@Override
	public void act(float delta) {
		super.act(delta);

		// --- 核心：布局与动画逻辑 ---
		if (getStage() == null) return;

		float W = getStage().getWidth();
		float H = getStage().getHeight();

		// 1. 定位 FPS 按钮 (左上角 + Padding)
		fpsBtn.setPosition(W - SAFE_PAD - fpsBtn.getWidth(), H - SAFE_PAD - fpsBtn.getHeight());

		// 2. 计算 Panel 目标位置
		// 坐标原点在左下角。
		// 屏幕顶部 Y = H。
		// 抽屉从上往下滑入。
		// 收起(隐藏)：面板底边在屏幕上方 -> Y = H
		// 展开(显示)：面板顶边在屏幕顶 -> Y = H - panelHeight

		if (currentState == State.COLLAPSED) {
			targetPanelY = H;
		} else {
			targetPanelY = H - panelHeight;
		}

		// 3. 插值运动 (Lerp)
		if (Math.abs(currentPanelY - targetPanelY) > 0.5f) {
			currentPanelY = MathUtils.lerp(currentPanelY, targetPanelY, LERP_SPEED * delta);
		} else {
			currentPanelY = targetPanelY;
		}

		// 4. 应用 Panel 布局
		// X 轴缩进 SAFE_PAD，宽度减去双倍 PAD
		panel.setPosition(SAFE_PAD, currentPanelY);
		panel.setSize(W - SAFE_PAD * 2, panelHeight);

		// -------------------------

		// 数据刷新
		updateTimer += delta;
		if (updateTimer > REFRESH_RATE) {
			updateTimer = 0;
			refreshData();
		}
		DLog.clearInfo();
	}

	private void refreshData() {
		fpsBtn.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

		// 只有面板在屏幕内时才更新文本 (优化)
		if (currentPanelY < getStage().getHeight()) {
			// [Phase3] LogPanel 由 act() 自行刷新, 无需手动更新
			if (contentContainer.getActor() == infoScroll) {
				infoLabel.setText(DLog.getInfoString());
			}
		}
	}

	public void setIntros(String text) {
		if (introLabel != null) introLabel.setText(text);
	}

	public void dispose() {
		if (backDrawable.getRegion().getTexture() != null) {
			backDrawable.getRegion().getTexture().dispose();
		}
		if (back2Drawable.getRegion().getTexture() != null) {
			back2Drawable.getRegion().getTexture().dispose();
		}
		if (dragDrawable.getRegion().getTexture() != null) {
			dragDrawable.getRegion().getTexture().dispose();
		}
	}
}
