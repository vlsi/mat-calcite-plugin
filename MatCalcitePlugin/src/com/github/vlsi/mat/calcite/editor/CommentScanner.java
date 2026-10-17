package com.github.vlsi.mat.calcite.editor;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;

import java.util.ArrayList;
import java.util.List;

public class CommentScanner extends RuleBasedScanner {
  public CommentScanner() {
    List<IRule> rules = new ArrayList<>();

    Token commentToken = SqlColors.token(SqlColors.COMMENT);
    rules.add(new EndOfLineRule("--", commentToken));
    rules.add(new EndOfLineRule("//", commentToken));
    rules.add(new MultiLineRule("/*", "*/", commentToken));

    setRules(rules.toArray(new IRule[0]));
  }
}
