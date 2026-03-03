package com.goldsprite.gdengine.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;

/**
 * 简易相机控制器 (通用版).
 * <p>
 * 提供对 {@link OrthographicCamera} 的基础控制功能，包括：
 * <ul>
 *     <li><b>鼠标拖拽平移</b> (右键或中键)</li>
 *     <li><b>滚轮缩放</b> (以鼠标为锚点)</li>
 *     <li><b>触摸手势</b> (双指缩放、单指平移)</li>
 * </ul>
 * <p>
 * 本控制器设计为通用的 {@link InputProcessor}，可直接添加到 {@link InputMultiplexer} 中使用。
 * <p>
 * <b>高级特性：</b>
 * <ul>
 *     <li>支持自定义坐标映射策略 ({@link CoordinateMapper})，适配 Viewport 或自定义坐标系。</li>
 *     <li>支持激活条件检查 ({@link #setActivationCondition(BooleanSupplier)})，灵活控制是否响应输入。</li>
 *     <li>解决了滚轮缩放时的"漂移"问题，保证以鼠标指针指向的世界点为中心进行缩放。</li>
 * </ul>
 *
 * @author GoldSprite
 */
public class SimpleCameraController implements InputProcessor {

	private final OrthographicCamera camera;
	private final GestureDetector gestureDetector;
	private final InputAdapter mouseInputAdapter;
	private boolean inputEnabled = true;

	/** 最小缩放比例 (Zoom 值越小，视野越小/物体越大) */
	public static float minZoom = 0.001f;
	/** 最大缩放比例 (Zoom 值越大，视野越大/物体越小) */
	public static float maxZoom = 100.0f;

	// [新增] UI Stage 列表: touchDown 时对这些 Stage 做 hit 检测，命中则跳过相机操作
	private final List<Stage> uiStages = new ArrayList<>();
	// 记录每个 pointer 是否被 UI 拦截（touchDown 时标记，touchUp 时重置）
	private final boolean[] touchBlockedByUI = new boolean[20];

	/**
	 * 坐标映射策略接口.
	 * <p>
	 * 用于将屏幕坐标 (Screen Coordinates) 转换为世界坐标 (World Coordinates)。
	 * 默认实现使用 {@link OrthographicCamera#unproject(Vector3)}。
	 * <p>
	 * 如果使用了 Viewport，建议传入 Viewport 的 unproject 逻辑。
	 */
	public interface CoordinateMapper {
		/**
		 * 将屏幕坐标转换为世界坐标.
		 * @param screenX 屏幕 X 坐标
		 * @param screenY 屏幕 Y 坐标
		 * @return 对应的世界坐标 (Vector2)
		 */
		Vector2 map(float screenX, float screenY);
	}

	// 默认策略: 标准全屏 Unproject
	private CoordinateMapper mapper;

	// [新增] 激活条件
	private BooleanSupplier activationCondition;

	/**
	 * 构造一个新的相机控制器.
	 * @param camera 需要控制的正交相机
	 */
	public SimpleCameraController(OrthographicCamera camera) {
		this.camera = camera;

		// 默认实现
		this.mapper = (x, y) -> {
			Vector3 v = camera.unproject(new Vector3(x, y, 0));
			return new Vector2(v.x, v.y);
		};

		GestureDetector.GestureListener listener = new CameraGestureListener();
		this.gestureDetector = new GestureDetector(20, 0.4f, 1.1f, 0.15f, listener);
		this.mouseInputAdapter = new CameraMouseListener();
	}

	/**
	 * 设置自定义的坐标映射策略.
	 * <p>
	 * 当使用了非全屏 Viewport 或特殊坐标系转换时非常有用。
	 * @param mapper 映射策略实现
	 */
	public void setCoordinateMapper(CoordinateMapper mapper) {
		this.mapper = mapper;
	}

	/**
	 * 设置激活条件.
	 * <p>
	 * 当条件返回 false 时，控制器将忽略所有输入事件。
	 * @param condition 返回 boolean 的 Supplier
	 */
	public void setActivationCondition(BooleanSupplier condition) {
		this.activationCondition = condition;
	}

