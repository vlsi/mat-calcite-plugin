package com.github.vlsi.mat.calcite.action;

import com.github.vlsi.mat.calcite.editor.CalcitePane;

import org.eclipse.jface.action.Action;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import java.util.regex.Pattern;

public class ExecuteQueryAction extends Action {
  public static final Pattern STARTS_WITH_EXPLAIN_PLAN = Pattern.compile("^\\s*explain\\s+plan\\s+for",
      Pattern.CASE_INSENSITIVE);

  private final boolean doExplain;
  private final CalcitePane pane;
  private final PaneState state;

  public ExecuteQueryAction(CalcitePane pane, PaneState state, boolean doExplain) {
    super(null);
    this.pane = pane;
    this.state = state;
    this.doExplain = doExplain;
    setText(doExplain ? "Explain" : "Run");
    setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("MatCalcitePlugin", doExplain ? "resources/icons" +
        "/explain.png" : "resources/icons/run.png"));
  }

  @Override
  public void run() {
    StyledText queryString = pane.getQueryString();
    String query = queryString.getSelectionText();
    // Where the SQL that runs starts in the editor, so an error position can be shown at the right place
    int offset = queryString.getSelection().x;
    if ("".equals(query)) //$NON-NLS-1$
    {
      query = queryString.getText();
      offset = 0;
    }
    // Temporary workaround for https://issues.apache.org/jira/browse/CALCITE-459
    query = query + queryString.getLineDelimiter();

    //TODO: actually check with this regexp doesn't work if there are comments in the beginning of the query
    String prefix = "";
    if (doExplain && !STARTS_WITH_EXPLAIN_PLAN.matcher(query).find()) {
      prefix = "explain plan for ";
    }
    pane.clearErrorHighlight();
    new CalciteJob(prefix + query, pane, state, offset, prefix.length()).schedule();
  }
}
