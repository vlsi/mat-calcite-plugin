package com.github.vlsi.mat.tests.calcite;

import org.junit.Test;

import java.sql.SQLException;

public class GetByKeyTests extends SampleHeapDumpTests {
  @Test
  public void testHashMapByKey() throws SQLException {
    returnsInOrder("select getByKey(m.this, 'GMT') v from java.util.HashMap m where getSize(m.this) = 208",
        "v",
        "Etc/GMT");
  }

  @Test
  public void testReferenceResult() throws SQLException {
    returnsInOrder("select length(getByKey(m.this, 'GMT')['value']) l from java.util.HashMap m where getSize(m.this) " +
            "= 208",
        "l",
        "7");
  }

  //@Test
  public void testConcurrentHashMap() throws SQLException {
    returnsInOrder("select getByKey(m.this, 'MST')['ID'] c from java.util.concurrent.ConcurrentHashMap m where " +
            "getByKey(m.this, 'MST') is not null",
        "c",
        "MST");
  }
}
