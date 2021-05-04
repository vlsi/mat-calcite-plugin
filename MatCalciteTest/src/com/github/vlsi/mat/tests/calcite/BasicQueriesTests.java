package com.github.vlsi.mat.tests.calcite;

import org.junit.Test;

import java.sql.SQLException;

public class BasicQueriesTests extends SampleHeapDumpTests {

  @Test
  public void testRegularClassName() throws SQLException {
    execute("select * from java.util.HashMap", 1);
  }

  @Test
  public void testQuotedClassName() throws SQLException {
    execute("select * from \"java.util.HashMap\"", 1);
  }

  @Test
  public void testRegularArrayName() throws SQLException {
    execute("select * from java.lang.\"Object[]\"", 1);
  }

  @Test
  public void testQuotedArrayName() throws SQLException {
    execute("select * from \"java.lang.Object[]\"", 1);
  }

  @Test
  public void testRegularNestedClassName() throws SQLException {
    execute("select * from java.util.\"HashMap$Node\"", 1);
  }

  @Test
  public void testQuotedNestedClassName() throws SQLException {
    execute("select * from \"java.util.HashMap$Node\"", 1);
  }

  @Test
  public void testInstanceOfName() throws SQLException {
    execute("select * from instanceof.java.util.HashMap", 1);
  }

  @Test
  public void testInstanceOfNestedClassName() throws SQLException {
    execute("select * from instanceof.java.util.\"HashMap$Node\"", 1);
  }

  @Test
  public void testInstanceOfQuotedClassName() throws SQLException {
    execute("select * from \"instanceof.java.util.HashMap$Node\"", 1);
  }

  @Test
  public void testStackTraceCount() throws SQLException {
    returnsInOrder("select count(*) CNT from native.ThreadStackFrames",
        "CNT",
        "39");
  }

  @Test
  public void testStackTraces() throws SQLException {
    returnsInOrder("select thread, depth, text from native.ThreadStackFrames order by 1, 2, 3 limit 2",
        "thread|depth|text",
        "Finalizer|0|at java.lang.Object.wait(J)V (Native Method)",
        "Finalizer|1|at java.lang.ref.ReferenceQueue.remove(J)Ljava/lang/ref/Reference; (ReferenceQueue.java:142)");
  }

  @Test
  public void testStackTracesUnnest() throws SQLException {
    returnsInOrder("select thread, depth, text, obj.va from native.ThreadStackFrames frm, unnest(frm.objects) obj(va)" +
            " order by 1, 2, 3 limit 2",
        "thread|depth|text|va",
        "Finalizer|1|at java.lang.ref.ReferenceQueue.remove(J)Ljava/lang/ref/Reference; (ReferenceQueue.java:142)" +
            "|java.lang.ref.ReferenceQueue @ 0x7bfe391c0",
        "Finalizer|1|at java.lang.ref.ReferenceQueue.remove(J)Ljava/lang/ref/Reference; (ReferenceQueue.java:142)" +
            "|java.lang.ref.ReferenceQueue$Lock @ 0x7bfe391b0");
  }

  @Test
  public void countStrings() throws SQLException {
    returnsInOrder("select count(*) CNT from java.lang.String",
        "CNT",
        "3256");
  }

  @Test
  public void arrayCase() throws SQLException {
    execute("select toString(l.this) from java.lang.\"Object[]\" l", 1);
  }

  //    @Test
  public void joinOptimization() throws SQLException {
    // Unfortunately, this is not yet optimized to snapshot.getObject(get_id(u.path))
    returnsInOrder("explain plan for select u.this, retainedSize(s.this) from \"java.lang.String\" s join \"java.net" +
            ".URL\" u on (s.this = u.path)",
        "PLAN",
        "EnumerableCalc(expr#0..2=[{inputs}], expr#3=[retainedSize($t2)], this=[$t0], EXPR$1=[$t3])\n"
            + "  EnumerableJoin(condition=[=($1, $2)], joinType=[inner])\n"
            + "    EnumerableCalc(expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2," +
            " $t0)], expr#4=[TO_REFERENCE($t3)], expr#5=['path'], expr#6=[RESOLVE_REFERENCE($t3, $t5)], this=[$t4], " +
            "path=[$t6])\n"
            + "      EnumerableTableScan(table=[[HEAP, $ids$:java.net.URL]])\n"
            + "    EnumerableCalc(expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2," +
            " $t0)], expr#4=[TO_REFERENCE($t3)], this=[$t4])\n"
            + "      EnumerableTableScan(table=[[HEAP, $ids$:java.lang.String]])\n");
  }

