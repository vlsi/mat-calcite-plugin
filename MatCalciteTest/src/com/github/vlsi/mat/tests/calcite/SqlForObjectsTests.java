package com.github.vlsi.mat.tests.calcite;

import com.github.vlsi.mat.calcite.SqlForObjects;

import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.snapshot.model.IClass;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SqlForObjectsTests extends SampleHeapDumpTests {

  private IClass classOf(String name) throws Exception {
    return getSnapshot().getClassesByName(name, false).iterator().next();
  }

  private static IContextObject object(int id) {
    return () -> id;
  }

  /** Number of result rows of the generated SQL (the CSV also has a header line). */
  private int rows(String sql) throws Exception {
    return executeToCSV(sql).size() - 1;
  }

  @Test
  public void selectedObjects() throws Exception {
    int[] ids = classOf("java.lang.String").getObjectIds();
    List<IContextObject> elements = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      elements.add(object(ids[i]));
    }
    String sql = SqlForObjects.build(getSnapshot(), elements);
    Assert.assertTrue(sql, sql.contains("from \"java.lang.String\""));
    Assert.assertEquals(sql, 3, rows(sql));
  }

  private static IContextObjectSet classRow(IClass cls, int[] instances) {
    return new IContextObjectSet() {
      @Override
      public int getObjectId() {
        return cls.getObjectId();
      }

      @Override
      public int[] getObjectIds() {
        return instances;
      }

      @Override
      public String getOQL() {
        return null;
      }
    };
  }

  /** A class row of a partial histogram, e.g. of a retained set, stands for some of the class's instances only. */
  @Test
  public void partialClassRowSelectsItsObjectsOnly() throws Exception {
    IClass stringClass = classOf("java.lang.String");
    int[] some = Arrays.copyOf(stringClass.getObjectIds(), 5);
    String sql = SqlForObjects.build(getSnapshot(), Collections.singletonList(classRow(stringClass, some)));
    Assert.assertTrue(sql, sql.contains("getAddress(this) in ("));
    Assert.assertEquals(sql, 5, rows(sql));
  }

  @Test
  public void classRowSelectsAllInstances() throws Exception {
    IClass threadClass = classOf("java.lang.Thread");
    int[] instances = threadClass.getObjectIds();
    String sql = SqlForObjects.build(getSnapshot(), Collections.singletonList(classRow(threadClass, instances)));
    Assert.assertEquals("select *\n  from \"java.lang.Thread\"\n", sql);
    Assert.assertEquals(sql, threadClass.getNumberOfObjects(), rows(sql));
  }

  @Test
  public void severalClassesGiveOneStatementEach() throws Exception {
    int thread = classOf("java.lang.Thread").getObjectIds()[0];
    int string = classOf("java.lang.String").getObjectIds()[0];
    String sql = SqlForObjects.build(getSnapshot(), Arrays.asList(object(thread), object(string)));
    Assert.assertTrue(sql, sql.startsWith("-- Objects of several classes"));
    String[] statements = sql.substring(sql.indexOf("\n\n") + 2).split("\n(?=select)");
    Assert.assertEquals(sql, 2, statements.length);
    for (String statement : statements) {
      Assert.assertEquals(statement, 1, rows(statement));
    }
  }
}
