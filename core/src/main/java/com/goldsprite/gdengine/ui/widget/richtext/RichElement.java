package com.goldsprite.gdengine.ui.widget.richtext;

public class RichElement {
	public enum Type { TEXT, IMAGE }

	public Type type;
	public String text;
	public String imagePath;
	// [New] Region Name (optional, from #suffix)
	public String regionName;
	public float imgWidth, imgHeight; // 0 means auto

	public RichStyle style;

	public RichElement(String text, RichStyle style) {
		this.type = Type.TEXT;
		this.text = text;
		this.style = style;
	}

	// for image
	public RichElement(String imagePath, String regionName, float w, float h, RichStyle style) {
		this.type = Type.IMAGE;
		this.imagePath = imagePath;
		this.regionName = regionName;
		this.imgWidth = w;
		this.imgHeight = h;
		this.style = style;
	}

	@Override
	public String toString() {
		if(type == Type.TEXT) return "Text(" + text + ")";
		return "Image(" + imagePath + ")";
	}
}
