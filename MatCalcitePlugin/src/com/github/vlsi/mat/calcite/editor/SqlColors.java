package com.github.vlsi.mat.calcite.editor;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.graphics.Color;
import org.eclipse.ui.PlatformUI;

/**
 * Syntax colors of the SQL editor. The values are defined as theme colors in plugin.xml, so they follow the
 * current workbench theme and can be edited in Preferences / Appearance / Colors and Fonts.
 * The registry owns the colors, so they must not be disposed.
 */
public final class SqlColors {
  public static final String KEYWORD = "com.github.vlsi.mat.calcite.sql_keyword";
  public static final String FUNCTION = "com.github.vlsi.mat.calcite.sql_function";
  public static final String COMMENT = "com.github.vlsi.mat.calcite.sql_comment";
  public static final String STRING = "com.github.vlsi.mat.calcite.sql_string";

  private SqlColors() {
  }

  public static Color get(String id) {
    return PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry().get(id);
  }

  public static Token token(String id) {
    return new Token(new TextAttribute(get(id)));
  }
}
