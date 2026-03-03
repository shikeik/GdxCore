package com.goldsprite.gdengine.screens.basics;

import com.goldsprite.gdengine.screens.GScreen;
import com.goldsprite.gdengine.log.DLog;

public abstract class ExampleGScreen extends GScreen {

	public String getIntroduction() { return ""; }

	// 4. 自动处理转屏逻辑
	@Override
	public void show() {
		super.show();

		// [核心改动] 将介绍文本注入到 DebugUI，而不是自己画
		DLog.setIntros(getIntroduction());
	}
}
