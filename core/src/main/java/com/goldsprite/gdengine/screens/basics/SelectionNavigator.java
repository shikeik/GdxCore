package com.goldsprite.gdengine.screens.basics;

/**
 * 选择屏导航输入接口。
 * 由项目层实现并注入到 BaseSelectionScreen，
 * 取代引擎对具体项目 InputManager/InputAction 的直接引用。
 */
public interface SelectionNavigator {
	/** 向上导航是否刚按下 */
	boolean isUpJustPressed();

	/** 向下导航是否刚按下 */
	boolean isDownJustPressed();

	/** 确认按钮是否刚按下 */
	boolean isConfirmJustPressed();

	/** 当前是否处于键盘/手柄输入模式（用于触发初始焦点） */
	boolean isKeyboardMode();
}
