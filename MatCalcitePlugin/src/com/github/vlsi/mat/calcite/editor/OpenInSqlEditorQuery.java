package com.github.vlsi.mat.calcite.editor;

import com.github.vlsi.mat.calcite.SqlForObjects;

import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Help;
import org.eclipse.mat.query.annotations.Icon;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.util.IProgressListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import java.util.List;

/**
 * "SQL / Open in SQL Editor" on the context menu of objects and classes in MAT's views: adds a query for the selection
 * to the last used SQL tab (opening one if there is none) and selects it, without running it. Modelled on MAT's
 * "Copy / OQL Query".
 */
@Name("Open in SQL Editor")
@Category("SQL")
@Icon("/resources/icons/plugin.png")
@Help("Adds a query that selects the chosen objects, or all instances of the chosen classes, to the SQL editor. "
    + "Run it with F5.")
public class OpenInSqlEditorQuery implements IQuery {
  @Argument
  public ISnapshot snapshot;

  @Argument
  public List<IContextObject> elements;

  @Argument
  public Display display;

  @Override
  public IResult execute(IProgressListener listener) throws Exception {
    String sql = SqlForObjects.build(snapshot, elements);
    display.asyncExec(() -> {
      MultiPaneEditor multiPaneEditor = editorOf(snapshot);
      if (multiPaneEditor == null) {
        return;
      }
      // Reuse the last SQL tab rather than opening one per query
      CalcitePane pane = CalcitePane.lastUsed(multiPaneEditor);
      if (pane == null) {
        multiPaneEditor.addNewPage("CALCITE", null);
        pane = CalcitePane.lastUsed(multiPaneEditor);
        if (pane == null) {
          return;
        }
      } else {
        multiPaneEditor.bringPageToTop(pane.getPaneState());
      }
      pane.appendQuery(sql);
    });
    // The query is only added to the SQL editor, so there is no result to show here
    throw new IProgressListener.OperationCanceledException();
  }

  /**
   * The editor of the dump the objects belong to: addresses mean nothing in another dump. The active editor is
   * usually that one, but not when the query was started from a view, or the user switched tabs meanwhile.
   */
  private static MultiPaneEditor editorOf(ISnapshot snapshot) {
    IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    IWorkbenchPage page = window == null ? null : window.getActivePage();
    if (page == null) {
      return null;
    }
    IEditorPart active = page.getActiveEditor();
    if (active instanceof MultiPaneEditor && snapshotOf((MultiPaneEditor) active) == snapshot) {
      return (MultiPaneEditor) active;
    }
    for (IEditorReference reference : page.getEditorReferences()) {
      IEditorPart editor = reference.getEditor(false);
      if (editor instanceof MultiPaneEditor && snapshotOf((MultiPaneEditor) editor) == snapshot) {
        page.activate(editor);
        return (MultiPaneEditor) editor;
      }
    }
    return null;
  }

  private static ISnapshot snapshotOf(MultiPaneEditor editor) {
    return editor.getQueryContext() == null ? null
        : (ISnapshot) editor.getQueryContext().get(ISnapshot.class, null);
  }
}
