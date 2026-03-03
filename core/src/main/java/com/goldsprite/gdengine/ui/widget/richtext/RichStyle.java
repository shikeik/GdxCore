package com.goldsprite.gdengine.ui.widget.richtext;

import com.badlogic.gdx.graphics.Color;

public class RichStyle {
	public Color color = Color.WHITE;
	public float fontSize = 30;
	public String fontName = null;
	public String event = null;

	public RichStyle() {}

	public RichStyle copy() {
		RichStyle s = new RichStyle();
		s.color = new Color(this.color);
		s.fontSize = this.fontSize;
		s.fontName = this.fontName;
		s.event = this.event;
		return s;
	}
}
