package com.github.vlsi.mat.calcite;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.eclipse.mat.snapshot.ISnapshot;

import java.sql.*;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class CalciteDataSource
{

	private static LoadingCache<ISnapshot, Schema> SCHEMA_CACHE = CacheBuilder
			.newBuilder()
			.weakKeys().build(new CacheLoader<ISnapshot, Schema>() {
				@Override
				public Schema load(ISnapshot key) throws Exception {
					return new HeapSchema(key);
				}
			});

	public static Connection getConnection(ISnapshot snapshot)
			throws SQLException {
		try {
			Class.forName("org.apache.calcite.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			throw new SQLException(
					"Unable to load Caclite JDBC driver", e);
		}
		Properties info = new Properties();
		info.put("lex", "JAVA");
		info.put("quoting", "DOUBLE_QUOTE");
		Connection connection = DriverManager.getConnection(
				"jdbc:calcite:", info);
		CalciteConnection con = connection
				.unwrap(CalciteConnection.class);

		if (snapshot == null)
			return connection;

		if ("HEAP".equals(con.getSchema()))
			return connection;

		SchemaPlus root = con.getRootSchema();
		Schema heapSchema;
		try {
			heapSchema = SCHEMA_CACHE.get(snapshot);
		} catch (ExecutionException e) {
			throw new SQLException("Unable to create heap schema", e);
		}
		root.add("HEAP", heapSchema);
		con.setSchema("HEAP");

		return connection;
	}

	public static void close(ResultSet rs, Statement st, Connection con) {
		if (rs != null)
			try {
				rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		if (st != null)
			try {
				st.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		if (con != null)
			try {
				con.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
	}

}
