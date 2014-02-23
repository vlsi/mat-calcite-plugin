package com.github.vlsi.mat.optiq.editor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

public class OpenOptiqAction extends Action {

	public OpenOptiqAction() {
		super("SQL");
	}

	@Override
	public void run() {
		IWorkbenchPage page = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getActivePage();
		IEditorPart part = page == null ? null : page.getActiveEditor();

		if (part instanceof MultiPaneEditor) {
			((MultiPaneEditor) part).addNewPage("OPTIQ", null);//$NON-NLS-1$
		}
	}

}
