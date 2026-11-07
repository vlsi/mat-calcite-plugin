package com.github.vlsi.mat.calcite.mcp;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@link McpServer} on the MCP stdio transport: one JSON-RPC message per line on stdin, one response per line on
 * stdout. Launch with {@code MemoryAnalyzer -nosplash -application MatCalcitePlugin.mcp}.
 *
 * <p>Requests run one at a time on a worker thread, since heap dumps are not shared between threads. Messages that
 * stop or check on a running call (cancellation, ping, the cancel_query tool) are handled at once on the reading
 * thread, so they work while a long query runs.
 */
public class McpServerApplication implements IApplication {
  /** How long queued requests may still run after stdin is closed. */
  private static final int SHUTDOWN_GRACE_SECONDS = 60;

  private volatile McpServer server;

  @Override
  public Object start(IApplicationContext context) throws IOException, InterruptedException {
    // stdout carries the protocol only: anything MAT, Calcite or the plugin prints goes to stderr instead
    PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.out), false,
        StandardCharsets.UTF_8.name());
    System.setOut(System.err);

    McpServer mcp = new McpServer();
    server = mcp;
    ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "MCP worker");
      t.setDaemon(true);
      return t;
    });
    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      for (String line = in.readLine(); line != null; line = in.readLine()) {
        if (line.trim().isEmpty()) {
          continue;
        }
        String message = line;
        if (isImmediate(mcp, message)) {
          handle(protocol, mcp, message);
        } else {
          worker.execute(() -> handle(protocol, mcp, message));
        }
      }
    } finally {
      // stdin closed: answer what is already queued, then exit. A client that wants a faster exit sends SIGTERM,
      // as the MCP stdio transport describes.
      worker.shutdown();
      if (!worker.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
        worker.shutdownNow();
      }
      mcp.close();
    }
    return IApplication.EXIT_OK;
  }

  private static boolean isImmediate(McpServer mcp, String message) {
    try {
      return mcp.isImmediate(message);
    } catch (Throwable e) {
      return false; // let the worker answer it, with an error if need be
    }
  }

  /**
   * Handles one message and sends the answer. McpServer.handle answers every request, even when a tool fails; this
   * also keeps a failure outside it from killing the worker thread, which left a request unanswered for good.
   */
  private static void handle(PrintStream protocol, McpServer mcp, String message) {
    try {
      send(protocol, mcp.handle(message));
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private static void send(PrintStream protocol, String response) {
    if (response == null) {
      return;
    }
    synchronized (protocol) {
      protocol.print(response);
      protocol.print('\n');
      protocol.flush();
    }
  }

  @Override
  public void stop() {
    McpServer current = server;
    if (current != null) {
      current.close();
    }
  }
}
