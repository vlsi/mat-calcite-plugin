package com.github.vlsi.mat.calcite;

import com.github.vlsi.mat.calcite.neo.PackageSchema;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.janino.CompilerFactory;
import org.eclipse.mat.snapshot.ISnapshot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class CalciteDataSource {

  private static final LoadingCache<ISnapshot, Schema> SCHEMA_CACHE = CacheBuilder
      .newBuilder()
      .weakKeys().build(new CacheLoader<ISnapshot, Schema>() {
        @Override
        public Schema load(ISnapshot key) throws Exception {
//                    return new HeapSchema(key);
          return PackageSchema.resolveSchema(key);
        }
      });

  private static boolean initCompilerDone;

  public static Connection getConnection(ISnapshot snapshot)
      throws SQLException {
    initJanino();

    try {
      Class.forName("org.apache.calcite.jdbc.Driver");
    } catch (ClassNotFoundException e) {
      throw new SQLException(
          "Unable to load Calcite JDBC driver", e);
    }
    Properties info = new Properties();
    info.put("lex", "JAVA");
    info.put("quoting", "DOUBLE_QUOTE");
    info.put("conformance", "LENIENT"); // enable cross apply, etc
    Connection connection = DriverManager.getConnection(
        "jdbc:calcite:", info);
    CalciteConnection con = connection
        .unwrap(CalciteConnection.class);

    if (snapshot == null) {
      return connection;
    }

    if ("HEAP".equals(con.getSchema())) {
      return connection;
    }

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

  private static void initJanino() throws SQLException {
    // For unknown reason, threadContextClassLoader.getResource("org.codehaus.commons.compiler.properties")
    // returns null when accessed via BundleClassLoader
    // We make a shortcut
    // Some OSGi WA might probably exist
    if (initCompilerDone) {
      return;
    }
    initCompilerDone = true;
    try {
      if (CompilerFactoryFactory.getDefaultCompilerFactory(CompilerFactory.class.getClassLoader()) == null) {
        throw new SQLException("Janino compiler is not initialized: CompilerFactoryFactory.getDefaultCompilerFactory" +
            "() == null");
      }
    } catch (Exception e) {
      throw new SQLException("Unable to load Janino compiler", e);
    }
  }

  public static void close(ResultSet rs, Statement st, Connection con) {
    if (rs != null) {
      try {
        rs.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    if (st != null) {
      try {
        st.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    if (con != null) {
      try {
        con.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

}
