package com.github.vlsi.mat.calcite.cli;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.VoidProgressListener;
import org.jline.reader.Completer;
import sqlline.AbstractCommandHandler;
import sqlline.Commands;
import sqlline.DispatchCallback;
import sqlline.MCPDatabaseConnection;
import sqlline.MCPSqlLine;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class OpenDumpCommandHandler extends AbstractCommandHandler {
  public OpenDumpCommandHandler(MCPSqlLine sqlLine, List<Completer> completers,
      String helpText, String... cmds) {
    super(sqlLine, cmds, helpText, completers);
  }

  public OpenDumpCommandHandler(MCPSqlLine sqlLine, Completer completer,
      String helpText, String... cmds) {
    this(sqlLine, Collections.singletonList(completer), helpText, cmds);
  }

  public void execute(String line, DispatchCallback callback) {
    final String[] parts = sqlLine.split(line, " ", 0);
    if (parts.length != 2) {
      sqlLine.error("Usage: open_dump <path to file>");
      callback.setToFailure();
      return;
    }
    // replace ~/ with user directory
    final String filename = Commands.expand(parts[1]);
    if (!new File(filename).isFile()) {
      sqlLine.error("Dump file " + filename + " does not exists!");
      callback.setToFailure();
      return;
    }

    final MCPSqlLine mcpSqlLine = (MCPSqlLine) sqlLine;
    final MCPDatabaseConnection mcpDatabaseConnection;
    try {
      mcpDatabaseConnection = new MCPDatabaseConnection(mcpSqlLine, filename, openSnapshot(new File(filename)));
    } catch (SnapshotException e) {
      callback.setToFailure();
      mcpSqlLine.error(e);
      return;
    }
    try {
      mcpSqlLine.setUpConnection(mcpDatabaseConnection);
      callback.setToSuccess();
    } catch (Exception e) {
      mcpDatabaseConnection.close();
      mcpSqlLine.removeConnection(mcpDatabaseConnection);
      callback.setToFailure();
      mcpSqlLine.error(e);
    }
  }

  private static ISnapshot openSnapshot(File heapDump) throws SnapshotException {
    System.out.println("exists = " + heapDump.exists() + ", file = " + heapDump.getAbsolutePath());
    return SnapshotFactory.openSnapshot(heapDump, new VoidProgressListener());
  }
}
