package com.github.vlsi.mat.calcite.mcp;

import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IDecorator;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IStructuredResult;
import org.eclipse.mat.query.refined.RefinedResultBuilder;
import org.eclipse.mat.query.refined.RefinedStructuredResult;
import org.eclipse.mat.query.refined.RefinedTable;
import org.eclipse.mat.query.refined.RefinedTree;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;

import java.util.List;

/**
 * Renders the result of a MAT query as text for an AI assistant: tables as markdown, trees as indented lines. Uses
 * MAT's own formatting, so object cells read like MAT's labels ({@code java.util.HashMap @ 0x7bfeb5848}).
 */
final class MatResultRenderer {
  private static final int MAX_CELL_LENGTH = 200;

  private final int maxRows;
  private final int maxDepth;
  private final int maxChildren;
  private int rows;
  /** Rows or tree children were left out. */
  private boolean truncated;
  /** Tree nodes below the depth limit were left out. */
  private boolean deeper;
  /** Label of the column to sort by, largest first, or null for the result's own order. */
  private String sortColumn;

  /**
   * @param maxRows     rows of a table, or lines of a tree
   * @param maxDepth    levels of a tree below the top elements (0 shows only the top elements)
   * @param maxChildren children shown per tree node
   */
  MatResultRenderer(int maxRows, int maxDepth, int maxChildren) {
    this.maxRows = maxRows;
    this.maxDepth = maxDepth;
    this.maxChildren = maxChildren;
  }

  /**
   * Sorts tables and trees by the column with this label, largest first. MAT translates its labels in language
   * packs, so without a column of that label the first column of byte sizes is used: "Shallow Heap" is one.
   */
  MatResultRenderer sortedBy(String columnLabel) {
    this.sortColumn = columnLabel;
    return this;
  }

  String render(ISnapshot snapshot, IResult result) {
    if (result instanceof TextResult) {
      return ((TextResult) result).getText();
    }
    if (!(result instanceof IStructuredResult)) {
      return result == null ? "(no result)" : result.getClass().getSimpleName() + ": " + result;
    }
    RefinedResultBuilder builder = new RefinedResultBuilder(new HeapQueryContext(snapshot), (IStructuredResult) result);
    if (sortColumn != null) {
      Column[] columns = ((IStructuredResult) result).getColumns();
      int index = -1;
      for (int i = 0; i < columns.length && index < 0; i++) {
        if (columns[i].getLabel().equals(sortColumn)) {
          index = i;
        }
      }
      for (int i = 0; i < columns.length && index < 0; i++) {
        if (Bytes.class.equals(columns[i].getType())) {
          index = i;
        }
      }
      if (index >= 0) {
        builder.setSortOrder(index, Column.SortDirection.DESC);
      }
    }
    RefinedStructuredResult refined = builder.build();
    StringBuilder sb = new StringBuilder();
    if (refined instanceof RefinedTree) {
      renderTree(sb, (RefinedTree) refined);
    } else {
      renderTable(sb, (RefinedTable) refined);
    }
    sb.append('\n').append(rows).append(rows == 1 ? " row" : " rows");
    if (truncated) {
      sb.append(" shown; more exist (raise max_rows").append(refined instanceof RefinedTree ? " or max_children)" : ")");
    }
    if (deeper) {
      sb.append("; ").append("deeper levels not shown (raise depth)");
    }
    return sb.toString();
  }

  private void renderTable(StringBuilder sb, RefinedTable table) {
    Column[] columns = table.getColumns();
    sb.append('|');
    for (Column column : columns) {
      sb.append(' ').append(cell(column.getLabel())).append(" |");
    }
    sb.append("\n|");
    for (int i = 0; i < columns.length; i++) {
      sb.append("---|");
    }
    sb.append('\n');
    for (Object row : table.getRows()) {
      if (rows == maxRows) {
        truncated = true;
        return;
      }
      sb.append('|');
      for (int i = 0; i < columns.length; i++) {
        sb.append(' ').append(cell(value(table, columns[i], row, i))).append(" |");
      }
      sb.append('\n');
      rows++;
    }
  }

  private void renderTree(StringBuilder sb, RefinedTree tree) {
    Column[] columns = tree.getColumns();
    for (int i = 0; i < columns.length; i++) {
      sb.append(i == 0 ? "" : " | ").append(columns[i].getLabel());
    }
    sb.append('\n');
    renderNodes(sb, tree, tree.getElements(), 0);
  }

  private void renderNodes(StringBuilder sb, RefinedTree tree, List<?> nodes, int depth) {
    int shown = 0;
    for (Object node : nodes) {
      if (rows == maxRows) {
        truncated = true;
        return;
      }
      if (depth > 0 && shown == maxChildren) {
        truncated = true;
        indent(sb, depth).append("... ").append(nodes.size() - shown).append(" more\n");
        return;
      }
      indent(sb, depth);
      Column[] columns = tree.getColumns();
      for (int i = 0; i < columns.length; i++) {
        sb.append(i == 0 ? "" : " | ").append(cell(value(tree, columns[i], node, i)));
      }
      sb.append('\n');
      rows++;
      shown++;
      if (tree.hasChildren(node)) {
        if (depth < maxDepth) {
          renderNodes(sb, tree, tree.getChildren(node), depth + 1);
        } else {
          deeper = true;
        }
      }
    }
  }

  /** The formatted value with the column's decorations, e.g. MAT's GC root marks on a path to GC roots. */
  private static String value(RefinedStructuredResult result, Column column, Object row, int index) {
    String text = result.getFormattedColumnValue(row, index);
    IDecorator decorator = column.getDecorator();
    if (decorator == null) {
      return text;
    }
    String prefix = decorator.prefix(row);
    String suffix = decorator.suffix(row);
    return (prefix == null ? "" : prefix + " ") + text + (suffix == null ? "" : " " + suffix);
  }

  private static StringBuilder indent(StringBuilder sb, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("  ");
    }
    return sb;
  }

  private static String cell(String text) {
    String value = text == null ? "" : text;
    if (value.length() > MAX_CELL_LENGTH) {
      value = value.substring(0, MAX_CELL_LENGTH) + "...";
    }
    return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
  }
}
