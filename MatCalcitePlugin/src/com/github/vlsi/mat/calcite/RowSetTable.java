package com.github.vlsi.mat.calcite;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IIconProvider;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.query.Icons;

import java.net.URL;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.rowset.CachedRowSet;

public class RowSetTable implements IResultTable, IIconProvider {

  private final ResultMetaData metaData;
  private final CachedRowSet rowSet;
  Column[] columns;
  int idColumnPosition = -1;

  public RowSetTable(CachedRowSet rowSet) throws SQLException {
    this.rowSet = rowSet;
    ResultSetMetaData md = rowSet.getMetaData();

    Column[] columns = new Column[md.getColumnCount()];

    ResultMetaData.Builder mdBuilder = new ResultMetaData.Builder();
    for (int i = 0; i < columns.length; i++) {
      String className = md.getColumnClassName(i + 1);
      Class<?> clazz;
      try {
        clazz = Class.forName(className);
      } catch (ClassNotFoundException e) {
        clazz = String.class;
      }
      String columnName = md.getColumnName(i + 1);
      columns[i] = new Column(columnName, clazz);
      if (md.getColumnType(i + 1) == Types.JAVA_OBJECT) {
        // Most likely a HeapReference
        final int columnPosition = i;
        String tableName = md.getTableName(i + 1);
        final String label;
        if (tableName == null || tableName.isEmpty()) {
          label = columnName;
        } else {
          label = tableName + "." + columnName;
        }
        mdBuilder.addContext(new ContextProvider(label) {
          @Override
          public IContextObject getContext(Object row) {
            return RowSetTable.getContext(row, columnPosition);
          }
        });
        if (idColumnPosition == -1) {
          // Use first object column as context provider (e.g. in case "this" column is missing)
          idColumnPosition = i;
        }
      }
      if (idColumnPosition == -1 && "this".equals(columns[i].getLabel())) {
        idColumnPosition = i;
      }
    }
    this.metaData = mdBuilder.build();
    this.columns = columns;
  }

  @Override
  public ResultMetaData getResultMetaData() {
    return metaData;
  }

  @Override
  public Object getColumnValue(Object row, int columnIndex) {
    if (row == null) {
      return "null";
    }
    return ((Object[]) row)[columnIndex];
  }

  @Override
  public Column[] getColumns() {
    return columns;
  }

  @Override
  public IContextObject getContext(final Object row) {
    if (idColumnPosition == -1) {
      return null;
    }
    return getContext(row, idColumnPosition);
  }

  /**
   * Icon of the object in the context column (instance, class, array, class loader, with a GC root overlay),
   * the same icons MAT shows in its own object lists.
   */
  @Override
  public URL getIcon(Object row) {
    if (idColumnPosition == -1 || !(row instanceof Object[])) {
      return null;
    }
    Object[] data = (Object[]) row;
    if (idColumnPosition >= data.length || !(data[idColumnPosition] instanceof HeapReference)) {
      return null;
    }
    IObject o = ((HeapReference) data[idColumnPosition]).getIObject();
    return Icons.forObject(o.getSnapshot(), o.getObjectId());
  }

  private static IContextObject getContext(final Object row, final int columnPosition) {
    if (!(row instanceof Object[])) {
      return null;
    }
    final Object[] data = (Object[]) row;
    if (columnPosition >= data.length) {
      return null;
    }
    final Object ref = data[columnPosition];
    if (!(ref instanceof HeapReference)) {
      return null;
    }
    return () -> ((HeapReference) ref).getIObject().getObjectId();
  }

  @Override
  public Object getRow(int rowId) {
    try {
      rowSet.absolute(rowId + 1);
    } catch (SQLException e1) {
      e1.printStackTrace();
      return null;
    }
    Object[] row = new Object[columns.length];
    for (int i = 0; i < row.length; i++) {
      try {
        row[i] = rowSet.getObject(i + 1);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return row;
  }

  @Override
  public int getRowCount() {
    return rowSet.size();
  }
}
