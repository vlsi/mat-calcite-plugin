package com.github.vlsi.mat.tests.calcite;

import com.github.vlsi.mat.calcite.Query;
import com.github.vlsi.mat.calcite.RowSetTable;

import org.eclipse.mat.util.VoidProgressListener;
import org.junit.Assert;
import org.junit.Test;

public class RowSetTableTests extends SampleHeapDumpTests {

  private RowSetTable run(String sql) throws Exception {
    Query query = new Query();
    query.snapshot = getSnapshot();
    query.sql = sql;
    return (RowSetTable) query.execute(new VoidProgressListener());
  }

  @Test
  public void objectRowsHaveIcons() throws Exception {
    RowSetTable table = run("select s.this from java.lang.String s limit 1");
    Assert.assertEquals(1, table.getRowCount());
    Assert.assertNotNull("icon for a String instance row", table.getIcon(table.getRow(0)));
  }

  @Test
  public void scalarRowsHaveNoIcons() throws Exception {
    RowSetTable table = run("select count(*) from java.lang.String");
    Assert.assertEquals(1, table.getRowCount());
    Assert.assertNull("icon for a row without objects", table.getIcon(table.getRow(0)));
  }
}
