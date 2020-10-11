package com.github.vlsi.mat.tests.calcite;

import org.junit.Test;

import java.sql.SQLException;

public class TableFunctionsTests extends SampleHeapDumpTests {
  @Test
  public void testGetMapEntries() throws SQLException {
    returnsInOrder("select count(*) cnt from java.util.HashMap m, lateral table(getMapEntries(m.this)) r where m.size" +
        " = 4", "cnt", "4");
  }
}
