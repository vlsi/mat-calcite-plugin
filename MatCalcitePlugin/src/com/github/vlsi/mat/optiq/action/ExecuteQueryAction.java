package com.github.vlsi.mat.optiq.action;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import com.github.vlsi.mat.optiq.editor.OptiqPane;

public class ExecuteQueryAction extends Action {
	private OptiqPane pane;
	private PaneState state;

	public ExecuteQueryAction(OptiqPane pane, PaneState state) {
		super(null);
		this.pane = pane;
		this.state = state;
	}

	@Override
	public void run() {
		StyledText queryString = pane.getQueryString();
		String query = queryString.getSelectionText();
		Point queryRange = queryString.getSelectionRange();

		if ("".equals(query)) //$NON-NLS-1$
		{
			query = queryString.getText();
			queryRange = new Point(0, queryString.getCharCount());
		}

		new OptiqJob(query, pane, state).schedule();
	}
}
