package com.github.vlsi.mat.tests.calcite;

import org.junit.Test;

import java.sql.SQLException;

public class TableFunctionsTests extends SampleHeapDumpTests {
  @Test
  public void testGetMapEntries() throws SQLException {
    returnsInOrder("select count(*) cnt from java.util.HashMap m, lateral table(getMapEntries(m.this)) r where m.size" +
        " = 4", "cnt", "4");
  }

  @Test
  public void selectOutboundReferences() throws SQLException {
    returnsInOrder("select sum(retainedSize(og.this)) retained_size, count(og.name) cnt_name from java.util.HashMap " +
                   "hm, lateral (select * from table(getOutboundReferences(hm.this))) as og(name, this)",
                   "retained_size|cnt_name",
                   "113712|155");
  }

  @Test
  public void selectOutboundReferencesCrossApply() throws SQLException {
    returnsInOrder("select sum(retainedSize(og.this)) retained_size, count(og.name) cnt_name from java.util.HashMap " +
                   "hm cross apply table(getOutboundReferences(hm.this)) as og(name, this)",
                   "retained_size|cnt_name",
                   "113712|155");
  }
}
