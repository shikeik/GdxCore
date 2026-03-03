package com.goldsprite.gdengine.testing;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;

/**
 * 输入模拟器
 * 用于自动化测试中模拟用户输入
 */
public class InputSimulator {

	/**
	 * 模拟点击屏幕坐标 (Screen Coordinates, Y-down)
	 */
	public static void click(int screenX, int screenY) {
		InputProcessor ip = Gdx.input.getInputProcessor();
		if (ip != null) {
			ip.touchDown(screenX, screenY, 0, Input.Buttons.LEFT);
			ip.touchUp(screenX, screenY, 0, Input.Buttons.LEFT);
		}
	}

	/**
	 * 模拟点击 UI 元素 (自动转换坐标)
	 */
	public static void tapActor(Actor actor) {
		if (actor == null || actor.getStage() == null) return;
		
		Stage stage = actor.getStage();
		Vector2 center = new Vector2(actor.getWidth() / 2, actor.getHeight() / 2);
		Vector2 stageCoords = actor.localToStageCoordinates(center);
		Vector2 screenCoords = stage.stageToScreenCoordinates(stageCoords);
		
		click((int)screenCoords.x, (int)screenCoords.y);
	}

	/**
	 * 模拟按键一次 (按下并抬起)
	 */
	public static void pressKey(int keycode) {
		InputProcessor ip = Gdx.input.getInputProcessor();
		if (ip != null) {
			ip.keyDown(keycode);
			ip.keyUp(keycode);
		}
	}
	
	/**
	 * 模拟按下按键 (不抬起)
	 */
	public static void keyDown(int keycode) {
		InputProcessor ip = Gdx.input.getInputProcessor();
		if (ip != null) {
			ip.keyDown(keycode);
		}
	}

	/**
	 * 模拟抬起按键
	 */
	public static void keyUp(int keycode) {
		InputProcessor ip = Gdx.input.getInputProcessor();
		if (ip != null) {
			ip.keyUp(keycode);
		}
	}

	/**
	 * 模拟输入文本 (通过 keyTyped)
	 */
	public static void typeText(String text) {
		InputProcessor ip = Gdx.input.getInputProcessor();
		if (ip != null) {
			for (char c : text.toCharArray()) {
				ip.keyTyped(c);
			}
		}
	}
}
