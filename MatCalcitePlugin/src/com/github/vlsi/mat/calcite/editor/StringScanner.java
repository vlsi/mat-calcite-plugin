package com.github.vlsi.mat.calcite.editor;

import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;

import java.util.ArrayList;
import java.util.List;

public class StringScanner extends RuleBasedScanner {
  public StringScanner() {
    List<IRule> rules = new ArrayList<>();

    Token stringToken = SqlColors.token(SqlColors.STRING);
    rules.add(new SingleLineRule("'", "'", stringToken));
    rules.add(new SingleLineRule("\"", "\"", stringToken));

    setRules(rules.toArray(new IRule[0]));
  }
}
