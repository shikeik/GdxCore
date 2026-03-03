package com.goldsprite.gdengine.log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
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
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.goldsprite.gdengine.assets.ColorTextureUtils;
import com.goldsprite.gdengine.assets.VisUIHelper;
import com.goldsprite.gdengine.ui.widget.HoverFocusScrollPane;

/**
 * 调试控制台 (抽屉动画版)
 */
public class DebugConsole extends Group {
	private static DebugConsole inst;
	public enum State { COLLAPSED, EXPANDED }
	public State currentState = State.COLLAPSED;

	// UI 组件
	private VisTextButton fpsBtn;
	private VisTable panel; // 滑动面板
	private VisLabel logLabel, infoLabel, introLabel;
	private ScrollPane logScroll, infoScroll, introScroll;
	private Container<ScrollPane> contentContainer;

	// 布局配置
	private final float SAFE_PAD = 30f; // 安全边距
	private final float MIN_HEIGHT = 100f;
	private float panelHeight = 400f; // 面板当前高度设定

	// 动画物理
	private float currentPanelY; // 面板当前的Y坐标
	private float targetPanelY;  // 目标Y坐标
	private final float LERP_SPEED = 15f; // 插值速度

	// 数据刷新
	private float updateTimer = 0;
	private final float REFRESH_RATE = 1/60f; // UI 刷新频率

	// 控制状态
	private boolean autoScroll = true; // 默认开启自动滚动

	float resizeHandleHeight = 14;

	private final TextureRegionDrawable backDrawable, back2Drawable, dragDrawable;

	public DebugConsole() {
		inst = this;
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
		VisTextButton btnLog = createTabBtn("LOG", () -> showTab(logScroll));
		VisTextButton btnInfo = createTabBtn("INFO", () -> showTab(infoScroll));

		// Auto Scroll Button
		VisTextButton btnAutoScroll = new VisTextButton("AutoScroll: ON");
		btnAutoScroll.setColor(Color.GREEN);
		btnAutoScroll.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				autoScroll = !autoScroll;
				if (autoScroll) {
					btnAutoScroll.setText("AutoScroll: ON");
					btnAutoScroll.setColor(Color.GREEN);
				} else {
					btnAutoScroll.setText("AutoScroll: OFF");
					btnAutoScroll.setColor(Color.GRAY);
				}
			}
		});

		// Clear Button
		VisTextButton btnClear = new VisTextButton("Clear");
		btnClear.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				DLog.logMessages.clear();
				logLabel.setText(""); // 立即清空文本
			}
		});

		VisTextButton btnClose = new VisTextButton(" X ");
		btnClose.setColor(Color.RED);
		btnClose.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					switchState(State.COLLAPSED);
				}
			});

		header.add(btnIntro).width(80).padRight(5);
		header.add(btnLog).width(80).padRight(5);
		header.add(btnInfo).width(80).padRight(5);
		header.add().expandX();
		header.add(btnAutoScroll).padRight(10);
		header.add(btnClear).padRight(10);
		header.add(btnClose).width(50);
		panel.add(header).growX().height(40).pad(5).row();

		// --- 内容区 ---
		introLabel = new VisLabel("", "small"); introLabel.setWrap(true);
		introScroll = new HoverFocusScrollPane(introLabel);
		logLabel = new VisLabel("", "small"); logLabel.setWrap(true);
		logLabel.setFontScale(0.75f); // 缩小一些以便信息量密集化
		logScroll = new HoverFocusScrollPane(logLabel);
		infoLabel = new VisLabel("", "small");
		infoScroll = new HoverFocusScrollPane(infoLabel);

		contentContainer = new Container<>();
		contentContainer.fill();
		panel.add(contentContainer).grow().pad(10).row();
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

	private void showTab(ScrollPane target) {
		contentContainer.setActor(target);
		if (target == logScroll) {
			target.layout(); target.setScrollY(target.getMaxY());
		}
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
			if (contentContainer.getActor() == logScroll) {
				logLabel.setText(String.join("\n", DLog.getLogs()));

				if (autoScroll) {
					// 只有在自动滚动开启时才执行
					// 增加一个小的容差或强制到底
					logScroll.layout();
					logScroll.setScrollY(logScroll.getMaxY());
					logScroll.setVelocityY(0);
				}
			} else if (contentContainer.getActor() == infoScroll) {
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
