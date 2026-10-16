package com.github.vlsi.mat.tests.calcite;

import com.github.vlsi.mat.calcite.QueryCancellation;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueryCancellationTests {
  @Test
  public void checkThrowsOnlyInsideACancelledScope() {
    QueryCancellation.check(); // no scope: nothing to cancel
    AtomicBoolean cancelled = new AtomicBoolean();
    try (QueryCancellation.Scope scope = QueryCancellation.enter(cancelled::get)) {
      QueryCancellation.check();
      cancelled.set(true);
      try {
        QueryCancellation.check();
        Assert.fail("expected a CancellationException");
      } catch (CancellationException expected) {
        // the scope's flag is set
      }
    }
    QueryCancellation.check(); // the scope is closed: its flag no longer applies
  }

  @Test
  public void nestedScopesRestoreTheOuterFlag() {
    AtomicBoolean outer = new AtomicBoolean(true);
    try (QueryCancellation.Scope a = QueryCancellation.enter(outer::get)) {
      try (QueryCancellation.Scope b = QueryCancellation.enter(() -> false)) {
        QueryCancellation.check();
      }
      try {
        QueryCancellation.check();
        Assert.fail("the outer flag should apply again");
      } catch (CancellationException expected) {
        // restored
      }
    }
  }

  @Test
  public void aFlagBelongsToItsThread() throws Exception {
    try (QueryCancellation.Scope scope = QueryCancellation.enter(() -> true)) {
      Throwable[] failure = new Throwable[1];
      Thread other = new Thread(() -> {
        try {
          QueryCancellation.check();
        } catch (Throwable e) {
          failure[0] = e;
        }
      });
      other.start();
      other.join();
      Assert.assertNull("another thread saw this thread's flag", failure[0]);
    }
  }
}
