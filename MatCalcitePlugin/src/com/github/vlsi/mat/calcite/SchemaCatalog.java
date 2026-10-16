package com.github.vlsi.mat.calcite;

import org.apache.calcite.schema.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.WeakHashMap;

/**
 * Finds the tables and functions of the heap schema ({@link CalciteDataSource#getHeapSchema}), for the schema browser.
 */
public final class SchemaCatalog {
  /** Sub-schema of classes with their subclasses. */
  public static final String INSTANCEOF = "instanceof";
  /** Sub-schema of tables that are not classes, e.g. ThreadStackFrames. */
  public static final String NATIVE = "native";
  /** Prefix of internal tables that list object ids. */
  public static final String IDS_PREFIX = "$ids$:";

  private SchemaCatalog() {
  }

  /** A table, with the name to use in SQL. */
  public static final class TableRef {
    /** Schema that holds the table. */
    public final Schema schema;
    /** Name of the table inside {@link #schema}. */
    public final String tableName;
    /** Readable full name, e.g. {@code java.util.HashMap} or {@code native.ThreadStackFrames}. */
    public final String fullName;
    /** Name as written in SQL, quoted where needed. */
    public final String sqlName;

    TableRef(Schema schema, String tableName, String fullName, String sqlName) {
      this.schema = schema;
      this.tableName = tableName;
      this.fullName = fullName;
      this.sqlName = sqlName;
    }
  }

  /**
   * Classes whose full name contains the filter (ignoring case), then matching tables that are not classes, sorted
   * by name. An empty filter matches everything. Returns at most {@code limit} tables.
   */
  public static List<TableRef> findTables(Schema root, String filter, int limit) {
    String lower = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
    List<TableRef> result = new ArrayList<>();
    ClassNames classes = classNames(root);
    for (int i = 0; i < classes.names.length; i++) {
      if (result.size() >= limit) {
        return result;
      }
      if (classes.lowerCase[i].contains(lower)) {
        // The root schema lists every class by its full name, usable as a single quoted identifier
        String name = classes.names[i];
        result.add(new TableRef(root, name, name, SqlIdentifiers.quote(name)));
      }
    }
    Schema nativeSchema = root.getSubSchema(NATIVE);
    if (nativeSchema != null) {
      for (String name : new TreeSet<>(nativeSchema.getTableNames())) {
        if (result.size() >= limit) {
          return result;
        }
        String fullName = NATIVE + "." + name;
        if (fullName.toLowerCase(Locale.ROOT).contains(lower)) {
          result.add(new TableRef(nativeSchema, name, fullName, SqlIdentifiers.quotePath(fullName)));
        }
      }
    }
    return result;
  }

  /**
   * The class tables of each schema, sorted, with their lower-case names. The schema browser searches them on every
   * keystroke, and the root schema lists about four tables per class (plain, instanceof., $ids$: ...): sorting them
   * each time took long for dumps with 100,000 classes. A schema's tables never change.
   */
  private static final Map<Schema, ClassNames> CLASS_NAMES = Collections.synchronizedMap(new WeakHashMap<>());

  private static final class ClassNames {
    final String[] names;
    final String[] lowerCase;

    ClassNames(List<String> sorted) {
      names = sorted.toArray(new String[0]);
      lowerCase = new String[names.length];
      for (int i = 0; i < names.length; i++) {
        lowerCase[i] = names[i].toLowerCase(Locale.ROOT);
      }
    }
  }

  private static ClassNames classNames(Schema root) {
    return CLASS_NAMES.computeIfAbsent(root, schema -> {
      List<String> names = new ArrayList<>();
      for (String name : new TreeSet<>(schema.getTableNames())) {
        if (!name.startsWith(IDS_PREFIX) && !name.startsWith(INSTANCEOF + ".")) {
          names.add(name);
        }
      }
      return new ClassNames(names);
    });
  }

  /** Names of the SQL functions, sorted, without internal ones. */
  public static List<String> functionNames(Schema root) {
    List<String> names = new ArrayList<>();
    for (String name : new TreeSet<>(root.getFunctionNames())) {
      if (!"getHints".equals(name)) { // internal, used by content assist
        names.add(name);
      }
    }
    return names;
  }
}
