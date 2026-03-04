package com.goldsprite.gdengine.ui.input;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;
import java.util.function.Consumer;

public class SmartTextInput extends SmartInput<String> {

	private final VisTextField textField;

	public SmartTextInput(String label, String initValue, Consumer<String> onChange) {
		this(label, initValue, onChange, null);
	}

	public SmartTextInput(String label, String initValue, Consumer<String> onChange, Consumer<String> onConfirm) {
		super(label, initValue, onChange);

		textField = new VisTextField(initValue);
		textField.addListener(new ChangeListener() {
				@Override
				public void changed(ChangeEvent event, Actor actor) {
					value = textField.getText();
					if (onChange != null) onChange.accept(value);
				}
			});

		// 监听焦点丢失触发 Command (可选)
		textField.addListener(new FocusListener() {
				String startValue = initValue;
				@Override
				public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
					if (focused) startValue = value;
					else {
						notifyCommand(startValue, value);
						if(onConfirm != null) onConfirm.accept(value);
					}
				}
			});

		// [修改] 布局优化：右对齐，固定宽度，类似 ColorInput
		VisTable controls = new VisTable();
		controls.add().growX(); // 弹簧占位符
		controls.add(textField).width(180); // 固定宽度
		addContent(controls);
	}

	@Override
	public void setReadOnly(boolean readOnly) {
		textField.setDisabled(readOnly);
		// 如果只读，透明度降低一点
		textField.setColor(1, 1, 1, readOnly ? 0.5f : 1f);
	}

	@Override public void updateUI() {
		if (!textField.getText().equals(value)) {
			int cursorPosition = textField.getCursorPosition();
			textField.setText(value);
			textField.setCursorPosition(Math.min(cursorPosition, value.length()));
		}
	}
}
