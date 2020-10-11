package com.github.vlsi.mat.calcite.editor;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;


public class OpenCalciteAction extends Action {

  public OpenCalciteAction() {
    super("SQL", AbstractUIPlugin.imageDescriptorFromPlugin("MatCalcitePlugin", "resources/icons/plugin.png"));
  }

  @Override
  public void run() {
    IWorkbenchPage page = PlatformUI.getWorkbench()
        .getActiveWorkbenchWindow().getActivePage();
    IEditorPart part = page == null ? null : page.getActiveEditor();

    if (part instanceof MultiPaneEditor) {
      ((MultiPaneEditor) part).addNewPage("CALCITE", null);//$NON-NLS-1$
    }
  }

}
