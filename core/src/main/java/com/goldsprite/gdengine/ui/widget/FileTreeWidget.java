package com.goldsprite.gdengine.ui.widget;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.goldsprite.gdengine.ui.event.ContextListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTree;
import java.util.Arrays;
import java.util.function.Consumer;
import com.kotcrab.vis.ui.widget.VisTable;
import com.badlogic.gdx.scenes.scene2d.Touchable;

/**
 * 通用文件树控件
 */
public class FileTreeWidget extends VisTree<FileTreeWidget.FileNode, FileHandle> {

	private Consumer<FileHandle> onFileSelected;
	private ContextMenuProvider contextMenuProvider;

	private Consumer<FileHandle> onDoubleClicked; // [新增]

	public interface ContextMenuProvider {
		void showMenu(FileHandle file, float x, float y);
	}

	public FileTreeWidget() {
		super();
		//debugAll();
		getSelection().setProgrammaticChangeEvents(false);
		setIndentSpacing(20f);

		// 监听选中
		addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				FileNode selection = getSelection().first();
				if (selection != null && onFileSelected != null) {
					onFileSelected.accept(selection.getValue());
				}
			}
		});
	}

	// [修改] 扩展回调接口
	public void setCallbacks(Consumer<FileHandle> onSelect, Consumer<FileHandle> onDoubleClick, ContextMenuProvider menuProvider) {
		this.onFileSelected = onSelect;
		this.onDoubleClicked = onDoubleClick; // [新增]
		this.contextMenuProvider = menuProvider;
	}

	public void build(FileHandle rootDir) {
		clearChildren();
		if (rootDir == null || !rootDir.exists()) return;

		FileNode rootNode = new FileNode(rootDir);
		rootNode.setExpanded(true);
		add(rootNode);

		recursiveAddNodes(rootNode, rootDir);
	}

	private void recursiveAddNodes(FileNode parentNode, FileHandle dir) {
		FileHandle[] files = dir.list();
		// 排序: 文件夹在前
		Arrays.sort(files, (a, b) -> {
			if (a.isDirectory() && !b.isDirectory()) return -1;
			if (!a.isDirectory() && b.isDirectory()) return 1;
			return a.name().compareTo(b.name());
		});

		for (FileHandle file : files) {
			// 只显示文件夹 (Unity风格: 左侧只看树状结构，右侧看内容)
			// 但为了方便，我们也显示文件，您可以根据喜好注释掉下面这行
			// if (!file.isDirectory()) continue;

			// 过滤隐藏文件
			if (file.name().startsWith(".")) continue;

			FileNode node = new FileNode(file);
			parentNode.add(node);

			//这个办法无法整行触发
			// 菜单与打开
			node.getActor().addListener(new ContextListener() {
					@Override
					public void onShowMenu(float stageX, float stageY) {
						getSelection().choose(node);
						if (contextMenuProvider != null) {
							contextMenuProvider.showMenu(file, stageX, stageY);
						}
					}
					@Override public void onLeftClick(InputEvent event, float x, float y, int count) {
						if (count == 2) {
							if (file.isDirectory()) {
								//如果是文件夹：切换展开/折叠
								node.setExpanded(!node.isExpanded());
							} else {
								// 如果是文件：触发回调 (ProjectPresenter 会处理打开逻辑)
								if (onDoubleClicked != null) {
									onDoubleClicked.accept(file);
								}
							}
						}

					}
				});

			if (file.isDirectory()) {
				recursiveAddNodes(node, file);
			}
		}

		// [新增] 智能展开逻辑
		// 条件：
		// 1. 当前目录不为空
		// 2. 当前目录只有一个子元素 (files.length == 1)
		// 3. 这个子元素是目录
		if (files.length == 1 && files[0].isDirectory()) {
			// 获取刚刚添加进去的那个子节点
			if (parentNode.getChildren().size > 0) {
				FileNode childNode = parentNode.getChildren().get(0);
				childNode.setExpanded(true); // 自动展开
			}
		}
	}

	// --- Node ---
	public static class FileNode extends VisTree.Node<FileNode, FileHandle, VisTable> {
		public FileNode(FileHandle file) {
			super(new VisTable());
			setValue(file);

			VisTable table = getActor();
			//table.debugAll();
			table.setBackground("button");

			// 名字
			VisLabel lbl = new VisLabel(file.name());
			lbl.setTouchable(Touchable.enabled);
			table.add(lbl).growX().left().padLeft(5);

			//节点类型染色
			if (file.isDirectory()) {
				lbl.setColor(Color.GOLD);
			} else {
				String ext = file.extension().toLowerCase();
				if (ext.equals("java")) lbl.setColor(Color.CYAN);
				else if (ext.equals("scene")) lbl.setColor(Color.ORANGE);
				else lbl.setColor(Color.WHITE);
			}
		}
	}
}
