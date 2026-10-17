package com.github.vlsi.mat.calcite.mcp;

import com.github.vlsi.mat.calcite.QueryCancellation;

import org.eclipse.mat.util.IProgressListener;
import org.eclipse.mat.util.VoidProgressListener;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cancellation of one running tool call. A cancel can come from another thread: an MCP
 * {@code notifications/cancelled}, the {@code cancel_query} tool or a timeout.
 *
 * <p>It stops the work in three ways, because none reaches everything: the JDBC statement's cancel flag (checked by
 * Calcite between result rows), {@link QueryCancellation} (checked by the heap object scan while an aggregation runs
 * and by the long analyses) and a progress listener (checked by MAT queries). It never interrupts the thread: an
 * interrupt closes MAT's index files, and one that arrives late would stop the next call.
 */
final class Cancellation {
  final Object requestId;
  private final AtomicReference<String> reason = new AtomicReference<>();
  private volatile Statement statement;

  Cancellation(Object requestId) {
    this.requestId = requestId;
  }

  /** Stops the call, keeping the first reason given. Safe to call from any thread. */
  void cancel(String why) {
    if (!reason.compareAndSet(null, why)) {
      return;
    }
    Statement current = statement;
    if (current != null) {
      try {
        current.cancel();
      } catch (SQLException | RuntimeException e) {
        // the statement may already be closed
      }
    }
  }
  boolean isCancelled() {
    return reason.get() != null;
  }

  /** Why the call was cancelled, e.g. "Query cancelled", or null. */
  String reason() {
    return reason.get();
  }

  /** Registers the running JDBC statement, so a cancel can reach it. */
  void setStatement(Statement statement) {
    this.statement = statement;
    if (isCancelled()) {
      try {
        statement.cancel();
      } catch (SQLException e) {
        // ignore: the call is being abandoned anyway
      }
    }
  }

  /** A progress listener for MAT queries that reports this cancellation. */
  IProgressListener listener() {
    return new VoidProgressListener() {
      @Override
      public boolean isCanceled() {
        return Cancellation.this.isCancelled();
      }
    };
  }
}
