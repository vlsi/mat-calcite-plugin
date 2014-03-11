package com.github.vlsi.mat.optiq;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;

import net.hydromatic.optiq.Schema;
import net.hydromatic.optiq.SchemaPlus;
import net.hydromatic.optiq.jdbc.OptiqConnection;

import org.eclipse.mat.snapshot.ISnapshot;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.MapMaker;

public class OptiqDataSource {

	private static Map<ISnapshot, HeapSchema> SCHEMA_CACHE = new MapMaker()
	.weakKeys().makeMap();

	private static LoadingCache<ISnapshot, Schema> SCH2EMA_CACHE = CacheBuilder
			.newBuilder()
			.weakKeys().build(new CacheLoader<ISnapshot, Schema>() {
				@Override
				public Schema load(ISnapshot key) throws Exception {
					return null;
				}
			});

	public static Connection getConnection(ISnapshot snapshot)
			throws SQLException {
		try {
			Class.forName("net.hydromatic.optiq.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			throw new SQLException(
					"Unable to load Optiq JDBC driver", e);
		}
		Properties info = new Properties();
		info.put("lex", "JAVA");
		info.put("quoting", "DOUBLE_QUOTE");
		Connection connection = DriverManager.getConnection(
				"jdbc:optiq:", info);
		OptiqConnection con = connection
				.unwrap(OptiqConnection.class);

		SchemaPlus root = con.getRootSchema();
		HeapSchema prototype = SCHEMA_CACHE.get(snapshot);
		HeapSchema heapSchema = new HeapSchema(root, "HEAP",
				snapshot, prototype);
		if (prototype == null)
			SCHEMA_CACHE.put(snapshot, heapSchema);

		root.add(heapSchema);
		con.setSchema(heapSchema.getName());

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
