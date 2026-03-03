package com.goldsprite.gdengine.log;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.goldsprite.gdengine.PlatformProfile;
import com.goldsprite.gdengine.chat.ChatChannel;
import com.goldsprite.gdengine.chat.ChatMessage;
import com.goldsprite.gdengine.ui.widget.HoverFocusScrollPane;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;

/**
 * 游戏内聊天面板（MC 风格）。
 * <p>
 * 功能:
 * <ul>
 *   <li>显示 {@link ChatChannel} 中的聊天、系统通知、命令反馈消息</li>
 *   <li>输入框: 普通文本 → 聊天，{@code /} 前缀 → 命令</li>
 *   <li>命令历史（上/下箭头翻阅）</li>
 *   <li>自动滚动到最新消息</li>
 *   <li>可嵌入 {@link DebugConsole} 的标签页或独立使用</li>
 * </ul>
 *
 * <b>设计参考</b>: 复用 {@link LogPanel} 的滚动/命令历史/刷新控制模式。
 *
 * @see ChatChannel
 * @see ChatMessage
 * @see CommandRegistry
 */
public class GameChatPanel extends VisTable {

    // ── 发送者名称（客户端为玩家名，服务器为 "Server"） ──
    private String senderName = "Player";

    // ── UI 组件 ──
    private VisLabel chatLabel;
    private HoverFocusScrollPane chatScroll;
    private VisTextField inputField;
    private VisTextButton autoScrollBtn;
    private boolean autoScroll = true;

    // ── 刷新控制 ──
    private float refreshTimer = 0;
    private static final float REFRESH_INTERVAL = 1 / 30f; // 30 FPS 刷新
    private int lastMessageCount = -1;

    // ── 命令历史 ──
    private final List<String> inputHistory = new ArrayList<>();
    private int historyIndex = -1;

    public GameChatPanel() {
        this("Player");
    }

    public GameChatPanel(String senderName) {
        this.senderName = senderName;

        // 将 CommandRegistry 接入 ChatChannel（GUI 模式）
        ChatChannel.get().setCommandExecutor(commandLine -> CommandRegistry.execute(commandLine));

        buildUI();
    }

    // ============================================================
    //  UI 构建
    // ============================================================

    private void buildUI() {
        // 第1行: 工具栏 (Auto-scroll + Clear)
        buildToolbar();
        // 第2行: 消息显示区
        buildChatArea();
        // 第3行: 输入栏
        buildInputRow();
    }

    // ---------- 工具栏 ----------

