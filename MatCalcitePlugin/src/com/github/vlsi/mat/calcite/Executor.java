package com.github.vlsi.mat.calcite;

import sqlline.MCPSqlLine;
import com.google.common.base.Joiner;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.VoidProgressListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

import static org.jline.terminal.TerminalBuilder.PROP_JANSI;

public class Executor implements IApplication {

  @Override
  public Object start(IApplicationContext context) {
    String[] args = (String[]) context.getArguments().get("application.args");
    char delimiter = ',';

    if (args.length == 0 || (args.length < 3 && !"sqlline".equalsIgnoreCase(args[0]))) {
      System.out.println("java com.github.vlsi.mat.calcite.Executor <heap-dump> <query> <result>");
      System.out.println("java com.github.vlsi.mat.calcite.Executor sqlline");
      return IApplication.EXIT_OK;
    }

    if ("sqlline".equalsIgnoreCase(args[0])) {
      try {
        System.setProperty(PROP_JANSI, Boolean.TRUE.toString());
        MCPSqlLine sqlLine = new MCPSqlLine();
        String[] sqllineArgs = new String[args.length - 1];
        System.arraycopy(args, 1, sqllineArgs, 0, args.length - 1);
        sqlLine.begin(sqllineArgs, null, false);
      } catch (IOException e) {
        e.printStackTrace();
        return IApplication.EXIT_OK;
      }
      return IApplication.EXIT_OK;
    }

    File heapFile = new File(args[0]);
    if (!heapFile.isFile()) {
      System.out.println("Heap dump file " + args[0] + " does not exists!");
      return IApplication.EXIT_OK;
    }

    File queryFile = new File(args[1]);
    if (!queryFile.isFile()) {
      System.out.println("Query file " + args[1] + " does not exists!");
      return IApplication.EXIT_OK;
    }
    File resultsFile = new File(args[2]);

    StringBuilder sbQuery = new StringBuilder();
    try (BufferedReader r = new BufferedReader(new FileReader(queryFile))) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        sbQuery.append(line).append("\n");
      }
    } catch (Exception e) {
      e.printStackTrace();
      return IApplication.EXIT_OK;
    }

    try (Connection con = CalciteDataSource.getConnection(openSnapshot(heapFile));
         BufferedWriter w = new BufferedWriter(new FileWriter(resultsFile))) {
      PreparedStatement ps = con.prepareStatement(sbQuery.toString());
      ResultSet rs = ps.executeQuery();
      ResultSetMetaData md = rs.getMetaData();
      Joiner joiner = Joiner.on(delimiter);
      Escaper escaper = Escapers.builder().addEscape('"', "\"\"").build();

      List<String> row = new ArrayList<>();
      final int columnCount = md.getColumnCount();
      for (int i = 1; i <= columnCount; i++) {
        row.add(escape(md.getColumnName(i), delimiter, escaper));
      }
      w.write(joiner.join(row));
      w.newLine();
      while (rs.next()) {
        row.clear();
        for (int i = 1; i <= columnCount; i++) {
          row.add(escape(String.valueOf(rs.getObject(i)), delimiter, escaper));
        }
        w.write(joiner.join(row));
        w.newLine();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return IApplication.EXIT_OK;
  }

  @Override
  public void stop() {

  }

  private static ISnapshot openSnapshot(File heapDump) throws SnapshotException {
    System.out.println("exists = " + heapDump.exists() + ", file = " + heapDump.getAbsolutePath());
    return SnapshotFactory.openSnapshot(heapDump, new VoidProgressListener());
  }

  private static String escape(String val, char delimiter, Escaper escaper) {
    return val.indexOf(delimiter) == -1 ? val : "\"" + escaper.escape(val) + "\"";
  }

}
