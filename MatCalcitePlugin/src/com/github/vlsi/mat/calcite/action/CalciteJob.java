package com.github.vlsi.mat.calcite.action;

import com.github.vlsi.mat.calcite.editor.CalcitePane;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CalciteJob extends AbstractPaneJob {

  private final String sql;
  private final CalcitePane calcitePane;
  private final PaneState state;
  /** Where sql starts in the editor, after a prefix of this length that is not in the editor, e.g. explain plan for. */
  private final int editorOffset;
  private final int prefixLength;

  public CalciteJob(String sql, CalcitePane pane, PaneState state) {
    this(sql, pane, state, 0, 0);
  }

  public CalciteJob(String sql, CalcitePane pane, PaneState state, int editorOffset, int prefixLength) {
    super(sql, pane);
    this.sql = sql;
    this.state = state;
    this.editorOffset = editorOffset;
    this.prefixLength = prefixLength;
    calcitePane = pane;
    this.setUser(true);
  }

  @Override
  protected IStatus doRun(IProgressMonitor monitor) {
    final QueryDescriptor descriptor = QueryRegistry.instance().getQuery("calcite");//$NON-NLS-1$
    final ArgumentSet argumentSet;
    try {
      argumentSet = descriptor.createNewArgumentSet(getPane().getEditor()
          .getQueryContext());
      argumentSet.setArgumentValue("sql", sql);//$NON-NLS-1$
      final QueryResult result = argumentSet
          .execute(new ProgressMonitorWrapper(monitor));
      // The tab may have been closed while the query ran: then there is nothing to show the result in
      display().asyncExec(() -> {
        if (!calcitePane.getQueryString().isDisposed()) {
          calcitePane.initQueryResult(result, state, sql);
        }
      });
    } catch (final Throwable e) {
      if (monitor.isCanceled()) {
        return Status.CANCEL_STATUS; // cancelled from the progress view, or the tab was closed
      }
      display().asyncExec(() -> {
        if (calcitePane.getQueryString().isDisposed()) {
          return;
        }
        String keyError = calcitePane.highlightError(e, sql, editorOffset, prefixLength);

        StringWriter sw = new StringWriter();
        if (keyError != null) {
          sw.append(keyError).append("\n");
        }
        if (sql != null) {
          sw.append(sql).append('\n');
        }
        e.printStackTrace(new PrintWriter(sw));
        // Like the OQL pane, errors are shown without a navigation history entry
        calcitePane.showText(sw.toString());
      });
      e.printStackTrace();
    }

    return Status.OK_STATUS;
  }

  /** The jobs of a pane, which it cancels when it is closed. */
  @Override
  public boolean belongsTo(Object family) {
    return family == calcitePane || super.belongsTo(family);
  }

  private static Display display() {
    return PlatformUI.getWorkbench().getDisplay();
  }

}
