package com.goldsprite.gdengine.ui.widget.richtext;

import com.badlogic.gdx.scenes.scene2d.Event;

public class RichTextEvent extends Event {
	public String eventId;

	public RichTextEvent(String id) {
		this.eventId = id;
	}
}
