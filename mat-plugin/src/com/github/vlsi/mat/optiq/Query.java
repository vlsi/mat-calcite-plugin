package com.github.vlsi.mat.optiq;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

@Name("SQL via optiq")
@Category("SQL")
@CommandName("optiq")
public class Query implements IQuery {

	@Argument
	public ISnapshot snapshot;

	@Argument(flag = Argument.UNFLAGGED)
	public String sql;

	@Override
	public IResult execute(IProgressListener listener) throws Exception {
		Connection con = null;
		Statement st = null;
		ResultSet rs = null;

		try {
			con = OptiqDataSource.getConnection(snapshot);

			st = con.createStatement();
			rs = st.executeQuery(sql);

			RowSetFactory rowSetFactory = RowSetProvider.newFactory();
			CachedRowSet rowSet = rowSetFactory.createCachedRowSet();
			rowSet.populate(rs);

			return new RowSetTable(rowSet);
		} finally {
			OptiqDataSource.close(rs, st, con);
		}
	}

}
