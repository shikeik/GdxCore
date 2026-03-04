package com.goldsprite.gdengine.ui.input;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import java.util.function.Consumer;

public class SmartSelectInput<T> extends SmartInput<T> {

	private final VisSelectBox<T> selectBox;

	public SmartSelectInput(String label, T initValue, Array<T> items, Consumer<T> onChange) {
		super(label, initValue, onChange);

		selectBox = new VisSelectBox<>();
		selectBox.setItems(items);
		selectBox.setSelected(initValue);

		selectBox.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				T newVal = selectBox.getSelected();
				T oldVal = value;
				value = newVal;
				if (onChange != null) onChange.accept(newVal);
				notifyCommand(oldVal, newVal);
			}
		});

		addContent(selectBox);
	}

	@Override
	public void setReadOnly(boolean readOnly) {
		selectBox.setDisabled(readOnly);
		selectBox.setTouchable(readOnly ? Touchable.disabled : Touchable.enabled);
	}

	@Override public void updateUI() {
		if (selectBox.getSelected() != value) {
			selectBox.setSelected(value);
		}
	}
}
