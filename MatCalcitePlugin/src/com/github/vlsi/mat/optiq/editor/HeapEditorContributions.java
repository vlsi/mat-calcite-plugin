package com.github.vlsi.mat.optiq.editor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.mat.ui.editor.IMultiPaneEditorContributor;
import org.eclipse.mat.ui.editor.MultiPaneEditor;

public class HeapEditorContributions implements IMultiPaneEditorContributor {
	MultiPaneEditor editor;
	Action openOptiqPane;

	@Override
	public void dispose() {
	}

	@Override
	public void init(MultiPaneEditor editor) {
		this.editor = editor;
		openOptiqPane = new OpenOptiqAction();
	}

	@Override
	public void contributeToToolbar(IToolBarManager manager) {
		manager.add(new Separator());
		manager.add(openOptiqPane);
	}

}
