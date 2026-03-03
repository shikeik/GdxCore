package com.goldsprite.gdengine.ui.event;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;

/**
 * 统一上下文菜单监听器 (修复版)
 * <p>
 * 封装了 PC (右键单击) 和 Mobile (长按) 唤出菜单的统一逻辑。
 * 修复了长按后松手依然触发左键点击的 Bug。
 * </p>
 */
public abstract class ContextListener extends ActorGestureListener {

	// 标记当前点击是否有效（用于互斥长按和点击）
	private boolean validTap = true;

	// 参数: halfTapSquareSize, tapCountInterval, longPressDuration, maxFlingDelay
	public ContextListener() {
		// 长按时间设为 0.4s
		super(20, 0.4f, 0.4f, 0.15f);
	}

	@Override
	public void touchDown(InputEvent event, float x, float y, int pointer, int button) {
		super.touchDown(event, x, y, pointer, button);
		// 每次按下重置状态，假设这是一次有效的点击
		validTap = true;
	}

	/**
	 * 当需要显示菜单时触发 (右键 或 长按)
	 */
	public abstract void onShowMenu(float stageX, float stageY);

	/**
	 * 当左键点击时触发
	 */
	public void onLeftClick(InputEvent event, float x, float y, int count) {}

	@Override
	public void tap(InputEvent event, float x, float y, int count, int button) {
		// 如果已经被标记为无效（比如触发了长按），则忽略这次点击
		if (!validTap) return;

		if (button == Input.Buttons.RIGHT) {
			// PC 右键直接唤出菜单
			onShowMenu(event.getStageX(), event.getStageY());
		} else if (button == Input.Buttons.LEFT) {
			// 左键点击
			onLeftClick(event, x, y, count);
		}
	}

	@Override
	public boolean longPress(Actor actor, float x, float y) {
		// 触发长按：
		// 1. 标记本次点击无效，阻止后续的 tap 触发
		validTap = false;

		// 2. 转换坐标唤出菜单
		Vector2 stagePos = actor.localToStageCoordinates(new Vector2(x, y));
		onShowMenu(stagePos.x, stagePos.y);

		// 3. 震动反馈
		Gdx.input.vibrate(50);

		return true;
	}
}