	/**
	 * 添加需要做 UI 遮挡检测的 Stage.
	 * <p>
	 * touchDown 时会对所有已添加的 Stage 做 hit 检测，
	 * 只要任意一个 Stage 命中了控件，整个触摸序列（拖拽、抬起）都跳过相机操作。
	 * <p>
	 * 典型用法:
	 * <pre>
	 * controller.addUIStages(DLog.getInstance().getStage(), myScreenStage);
	 * </pre>
	 * @param stages 需要参与遮挡检测的 Stage（可变参数）
	 */
	public void addUIStages(Stage... stages) {
		uiStages.addAll(Arrays.asList(stages));
	}

	/**
	 * 启用或禁用输入处理.
	 * @param enabled true 为启用，false 为禁用
	 */
	public void setInputEnabled(boolean enabled) {
		this.inputEnabled = enabled;
	}

	// [新增] 内部检查
	private boolean shouldIgnore() {
		// 如果 inputEnabled 为 false，或者设置了条件但条件不满足，则忽略
		return !inputEnabled || (activationCondition != null && !activationCondition.getAsBoolean());
	}

	// --- 代理 InputProcessor ---
	@Override public boolean touchDown(int x, int y, int pointer, int button) {
		// [修改] 增加检查
		if (shouldIgnore()) return false;

		// [新增] UI 遮挡检测：按下时对所有注册的 Stage 做 hit 检测
		if (isUIHit(x, y)) {
			if (pointer >= 0 && pointer < touchBlockedByUI.length) {
				touchBlockedByUI[pointer] = true;
			}
			return false;
		}
		if (pointer >= 0 && pointer < touchBlockedByUI.length) {
			touchBlockedByUI[pointer] = false;
		}

		// 优先处理鼠标逻辑 (PC右键)
		if (mouseInputAdapter.touchDown(x, y, pointer, button)) return true;
		// 其次处理手势 (Android双指/单指拖拽)
		return gestureDetector.touchDown(x, y, pointer, button);
	}
	@Override public boolean touchUp(int x, int y, int pointer, int button) {
		// [新增] 重置 UI 拦截标记
		if (pointer >= 0 && pointer < touchBlockedByUI.length) {
			touchBlockedByUI[pointer] = false;
		}
		mouseInputAdapter.touchUp(x, y, pointer, button);
		return gestureDetector.touchUp(x, y, pointer, button);
	}
	@Override public boolean touchDragged(int x, int y, int pointer) {
		// 拖拽过程中通常不需要检查 condition (因为 touchDown 已经检查过了)
		// 但为了安全，如果 inputEnabled 被强关，还是断开比较好
		if (shouldIgnore()) return false;
		// [新增] 如果此 pointer 在 touchDown 时被 UI 拦截，跳过相机拖拽
		if (pointer >= 0 && pointer < touchBlockedByUI.length && touchBlockedByUI[pointer]) return false;
		if (mouseInputAdapter.touchDragged(x, y, pointer)) return true;
		return gestureDetector.touchDragged(x, y, pointer);
	}
	@Override public boolean scrolled(float amountX, float amountY) {
		// [修改] 增加检查 (解决滚轮串味的核心)
		if (shouldIgnore()) return false;
		return mouseInputAdapter.scrolled(amountX, amountY);
	}
	// ... 其他方法直接返回 false 或调用 super ...
	@Override public boolean keyDown(int keycode) { return false; }
	@Override public boolean keyUp(int keycode) { return false; }
	@Override public boolean keyTyped(char character) { return false; }
	@Override public boolean mouseMoved(int screenX, int screenY) { return false; }
	@Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }

	/**
	 * 对所有已注册的 UI Stage 做命中检测.
	 * 任意一个 Stage 的 hit 返回非 null（即触摸点命中了某个 touchable 的 Actor），则返回 true。
	 */
	private boolean isUIHit(int screenX, int screenY) {
		for (Stage stage : uiStages) {
			if (stage == null) continue;
			Vector2 coords = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
			if (stage.hit(coords.x, coords.y, true) != null) return true;
		}
		return false;
	}

	// --- 手势逻辑 (Mobile / Touch) ---
	private class CameraGestureListener extends GestureDetector.GestureAdapter {
		private float initialScale = 1f;

		@Override
		public boolean pan(float x, float y, float deltaX, float deltaY) {
			// Android 单指平移 (如果需要的话，或者交给 MouseListener 的 touchDragged 处理)
			// 这里为了跟手，我们也应该使用 mapper。
			// 但 GestureDetector 的 pan delta 是屏幕像素。
			// 简单实现：将屏幕 delta 转为世界 delta
			// 世界增量 = 屏幕增量 * (世界宽 / 屏幕宽)
			// 或者利用两个点：
			Vector2 p1 = mapper.map(x, y);
			Vector2 p2 = mapper.map(x - deltaX, y - deltaY);
			camera.translate(p2.x - p1.x, p2.y - p1.y);
			return true;
		}

		@Override
		public boolean zoom(float initialDistance, float distance) {
			// 双指缩放
			float ratio = initialDistance / distance;
			float newZoom = MathUtils.clamp(initialScale * ratio, minZoom, maxZoom);
			camera.zoom = newZoom;
			return true;
		}

		@Override
		public boolean touchDown(float x, float y, int pointer, int button) {
			initialScale = camera.zoom;
			return false;
		}
	}

	// --- 鼠标逻辑 (PC / Mouse) ---
	private class CameraMouseListener extends InputAdapter {
		private Vector2 lastWorldPos = new Vector2();
		private boolean isPanning = false;

		@Override
		public boolean touchDown(int x, int y, int pointer, int button) {
			// 允许 右键 或 中键 拖拽
			if (button == Input.Buttons.RIGHT || button == Input.Buttons.MIDDLE) {
				isPanning = true;
				// 记录按下时的世界坐标锚点
				lastWorldPos.set(mapper.map(x, y));
				return true;
			}
			return false;
		}

		@Override
		public boolean touchUp(int x, int y, int pointer, int button) {
			if (button == Input.Buttons.RIGHT || button == Input.Buttons.MIDDLE) {
				isPanning = false;
				return true;
			}
			return false;
		}

		@Override
		public boolean touchDragged(int x, int y, int pointer) {
			if (isPanning) {
				// [核心跟手逻辑]
				// 1. 获取当前鼠标位置对应的"新"世界坐标 (假设相机没动)
				Vector2 currWorldPos = mapper.map(x, y);

				// 2. 计算差异：鼠标当前指着的点，和上一帧指着的点，在世界空间差了多少
				// 实际上我们希望鼠标底下的世界点不动。
				// 所以相机需要移动，抵消这个差值。
				float dx = lastWorldPos.x - currWorldPos.x;
				float dy = lastWorldPos.y - currWorldPos.y;

				camera.translate(dx, dy);
				camera.update();

				// 注意：相机移动后，mapper.map(x,y) 的结果会变，
				// 但因为我们是把"上一帧的世界点"对齐到"当前鼠标位置"，
				// 所以下一帧比较的基准(lastWorldPos)依然应该是那个固定的世界点吗？
				// 不，最简单的做法是每帧重置锚点：
				// 移动完相机后，重新采样当前的鼠标世界位置作为下一帧的基准。
				lastWorldPos.set(mapper.map(x, y));
				return true;
			}
			return false;
		}

		@Override
		public boolean scrolled(float amountX, float amountY) {
			// [锚点缩放]
			// 1. 获取缩放中心 (鼠标位置)
			float mouseX = Gdx.input.getX();
			float mouseY = Gdx.input.getY();
			Vector2 anchor = mapper.map(mouseX, mouseY);

			// 2. 执行缩放
			float zoomFactor = 0.1f;
			float targetZoom = camera.zoom + amountY * zoomFactor * camera.zoom;
			camera.zoom = MathUtils.clamp(targetZoom, minZoom, maxZoom);
			camera.update();

			// 3. 补偿位移：缩放后，鼠标指向的新世界坐标变了，要把相机移回去
			Vector2 newAnchor = mapper.map(mouseX, mouseY);
			camera.translate(anchor.x - newAnchor.x, anchor.y - newAnchor.y);
			camera.update();

			return true;
		}
	}
}
