package com.github.vlsi.mat.optiq.action;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.swt.custom.StyledText;
import com.github.vlsi.mat.optiq.editor.OptiqPane;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import java.util.regex.Pattern;

public class ExecuteQueryAction extends Action {
	public static final Pattern STARTS_WITH_EXPLAIN_PLAN = Pattern.compile("^\\s*explain\\s+plan\\s+for", Pattern.CASE_INSENSITIVE);

	private boolean doExplain;
	private OptiqPane pane;
	private PaneState state;

	public ExecuteQueryAction(OptiqPane pane, PaneState state, boolean doExplain) {
		super(null);
		this.pane = pane;
		this.state = state;
		this.doExplain = doExplain;
		setText(doExplain?"Explain":"Run");
		setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("MatCalcitePlugin", doExplain?"icons/explain.png":"icons/run.png"));
	}

	private String getSelectedQuery() {
		StyledText queryString = pane.getQueryString();
		String query = queryString.getSelectionText();

		if ("".equals(query)) //$NON-NLS-1$
		{
			query = queryString.getText();
		}

		// Temporary workaround for https://issues.apache.org/jira/browse/CALCITE-459
		query = query + queryString.getLineDelimiter();

		return query;
	}

	@Override
	public void run() {
		String query = getSelectedQuery();
		//TODO: actually check with this regexp doesn't work if there are comments in the beginning of the query
		if (doExplain && !STARTS_WITH_EXPLAIN_PLAN.matcher(query).find()) {
			query = "explain plan for " + query;
		}
		new OptiqJob(query, pane, state).schedule();
	}
}
