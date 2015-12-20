package com.github.vlsi.mat.calcite.action;

import com.github.vlsi.mat.calcite.editor.CalcitePane;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CalciteJob extends AbstractPaneJob {

	private String sql;
	private CalcitePane calcitePane;
	private PaneState state;

	public CalciteJob(String sql, CalcitePane pane, PaneState state) {
		super(sql, pane);
		this.sql = sql;
		this.state = state;
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
			calcitePane.getQueryString().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					calcitePane.initQueryResult(result, state);
				}
			});
		} catch (final Throwable e) {
			calcitePane.getQueryString().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					StringWriter sw = new StringWriter();
					if (sql != null)
						sw.append(sql).append('\n');
					e.printStackTrace(new PrintWriter(sw));
					String exceptionText = sw.toString();

					TextResult tr = new TextResult(exceptionText, false);
					QueryResult result = new QueryResult(descriptor, "calcite", tr);
					calcitePane.initQueryResult(result, state);
				}
			});
			e.printStackTrace();
		}

		return Status.OK_STATUS;
	}

}
