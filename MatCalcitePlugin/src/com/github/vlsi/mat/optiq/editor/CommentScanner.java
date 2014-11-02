package com.github.vlsi.mat.optiq.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class CommentScanner extends RuleBasedScanner {
	public CommentScanner() {
		List<IRule> rules = new ArrayList<IRule>();

		Token commentToken = new Token(new TextAttribute(new Color(
				Display.getCurrent(), new RGB(63, 127, 95))));
		rules.add(new EndOfLineRule("--", commentToken));
		rules.add(new EndOfLineRule("//", commentToken));
		rules.add(new MultiLineRule("/*", "*/", commentToken));

		setRules(rules.toArray(new IRule[rules.size()]));
	}
}
