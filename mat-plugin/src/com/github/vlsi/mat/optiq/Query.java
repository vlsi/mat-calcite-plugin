package com.github.vlsi.mat.optiq;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;

import net.hydromatic.optiq.SchemaPlus;
import net.hydromatic.optiq.jdbc.OptiqConnection;

import org.eclipse.mat.query.Column;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.query.ResultMetaData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

@Name("SQL via optiq")
@Category("SQL")
public class Query implements IQuery {

	@Argument
	public ISnapshot snapshot;

	@Argument(flag = Argument.UNFLAGGED)
	public String sql;

	static class Result implements IResultTable {
		@Override
		public Object getColumnValue(Object row, int columnIndex) {
			return null;
		}

		@Override
		public Column[] getColumns() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IContextObject getContext(Object row) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ResultMetaData getResultMetaData() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object getRow(int rowId) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getRowCount() {
			// TODO Auto-generated method stub
			return 0;
		}

	}

	@Override
	public IResult execute(IProgressListener listener) throws Exception {
		Class.forName("net.hydromatic.optiq.jdbc.Driver");
		Connection connection = DriverManager.getConnection("jdbc:optiq:lex=JAVA");
		OptiqConnection con = connection.unwrap(OptiqConnection.class);

		SchemaPlus root = con.getRootSchema();
		HeapSchema heapSchema = new HeapSchema(root, "HEAP", snapshot);

		root.add(heapSchema);

		con.setSchema(heapSchema.getName());

		Statement st = con.createStatement();
		ResultSet rs = st.executeQuery(sql);

		RowSetFactory rowSetFactory = RowSetProvider.newFactory();
		CachedRowSet rowSet = rowSetFactory.createCachedRowSet();
		rowSet.populate(rs);

		rs.close();
		st.close();
		connection.close();
		return new RowSetTable(rowSet);
	}

}
