package com.github.vlsi.mat.calcite;

import org.apache.calcite.sql.parser.SqlAbstractParserImpl;
import org.apache.calcite.sql.parser.SqlParser;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Writes identifiers for the SQL dialect of {@link CalciteDataSource#getConnection}: Calcite's JAVA lexer, which is
 * case-sensitive and quotes with double quotes.
 */
public final class SqlIdentifiers {
  private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  /** Calcite's parser reserves more than SQL-92 does, e.g. REF, so java.lang.ref must be written java.lang."ref". */
  private static final SqlAbstractParserImpl.Metadata PARSER = SqlParser.create("").getMetadata();

  private SqlIdentifiers() {
  }

  /**
   * Quotes an identifier unless it is a plain identifier that is not a reserved word, e.g. {@code size} stays as is,
   * {@code @classLoader} becomes {@code "@classLoader"} and {@code java.lang.String} becomes
   * {@code "java.lang.String"} (the heap schema lists every class by its full name too).
   */
  public static String quote(String identifier) {
    if (PLAIN_IDENTIFIER.matcher(identifier).matches() && !PARSER.isReservedWord(identifier.toUpperCase(Locale.ROOT))) {
      return identifier;
    }
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }

  /**
   * Quotes each part of a dotted schema path where needed, e.g. {@code java.util."HashMap$Node"}.
   */
  public static String quotePath(String path) {
    StringBuilder sb = new StringBuilder();
    for (String part : path.split("\\.")) {
      if (sb.length() > 0) {
        sb.append('.');
      }
      sb.append(quote(part));
    }
    return sb.toString();
  }
}
