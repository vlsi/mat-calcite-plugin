package com.github.vlsi.mat.optiq.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class StringScanner extends RuleBasedScanner {
	public StringScanner() {
		List<IRule> rules = new ArrayList<IRule>();

		Token stringToken = new Token(new TextAttribute(new Color(
				Display.getCurrent(), new RGB(42, 0, 255))));
		rules.add(new SingleLineRule("'", "'", stringToken));
		rules.add(new SingleLineRule("\"", "\"", stringToken));

		setRules(rules.toArray(new IRule[rules.size()]));
	}
}
