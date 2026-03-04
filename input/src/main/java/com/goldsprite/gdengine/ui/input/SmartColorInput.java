package com.goldsprite.gdengine.ui.input;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.goldsprite.gdengine.assets.ColorTextureUtils;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter;
import java.util.function.Consumer;

public class SmartColorInput extends SmartInput<Color> {

	private final VisTextButton previewBtn;
	private final TextureRegionDrawable drawable;
	private static ColorPicker sharedPicker;

	public SmartColorInput(String label, Color initValue, Consumer<Color> onChange) {
		super(label, new Color(initValue), onChange);

		drawable = ColorTextureUtils.createColorDrawable(Color.WHITE);

		previewBtn = new VisTextButton(value.toString());
		TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(previewBtn.getStyle());
		style.up = drawable;
		style.down = drawable;
		previewBtn.setStyle(style);

		previewBtn.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					if (!previewBtn.isDisabled()) openPicker();
				}
			});

		updateUI();

		VisTable controls = new VisTable();
		controls.add().growX();
		controls.add(previewBtn).width(180);
		addContent(controls);
	}

	@Override
	public void setReadOnly(boolean readOnly) {
		previewBtn.setDisabled(readOnly);
		previewBtn.setTouchable(readOnly ? Touchable.disabled : Touchable.enabled);
	}

	private void openPicker() {
		if (sharedPicker == null) sharedPicker = new ColorPicker();
		final Color restoreColor = new Color(value);

		sharedPicker.setListener(new ColorPickerAdapter() {
				@Override public void changed(Color newColor) { notifyValueChanged(new Color(newColor)); }
				@Override public void canceled(Color oldColor) { notifyValueChanged(restoreColor); }
				@Override public void finished(Color newColor) {
					Color finalColor = new Color(newColor);
					notifyValueChanged(finalColor);
					notifyCommand(restoreColor, finalColor);
				}
			});
		sharedPicker.setColor(value);
		if (getStage() != null) getStage().addActor(sharedPicker.fadeIn());
	}

	@Override public void updateUI() {
		previewBtn.setColor(value);
		previewBtn.setText(value.toString().toUpperCase());
	}
}
