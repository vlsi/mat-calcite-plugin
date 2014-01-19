package com.github.vlsi.mat.optiq;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import javax.sql.rowset.CachedRowSet;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;

public class RowSetTable implements IResultTable {

	private CachedRowSet rowSet;
	Column[] columns;

	public RowSetTable(CachedRowSet rowSet) throws SQLException {
		this.rowSet = rowSet;
		ResultSetMetaData md = rowSet.getMetaData();

		Column[] columns = new Column[md.getColumnCount()];
		for (int i = 0; i < columns.length; i++) {
			String className = md.getColumnClassName(i + 1);
			Class<?> clazz;
			try {
				clazz = Class.forName(className);
			} catch (ClassNotFoundException e) {
				clazz = String.class;
			}
			columns[i] = new Column(md.getColumnName(i + 1), clazz);
		}
		this.columns = columns;
	}

	@Override
	public ResultMetaData getResultMetaData() {

		return null;
	}

	@Override
	public Object getColumnValue(Object row, int columnIndex) {
		if (row == null)
			return "null";
		return ((Object[]) row)[columnIndex];
	}

	@Override
	public Column[] getColumns() {
		return columns;
	}

	@Override
	public IContextObject getContext(final Object row) {
		if (row == null && !(row instanceof Object[]))
			return null;
		final Object[] data = (Object[]) row;
		if (data.length < 1 || !(data[0] instanceof Integer))
			return null;
		return new IContextObject() {

			@Override
			public int getObjectId() {
				return (Integer) data[0];
			}
		};
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
		System.out.println("size: " + rowSet.size());
		return rowSet.size();
	}
}
