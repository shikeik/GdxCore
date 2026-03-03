package com.goldsprite.gdengine.testing;

import java.util.function.BooleanSupplier;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.Array;
import com.goldsprite.gdengine.log.DLog;

/**
 * 全局自动测试管理器
 * 模仿 CLogAssert 风格，支持时间线任务执行
 */
public class AutoTestManager {
	public static boolean ENABLED = false;
	
	private static final AutoTestManager INSTANCE = new AutoTestManager();
	private final Array<TestTask> taskQueue = new Array<>();
	private TestTask currentTask;
	private float totalTime = 0;

	private AutoTestManager() {}

	public static AutoTestManager getInstance() {
		return INSTANCE;
	}

	public void update(float delta) {
		if (!ENABLED) return;

		totalTime += delta;

		if (currentTask != null) {
			currentTask.update(delta);
			if (currentTask.isFinished()) {
				log("[PASS] 任务完成: " + currentTask.name);
				currentTask = null;
			}
		} else {
			if (taskQueue.size > 0) {
				currentTask = taskQueue.removeIndex(0);
				log("[START] 开始任务: " + currentTask.name);
				currentTask.start();
			} else {
				// No more tasks
			}
		}
	}

	public void add(TestTask task) {
		taskQueue.add(task);
	}
	
	public void addWait(float seconds) {
		add(new WaitTask(seconds));
	}
	
	public void addAction(String name, Runnable action) {
		add(new ActionTask(name, action));
	}

	/**
	 * 等待某个条件满足后再继续后续任务
	 * @param name 任务描述
	 * @param condition 条件判断，返回true表示满足
	 * @param timeout 超时时间（秒），超时后标记FAIL并继续
	 */
	public void addWaitCondition(String name, BooleanSupplier condition, float timeout) {
		add(new WaitConditionTask(name, condition, timeout));
	}

	public void log(String msg) {
		DLog.logT("AutoTest", msg);
	}

	public void logPass(String msg) {
		DLog.logT("AutoTest", "[PASS] " + msg);
	}

	public void logFail(String msg) {
		DLog.logErrT("AutoTest", "[FAIL] " + msg);
	}

	// --- Task Classes ---

	public static abstract class TestTask {
		protected String name;
		protected boolean finished = false;

		public TestTask(String name) {
			this.name = name;
		}

		public void start() {}
		public void update(float delta) {}
		public boolean isFinished() { return finished; }
	}

	public static class WaitTask extends TestTask {
		private float duration;
		private float timer;

		public WaitTask(float duration) {
			super("Wait " + duration + "s");
			this.duration = duration;
		}

		@Override
		public void update(float delta) {
			timer += delta;
			if (timer >= duration) finished = true;
		}
	}

	public static class ActionTask extends TestTask {
		private Runnable action;

		public ActionTask(String name, Runnable action) {
			super(name);
			this.action = action;
		}

		@Override
		public void start() {
			try {
				action.run();
				AutoTestManager.getInstance().logPass(name);
			} catch (Exception e) {
				AutoTestManager.getInstance().logFail(name + " - " + e.getMessage());
				e.printStackTrace();
			}
			finished = true;
		}
	}
	
	public static class AssertTask extends TestTask {
		private BooleanSupplier condition;
		
		public AssertTask(String name, BooleanSupplier condition) {
			super("Assert: " + name);
			this.condition = condition;
		}
		
		@Override
		public void start() {
			if (condition.getAsBoolean()) {
				AutoTestManager.getInstance().logPass(name);
			} else {
				AutoTestManager.getInstance().logFail(name);
			}
			finished = true;
		}
	}

	/**
	 * 等待条件达成任务
	 * 每帧检查条件，条件满足则完成；超时则标记失败并继续
	 */
	public static class WaitConditionTask extends TestTask {
		private BooleanSupplier condition;
		private float timeout;
		private float timer;

		public WaitConditionTask(String name, BooleanSupplier condition, float timeout) {
			super("WaitFor: " + name);
			this.condition = condition;
			this.timeout = timeout;
		}

		@Override
		public void update(float delta) {
			timer += delta;
			if (condition.getAsBoolean()) {
				AutoTestManager.getInstance().logPass(name);
				finished = true;
			} else if (timer >= timeout) {
				AutoTestManager.getInstance().logFail(name + " - 等待超时(" + timeout + "s)");
				finished = true;
			}
		}
	}

	public static class DragTask extends TestTask {
		private float startX, startY, endX, endY, duration;
		private float timer;

		public DragTask(float startX, float startY, float endX, float endY, float duration) {
			super("Drag " + startX + "," + startY + " -> " + endX + "," + endY);
			this.startX = startX;
			this.startY = startY;
			this.endX = endX;
			this.endY = endY;
			this.duration = duration;
		}

		@Override
		public void start() {
			InputProcessor ip = Gdx.input.getInputProcessor();
			if (ip != null) {
				ip.touchDown((int)startX, (int)startY, 0, Input.Buttons.LEFT);
			}
		}

		@Override
		public void update(float delta) {
			timer += delta;
			float alpha = Math.min(1, timer / duration);
			float currentX = startX + (endX - startX) * alpha;
			float currentY = startY + (endY - startY) * alpha;
			
			InputProcessor ip = Gdx.input.getInputProcessor();
			if (ip != null) {
				ip.touchDragged((int)currentX, (int)currentY, 0);
			}

			if (timer >= duration) {
				if (ip != null) {
					ip.touchUp((int)endX, (int)endY, 0, Input.Buttons.LEFT);
				}
				finished = true;
			}
		}
	}
}
