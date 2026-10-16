package com.github.vlsi.mat.calcite.editor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Handles the SQL editor commands declared in plugin.xml. Their key bindings live in the
 * {@link CalcitePane#CONTEXT_ID} context, a child of MAT's editor context, so they take precedence over MAT's OQL
 * bindings for the same keys while the SQL editor has focus.
 */
public class SqlEditorCommandHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    CalcitePane pane = activeCalcitePane();
    if (pane != null) {
      pane.runCommand(event.getCommand().getId());
    }
    return null;
  }

  private static CalcitePane activeCalcitePane() {
    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    IWorkbenchPage page = window == null ? null : window.getActivePage();
    IEditorPart editor = page == null ? null : page.getActiveEditor();
    if (!(editor instanceof MultiPaneEditor)) {
      return null;
    }
    AbstractEditorPane pane = ((MultiPaneEditor) editor).getActiveEditor();
    return pane instanceof CalcitePane ? (CalcitePane) pane : null;
  }
}