  @Test
  public void selectAllColumns() throws SQLException {
    execute("select * from java.util.HashMap", 2);
  }

  @Test
  public void selectThisColumn() throws SQLException {
    execute("select this from java.util.HashMap", 2);
  }

  @Test
  public void selectThisDotField() throws SQLException {
    returnsInOrder("select sum(hm.size) sum_hm_size from java.util.HashMap hm",
        "sum_hm_size",
        "922");
  }

  @Test
  public void selectClass() throws SQLException {
    returnsInOrder("select name, \"@super\", \"@classLoader\" from java.lang.Class order by toString(name) limit 10",
        "name|@super|@classLoader",
        "boolean[]|class java.lang.Object @ 0x7bfe3fde8|<system class loader>",
        "byte[]|class java.lang.Object @ 0x7bfe3fde8|<system class loader>",
        "byte[][]|class java.lang.Object @ 0x7bfe3fde8|<system class loader>",
        "char[]|class java.lang.Object @ 0x7bfe3fde8|<system class loader>",
        "com.google.common.base.Function|class java.lang.Object @ 0x7bfe3fde8|org.codehaus.plexus.classworlds.realm" +
            ".ClassRealm @ 0x7bfe97510",
        "com.google.common.base.Joiner|class java.lang.Object @ 0x7bfe3fde8|org.codehaus.plexus.classworlds.realm" +
            ".ClassRealm @ 0x7bfe97510",
        "com.google.common.base.Joiner$1|class com.google.common.base.Joiner @ 0x7bfeec1e8|org.codehaus.plexus" +
            ".classworlds.realm.ClassRealm @ 0x7bfe97510",
        "com.google.common.base.Joiner$2|class com.google.common.base.Joiner @ 0x7bfeec1e8|org.codehaus.plexus" +
            ".classworlds.realm.ClassRealm @ 0x7bfe97510",
        "com.google.common.base.Preconditions|class java.lang.Object @ 0x7bfe3fde8|org.codehaus.plexus.classworlds" +
            ".realm.ClassRealm @ 0x7bfe97510",
        "com.google.common.collect.AbstractIndexedListIterator|class com.google.common.collect" +
            ".UnmodifiableListIterator @ 0x7bfee9e10|org.codehaus.plexus.classworlds.realm.ClassRealm @ 0x7bfe97510");
  }

  @Test
  public void selectClassFields() throws SQLException {
    returnsInOrder("select this['@className'] className" +
            ", this['@class']['@className'] class_className" +
            ", this['@class']['name'] class_name" +
            ", this['@class']['@super'] class_super" +
            ", this['@class']['@classLoader'] class_classLoader" +
            " from java.util.HashMap order by this limit 1",
        "className|class_className|class_name|class_super|class_classLoader",
        "java.util.HashMap|java.lang.Class|java.util.HashMap|class java.util.AbstractMap @ 0x7bfe6ed10|<system class " +
            "loader>");
  }

  @Test
  public void groupByClassLoader() throws SQLException {
    returnsInOrder("select \"@classLoader\", count(*) classes" +
            " from java.lang.Class group by \"@classLoader\" order by 2 desc",
        "@classLoader|classes",
        "<system class loader>|748",
        "org.codehaus.plexus.classworlds.realm.ClassRealm @ 0x7bfe97510|217",
        "sun.misc.Launcher$AppClassLoader @ 0x7bfe391f0|16",
        "null|9",
        "sun.misc.Launcher$ExtClassLoader @ 0x7bfe394f0|4");
  }

