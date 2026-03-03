package com.goldsprite.gdengine.neonbatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.graphics.g2d.Batch;

public class NeonActor extends Actor{

	public void draw(NeonBatch batch, float parentAlpha) {
		super.draw(batch, parentAlpha);
	}

	@Override
	public void draw(Batch batch, float parentAlpha) {
		draw((NeonBatch)batch, parentAlpha);
	}
}
