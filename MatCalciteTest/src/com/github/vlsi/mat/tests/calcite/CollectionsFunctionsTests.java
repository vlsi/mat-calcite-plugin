package com.github.vlsi.mat.tests.calcite;

import org.junit.Test;

import java.sql.SQLException;

public class CollectionsFunctionsTests extends SampleHeapDumpTests {
  // asMap

  @Test
  public void testAsMap() throws SQLException {
    returnsInOrder("select getField(asMap(m.this)['org.codehaus.plexus.classworlds'],'pkgName') pkgName from java" +
                   ".util.HashMap m where m.size = 4",
                   "pkgName", "org.codehaus.plexus.classworlds");
  }

  // asMultiSet

  @Test
  public void testAsMultiset() throws SQLException {
    returnsInOrder("select n from (\n" +
                   "select \n" +
                   " toString(getField(r.this, 'name')) n\n" +
                   "from \n" +
                   " java.util.logging.LogManager lm,\n" +
                   " unnest(asMultiSet(lm.rootLogger['kids'])) r(this)\n" +
                   ") order by n",
                   "n",
                   "com.google.inject.internal.util.Stopwatch",
                   "global");
  }

  // asArray

  @Test
  public void testAsArray() throws SQLException {
    returnsInOrder("select \n" +
                   " g.index,\n" +
                   " toString(g.threadName) threadName\n" +
                   "from \n" +
                   " java.lang.ThreadGroup tg,\n" +
                   " unnest(asArray(tg.threads)) with ordinality g(threadName, index)\n" +
                   "where \n" +
                   " toString(tg.name)='system'",
                   "index|threadName",
                   "1|Reference Handler",
                   "2|Finalizer",
                   "3|Signal Dispatcher",
                   "4|null");
  }

  // asByteArray

  @Test
  public void testAsByteArray() throws SQLException {
    returnsInOrder("select asByteArray(ps.\"out\"['buf'])[1] b from java.io.PrintStream ps",
                   "b", "0", "0");
  }

  // asShortArray

  @Test
  public void testAsShortArray() throws SQLException {
    // Test dump has no short[] objects, so we just check that function could be invoked
    returnsInOrder("select cardinality(asShortArray(o.this)) m from java.lang.Object o limit 1",
                   "m", "0");
  }

  // asIntArray

  @Test
  public void testAsIntArray() throws SQLException {
    returnsInOrder("select sum(cs.v) s from java.util.GregorianCalendar c, unnest(asIntArray(c.stamp)) cs(v)",
                   "s", "68");
  }

  // asLongArray

  @Test
  public void testAsLongArray() throws SQLException {
    returnsInOrder("select asLongArray(m.this)[14] c from \"long[]\" m where cardinality(asLongArray(m.this)) = 16",
                   "c", "1388527200000");
  }

  // asBooleanArray

  @Test
  public void testAsBooleanArray() throws SQLException {
    returnsInOrder("select min(case when v.this then 1 else 0 end) minv, max(case when v.this then 1 else 0 end) maxv" +
                   " from \"boolean[]\" b, unnest(asBooleanArray(b.this)) v(this)",
                   "minv|maxv","0|1");
  }

  // asCharArray

  @Test
  public void testAsCharArray() throws SQLException {
    returnsInOrder("select listagg(toString(v.this),'') ns from java.lang.String s, unnest(asCharArray(s.\"value\")) " +
                   "v(this) where toString(s.this) = 'com.google.common.collect.Maps'",
                   "ns","com.google.common.collect.Maps");
  }

  // asFloatArray

  @Test
  public void testAsFloatArray() throws SQLException {
    // Test dump has no float[] objects, so we just check that function could be invoked
    returnsInOrder("select cardinality(asFloatArray(o.this)) m from java.lang.Object o limit 1",
                   "m", "0");
  }

  // asDoubleArray

  @Test
  public void testAsDoubleArray() throws SQLException {
    // Test dump has no double[] objects, so we just check that function could be invoked
    returnsInOrder("select cardinality(asDoubleArray(o.this)) m from java.lang.Object o limit 1",
                   "m", "0");
  }

}
