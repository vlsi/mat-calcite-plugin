package com.github.vlsi.mat.tests.calcite;

import com.github.vlsi.mat.calcite.CalciteDataSource;

import com.google.common.base.Joiner;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Assert;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractQueriesTests {

  protected abstract ISnapshot getSnapshot();

  protected static ISnapshot openSnapshot(File heapDump) throws SnapshotException {
    System.out.println("exists = " + heapDump.exists() + ", file = " + heapDump.getAbsolutePath());
    return SnapshotFactory.openSnapshot(heapDump, new VoidProgressListener());
  }

  public static void closeSnapshot(ISnapshot snapshot) {
    SnapshotFactory.dispose(snapshot);
  }

  protected void returnsInOrder(String sql, String... expected) throws SQLException {
    String[] actuals = new String[0];
    try {
      actuals = executeToCSV(sql).toArray(actuals);
    } catch (SQLException e) {
      e.printStackTrace(); // tycho-surefire-plugin forces trimStackTrace=true
    }
    System.out.println("Arrays.toString(expected) = " + String.join("\n", expected));
    System.out.println("Arrays.toString(actuals) = " + String.join("\n", actuals));
    Assert.assertArrayEquals(sql, nnl(expected), nnl(actuals));
  }

  /**
   * Same as {@link #returnsInOrder(String, String...)}, but tolerant of MAT's own stack frame formatting.
   *
   * MAT renders the source location of a native method differently depending on its version. Before commit
   * 0c72944e ("Bug 581644 - Allow stack frames as pseudo-objects for HPROF snapshots") it produced
   * "(Native Method)"; from that commit on it also includes the source file, e.g.
   * "(Object.java(Native Method))".
   *
   * That string never passes through plugin logic - SnapshotThreadStacksTable returns
   * IStackFrame.getText() verbatim - so pinning the exact rendering only makes the test fail whenever MAT
   * changes its own formatting, for reasons unrelated to what is actually being tested. This helper strips
   * the optional source file prefix so the assertion still covers the stable, meaningful part: that
   * native.ThreadStackFrames is queryable, sorts correctly, and reports the right thread, depth and method.
   */
  protected void returnsInOrderIgnoringStackFrameSourceFile(String sql, String... expected)
      throws SQLException {
    String[] actuals;
    try {
      actuals = executeToCSV(sql).toArray(new String[0]);
    } catch (SQLException e) {
      e.printStackTrace(); // tycho-surefire-plugin forces trimStackTrace=true
      throw e;
    }
    Assert.assertArrayEquals(sql, stripStackFrameSourceFile(nnl(expected)),
        stripStackFrameSourceFile(nnl(actuals)));
  }

  private static Object[] stripStackFrameSourceFile(Object[] lines) {
    for (int i = 0; i < lines.length; i++) {
      if (lines[i] instanceof String) {
        // "(Object.java(Native Method))" -> "(Native Method)".
        // The [^()] guard keeps ordinary "(ReferenceQueue.java:142)" line references untouched, because
        // they are followed by ':' rather than by '('. Note the two trailing \\): one closes the inner
        // "(Native Method)", the other closes the outer "(...java(Native Method))". Omitting the second
        // leaves a dangling ")" in the result.
        lines[i] = ((String) lines[i]).replaceAll(
            "\\((?:[^()]+\\.(?:java|class))\\((Native Method|Compiled Code)\\)\\)", "($1)");
      }
    }
    return lines;
  }

  protected List<String> executeToCSV(String sql) throws SQLException {
    List<String> res = new ArrayList<>();
    System.out.println("sql = " + sql);
    try (Connection con = CalciteDataSource.getConnection(getSnapshot())) {
      PreparedStatement ps = con.prepareStatement(sql);
      ResultSet rs = ps.executeQuery();
      ResultSetMetaData md = rs.getMetaData();
      Joiner joiner = Joiner.on('|');
      List<String> row = new ArrayList<>();
      final int columnCount = md.getColumnCount();
      for (int i = 1; i <= columnCount; i++) {
        row.add(md.getColumnName(i));
      }
      res.add(joiner.join(row));
      while (rs.next()) {
        row.clear();
        for (int i = 1; i <= columnCount; i++) {
          row.add(String.valueOf(rs.getObject(i)));
        }
        res.add(joiner.join(row));
      }
    } catch (SQLException e) {
      e.printStackTrace(); // tycho-surefire-plugin forces trimStackTrace=true
      throw e;
    }
    return res;
  }

  protected void execute(String sql, int limit) throws SQLException {
    System.out.println("sql = " + sql);

    try (Connection con = CalciteDataSource.getConnection(getSnapshot());
         PreparedStatement ps = con.prepareStatement(sql);
         ResultSet rs = ps.executeQuery();
    ) {
      ResultSetMetaData md = rs.getMetaData();
      for (int j = 0; rs.next() && j < limit; j++) {
        for (int i = 1; i <= md.getColumnCount(); i++) {
          System.out.println(md.getColumnName(i) + ": " + rs.getObject(i));
        }
        System.out.println();
      }
    } catch (SQLException e) {
      e.printStackTrace(); // tycho-surefire-plugin forces trimStackTrace=true
      throw e;
    }
  }

  protected static String nnl(String text) {
    return text.replace("\r\n", "\n").replace("\r", "\n");
  }

  protected static Object[] nnl(Object[] array) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] instanceof String) {
        array[i] = nnl((String) array[i]);
      }
    }
    return array;
  }
}
