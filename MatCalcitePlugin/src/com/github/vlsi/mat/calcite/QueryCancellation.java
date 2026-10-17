package com.github.vlsi.mat.calcite;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Lets long scans stop when the query that runs them is cancelled. The caller marks the code it runs with
 * {@link #enter}, and the heap object scan calls {@link #check} for each object.
 *
 * <p>This deliberately does not use {@link Thread#interrupt()}: MAT reads its index files through a
 * {@link java.nio.channels.FileChannel}, which an interrupt closes for good, and an interrupt sent just as one call
 * finishes would hit the next call on the same thread. A flag belongs to one call only.
 */
public final class QueryCancellation {
  private static final ThreadLocal<BooleanSupplier> CURRENT = new ThreadLocal<>();

  private QueryCancellation() {
  }

  /**
   * Runs the current thread's work under the given flag until the returned scope is closed.
   *
   * @param cancelled tells whether the work has been cancelled; called often, so it must be cheap
   */
  public static Scope enter(BooleanSupplier cancelled) {
    BooleanSupplier previous = CURRENT.get();
    CURRENT.set(cancelled);
    return () -> {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    };
  }

  /** Throws {@link CancellationException} if the current thread's work was cancelled. */
  public static void check() {
    BooleanSupplier cancelled = CURRENT.get();
    if (cancelled != null && cancelled.getAsBoolean()) {
      throw new CancellationException("Query cancelled");
    }
  }

  /** The extent of {@link #enter}; closing it restores the previous flag. */
  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
