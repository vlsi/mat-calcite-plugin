package com.github.vlsi.mat.calcite.editor;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;

public class CalcitePartitionScanner extends RuleBasedPartitionScanner {
	public static final String SQL_COMMENT = "__calcite_comment"; //$NON-NLS-1$
	public static final String SQL_STRING = "__calcite_string"; //$NON-NLS-1$
	public static final String SQL_QUOTED_IDENTIFIER = "__calcite_quoted_identifier"; //$NON-NLS-1$

	public CalcitePartitionScanner() {
		IToken comment = new Token(SQL_COMMENT);
		IToken string = new Token(SQL_STRING);
		IToken quotedIdentifier = new Token(SQL_QUOTED_IDENTIFIER);

		setPredicateRules(new IPredicateRule[] {
				new EndOfLineRule("//", comment),
				new EndOfLineRule("--", comment),
				new MultiLineRule("/*", "*/", comment),
				new SingleLineRule("\"", "\"", quotedIdentifier),
				new MultiLineRule("'", "'", string),
		});
	}
}