    private void buildToolbar() {
        VisTable row = new VisTable();

        VisLabel title = new VisLabel("Chat", "small");
        title.setColor(Color.CYAN);
        row.add(title).padLeft(4).padRight(8);

        row.add().expandX(); // 弹性间距

        // AutoScroll
        autoScrollBtn = new VisTextButton("Auto: ON");
        autoScrollBtn.setColor(Color.GREEN);
        autoScrollBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                autoScroll = !autoScroll;
                autoScrollBtn.setText(autoScroll ? "Auto: ON" : "Auto: OFF");
                autoScrollBtn.setColor(autoScroll ? Color.GREEN : Color.GRAY);
            }
        });
        row.add(autoScrollBtn).padRight(6).minWidth(70);

        // Clear
        VisTextButton clearBtn = new VisTextButton("Clear");
        clearBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ChatChannel.get().clear();
                chatLabel.setText("");
                lastMessageCount = 0;
            }
        });
        row.add(clearBtn).padRight(2).minWidth(50);

        add(row).growX().pad(2).row();
    }

    // ---------- 消息显示区 ----------

    private void buildChatArea() {
        chatLabel = new VisLabel("", "small");
        chatLabel.setWrap(true);
        chatLabel.setFontScale(PlatformProfile.get().logPanelFontScale);
        chatScroll = new HoverFocusScrollPane(chatLabel);
        chatScroll.setFadeScrollBars(false);
        add(chatScroll).grow().pad(2).row();
    }

    // ---------- 输入栏 ----------

    private void buildInputRow() {
        VisTable row = new VisTable();

        VisLabel prompt = new VisLabel(">", "small");
        inputField = new VisTextField("");
        inputField.setMessageText("输入消息或 /命令 ...");

        // 键盘事件
        inputField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER) {
                    executeInput();
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

        VisTextButton sendBtn = new VisTextButton("Send");
        sendBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                executeInput();
            }
        });

        row.add(prompt).padLeft(4).padRight(4);
        row.add(inputField).growX().height(28);
        row.add(sendBtn).padLeft(4).padRight(4).minWidth(50);

        add(row).growX().pad(2).row();
    }

    // ============================================================
    //  输入处理
    // ============================================================

    /** 执行输入框中的文本 */
    private void executeInput() {
        String input = inputField.getText().trim();
        if (input.isEmpty()) return;

        // 记入历史
        inputHistory.add(input);
        historyIndex = inputHistory.size();

        // 交给 ChatChannel 统一路由（/ 命令 or 聊天）
        ChatChannel.get().processInput(input, senderName);

        inputField.setText("");
    }

    /** 浏览历史输入 (direction: -1上翻, +1下翻) */
    private void navigateHistory(int direction) {
        if (inputHistory.isEmpty()) return;
        historyIndex += direction;
        if (historyIndex < 0) historyIndex = 0;
        if (historyIndex >= inputHistory.size()) {
            historyIndex = inputHistory.size();
            inputField.setText("");
            return;
        }
        inputField.setText(inputHistory.get(historyIndex));
        inputField.setCursorPosition(inputField.getText().length());
    }

    // ============================================================
    //  消息渲染
    // ============================================================

    /** 刷新聊天显示区 */
    private void refreshChatDisplay() {
        List<ChatMessage> msgs = ChatChannel.get().getMessages();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < msgs.size(); i++) {
            ChatMessage msg = msgs.get(i);

            // 根据类型上色（使用 VisUI 的 Markup 颜色标记）
            switch (msg.type) {
                case CHAT:
                    sb.append("[WHITE]").append(msg.getFormatted());
                    break;
                case SYSTEM:
                    sb.append("[YELLOW]").append(msg.getFormatted());
                    break;
                case COMMAND_RESULT:
                    sb.append("[CYAN]").append(msg.getFormatted());
                    break;
                default:
                    sb.append(msg.getFormatted());
                    break;
            }
            if (i < msgs.size() - 1) sb.append("\n");
        }
        chatLabel.setText(sb.toString());

        if (autoScroll) {
            chatScroll.layout();
            chatScroll.setScrollY(chatScroll.getMaxY());
            chatScroll.setVelocityY(0);
        }
    }

    // ============================================================
    //  帧更新
    // ============================================================

    @Override
    public void act(float delta) {
        super.act(delta);

        refreshTimer += delta;
        if (refreshTimer < REFRESH_INTERVAL) return;
        refreshTimer = 0;

        // 检测新消息
        int currentCount = ChatChannel.get().getMessageCount();
        if (currentCount != lastMessageCount) {
            lastMessageCount = currentCount;
            refreshChatDisplay();
        }
    }

    // ============================================================
    //  公开 API
    // ============================================================

    /** 设置发送者名称 */
    public void setSenderName(String name) {
        this.senderName = name != null ? name : "Player";
    }

    /** 获取发送者名称 */
    public String getSenderName() {
        return senderName;
    }

    /** 获取/设置自动滚动 */
    public boolean isAutoScroll() {
        return autoScroll;
    }

    public void setAutoScroll(boolean val) {
        autoScroll = val;
        if (autoScrollBtn != null) {
            autoScrollBtn.setText(autoScroll ? "Auto: ON" : "Auto: OFF");
            autoScrollBtn.setColor(autoScroll ? Color.GREEN : Color.GRAY);
        }
    }

    /** 获取输入框引用（用于外部聚焦控制） */
    public VisTextField getInputField() {
        return inputField;
    }
}