  @Test
  public void joinClassClass() throws SQLException {
    returnsInOrder("select count(a.name) ca, count(b.name) cb\n" +
            "  from java.lang.Class a\n" +
            "  left join java.lang.Class b\n" +
            "  on (a.this = b.this)",
        "ca|cb",
        "985|985");
  }

  @Test
  public void joinClassClassLoader() throws SQLException {
    returnsInOrder("select count(c.name) c, count(cl.this) cl\n" +
            "  from java.lang.Class c\n" +
            "  left join instanceof.java.lang.ClassLoader cl\n" +
            "  on (c.\"@classLoader\" = cl.this)",
        "c|cl",
        "985|985");
  }

  @Test
  public void countClass() throws SQLException {
    returnsInOrder("select count(this['@super']) a, count(\"@super\") b, count(enumConstantDirectory) c from java.lang.Class",
        "a|b|c",
        "984|984|0");
  }

  @Test
  public void selectShallow() throws SQLException {
    returnsInOrder("select sum(cast(hm.this['@shallow'] as bigint)) sum_shallow from java.util.HashMap hm",
        "sum_shallow",
        "3840");
  }

  @Test
  public void selectThisMapField() throws SQLException {
    returnsInOrder("select count(this['table'][0]) count_first_entry from java.util.HashMap",
        "count_first_entry",
        "18");
  }

  @Test
  public void selectThisMapFieldMatSyntax() throws SQLException {
    returnsInOrder("select count(this['table.[0]']) count_first_entry from java.util.HashMap",
        "count_first_entry",
        "71");
    // This is actually incorrect, as IObject.resolveValue handle null values in array as reference to first Object on
    // heap (i.e. System ClassLoader). The previous test (@selectThisMapField) handles null values correctly (as we
    // do it directly in our code), so the correct result should be 18, but as we testing Mat syntax here (handled by
    // Mat itself), so we just accept its behavior.
  }

  @Test
  public void selectShallowSizeFunction() throws SQLException {
    returnsInOrder("select sum(shallowSize(this)) shallow_size from java.util.HashMap",
        "shallow_size",
        "3840");
  }

  @Test
  public void selectRetainedSizeFunction() throws SQLException {
    returnsInOrder("select sum(retainedSize(this)) retained_size from java.util.HashMap",
        "retained_size",
        "114976");
  }

  @Test
  public void testGetField() throws SQLException {
    returnsInOrder("select getField(lm.this, 'initializationDone') a, getField(lm.rootLogger, 'levelValue') b, getField(lm.this, 'listenerMap')['size'] c from java.util.logging.LogManager lm",
                   "a|b|c",
                   "true|800|0");
  }

  @Test
  public void testGetStaticField() throws SQLException {
    returnsInOrder("select c.name, getStaticField(c.this, 'serialVersionUID') serialVersionUID from java.lang.Class c where c.name = 'java.util.HashMap'",
                   "name|serialVersionUID",
                   "java.util.HashMap|362498820763181265");
  }

  @Test
  public void testReadme() throws SQLException {
    execute("explain plan for select toString(file) file_str, count(*) cnt, sum(retainedSize(this)) sum_retained, sum" +
        "(shallowSize(this)) sum_shallow\n"
        + "  from java.net.URL\n"
        + " group by toString(file)\n"
        + "having count(*)>1\n"
        + " order by sum(shallowSize(this)) desc", 5);

    execute("select toString(file) file_str, count(*) cnt, sum(retainedSize(this)) sum_retained, sum(shallowSize" +
        "(this)) sum_shallow\n"
        + "  from java.net.URL\n"
        + " group by toString(file)\n"
        + "having count(*)>1\n"
        + " order by sum(retainedSize(this)) desc", 5);

    execute("explain plan for select u.this, retainedSize(s.this)\n" +
        "   from java.lang.String s\n" +
        "   join java.net.URL u\n" +
        "     on s.this = u.path", 10);

    execute("select u.this, retainedSize(s.this)\n" +
        "   from java.lang.String s\n" +
        "   join java.net.URL u\n" +
        "     on s.this = u.path", 10);
  }
}
