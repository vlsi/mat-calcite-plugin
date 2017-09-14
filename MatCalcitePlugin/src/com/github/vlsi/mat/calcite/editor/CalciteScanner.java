package com.github.vlsi.mat.calcite.editor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.sql.parser.SqlAbstractParserImpl;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IWhitespaceDetector;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import com.github.vlsi.mat.calcite.CalciteDataSource;
import com.google.common.base.Splitter;

public class CalciteScanner extends RuleBasedScanner {
	static class CalciteWhitespaceDetector implements IWhitespaceDetector {
		@Override
		public boolean isWhitespace(char c) {
			return Character.isWhitespace(c);
		}
	}

	public class CalciteWordDetector implements IWordDetector {
		@Override
		public boolean isWordStart(char c) {
			return Character.isJavaIdentifierStart(c);
		}

		@Override
		public boolean isWordPart(char c) {
			return Character.isJavaIdentifierPart(c) || c == '.';
		}
	}

	public CalciteScanner() {
		List<IRule> rules = new ArrayList<IRule>();

		Token other = new Token(CalcitePartitionScanner.OTHER);
		Token keywordToken = new Token(new TextAttribute(new Color(
				Display.getCurrent(), new RGB(127, 0, 85))));

		// "other" class is importantThis is required so Eclipse does not try to highlight in the middle of the words
		WordRule keywordRule = new WordRule(new CalciteWordDetector(),
				other, true);

		// Get keyword list from Calcite
		Connection con = null;
		try {
			con = CalciteDataSource.getConnection(null);
			String keywords = con.getMetaData().getSQLKeywords();
			for (String keyword : Splitter.on(',').split(keywords)) {
				keywordRule.addWord(keyword, keywordToken);
			}
		} catch (SQLException e) {

		} finally {
			CalciteDataSource.close(null, null, con);
		}

		for (String keyword : SqlAbstractParserImpl.getSql92ReservedWords()) {
			keywordRule.addWord(keyword, keywordToken);
		}

		rules.add(keywordRule);

		setRules(rules.toArray(new IRule[rules.size()]));
	}
}
