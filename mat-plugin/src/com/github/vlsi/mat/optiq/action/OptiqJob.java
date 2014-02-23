package com.github.vlsi.mat.optiq.action;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.registry.ArgumentSet;
import org.eclipse.mat.query.registry.QueryDescriptor;
import org.eclipse.mat.query.registry.QueryRegistry;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.AbstractPaneJob;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.ProgressMonitorWrapper;
import org.eclipse.ui.PartInitException;

import com.github.vlsi.mat.optiq.editor.OptiqPane;

public class OptiqJob extends AbstractPaneJob {

	private String sql;
	private OptiqPane optiqPane;
	private PaneState state;

	public OptiqJob(String sql, OptiqPane pane, PaneState state) {
		super(sql, pane);
		this.sql = sql;
		this.state = state;
		optiqPane = pane;
		this.setUser(true);
	}

	@Override
	protected IStatus doRun(IProgressMonitor monitor) {
		QueryDescriptor descriptor = QueryRegistry.instance().getQuery("optiq");//$NON-NLS-1$
		ArgumentSet argumentSet;
		try {
			argumentSet = descriptor.createNewArgumentSet(getPane().getEditor()
					.getQueryContext());
			argumentSet.setArgumentValue("sql", sql);//$NON-NLS-1$
			final QueryResult result = argumentSet
					.execute(new ProgressMonitorWrapper(monitor));
			optiqPane.getQueryString().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					optiqPane.initQueryResult(result, state);
				}
			});
		} catch (final Throwable e) {
			optiqPane.getQueryString().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					try {
						optiqPane.createExceptionPane(sql, e);
					} catch (PartInitException pie) {
						ErrorHelper.logThrowable(pie);
					}
				}
			});
			e.printStackTrace();
		}

		return Status.OK_STATUS;
	}

}
