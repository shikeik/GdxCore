package com.goldsprite.gdengine.ui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * 通用控制台输入栏 — 封装 VisTextField + 历史翻阅 + Enter 提交 + 长按上下文菜单。
 * <p>
 * 内置功能（继承自 VisTextField）:
 * <ul>
 *   <li>光标左右移动 (← →)</li>
 *   <li>Home / End 跳到行首/行尾</li>
 *   <li>Ctrl+A 全选</li>
 *   <li>Ctrl+C 复制 / Ctrl+X 剪切 / Ctrl+V 粘贴</li>
 *   <li>文本选中（Shift+方向键 / 鼠标拖选）</li>
 * </ul>
 * <p>
 * 额外封装功能:
 * <ul>
 *   <li>↑↓ 历史命令翻阅</li>
 *   <li>Enter 触发提交回调</li>
 *   <li>自动附加 TextFieldPasteMenu（Android 长按上下文菜单）</li>
 * </ul>
 * <p>
 * 用法:
 * <pre>
 * ConsoleTextField cmd = new ConsoleTextField();
 * cmd.setMessageText("输入命令 ...");
 * cmd.setOnSubmit(text -> executeCommand(text));
 * table.add(cmd).growX().height(28);
 * </pre>
 */
public class ConsoleTextField extends VisTable {

    /** 内部输入框 */
    private final VisTextField textField;

    /** 输入历史 */
    private final List<String> history = new ArrayList<>();

    /** 当前历史索引（history.size() 表示当前输入，非历史） */
    private int historyIndex = 0;

    /** 提交回调 */
    private Consumer<String> onSubmit;

    /** 历史上限 */
    private int maxHistory = 50;

    /** 暂存当前正在编辑的文本（翻阅历史时保存） */
    private String pendingInput = "";

    // ══════════════ 构造 ══════════════

    public ConsoleTextField() {
        this("");
    }

    public ConsoleTextField(String initialText) {
        textField = new VisTextField(initialText);

        // 自动附加长按上下文菜单（Android 友好）
        TextFieldPasteMenu.attach(textField);

        // 注册键盘事件: Enter 提交 + ↑↓ 历史翻阅
        textField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER) {
                    submitInput();
                    return true;
                }
                if (keycode == Input.Keys.UP) {
                    navigateHistory(-1);
                    return true;
                }
                if (keycode == Input.Keys.DOWN) {
                    navigateHistory(1);
                    return true;
                }
                return false;
            }
        });

        // 布局: 输入框填满整个 Table
        add(textField).growX();

        // 初始化历史索引
        historyIndex = 0;
    }

    // ══════════════ 公开 API ══════════════

    /** 设置提交回调（Enter 键触发） */
    public void setOnSubmit(Consumer<String> callback) {
        this.onSubmit = callback;
    }

    /** 设置占位符提示文字 */
    public void setMessageText(String text) {
        textField.setMessageText(text);
    }

    /** 获取当前文本 */
    public String getText() {
        return textField.getText();
    }

    /** 设置文本 */
    public void setText(String text) {
        textField.setText(text);
        textField.setCursorPosition(text != null ? text.length() : 0);
    }

    /** 清空输入 */
    public void clear() {
        textField.setText("");
    }

    /** 获取内部 VisTextField（用于自定义配置） */
    public VisTextField getTextField() {
        return textField;
    }

    /** 设置历史上限 */
    public void setMaxHistory(int max) {
        this.maxHistory = Math.max(1, max);
    }

    /** 获取输入历史列表（只读） */
    public List<String> getHistory() {
        return java.util.Collections.unmodifiableList(history);
    }

    /** 请求焦点 */
    public void focus() {
        if (getStage() != null) {
            getStage().setKeyboardFocus(textField);
        }
    }

    // ══════════════ 内部逻辑 ══════════════

    /** 提交当前输入 */
    private void submitInput() {
        String text = textField.getText().trim();
        textField.setText("");
        pendingInput = "";

        if (text.isEmpty()) return;

        // 记入历史
        history.add(text);
        if (history.size() > maxHistory) {
            history.remove(0);
        }
        historyIndex = history.size(); // 指向末尾之后

        // 触发回调
        if (onSubmit != null) {
            onSubmit.accept(text);
        }
    }

    /** 浏览历史命令 (direction: -1 上翻, +1 下翻) */
    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;

        // 首次上翻时保存当前编辑内容
        if (historyIndex == history.size() && direction == -1) {
            pendingInput = textField.getText();
        }

        historyIndex += direction;

        if (historyIndex < 0) {
            historyIndex = 0;
        }

        if (historyIndex >= history.size()) {
            // 回到当前编辑
            historyIndex = history.size();
            textField.setText(pendingInput);
            textField.setCursorPosition(pendingInput.length());
            return;
        }

        String historyText = history.get(historyIndex);
        textField.setText(historyText);
        textField.setCursorPosition(historyText.length());
    }
}
