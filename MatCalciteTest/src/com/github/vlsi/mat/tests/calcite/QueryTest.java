package com.github.vlsi.mat.tests.calcite;

import com.github.vlsi.mat.calcite.CalciteDataSource;
import com.google.common.base.Joiner;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotFactory;
import org.eclipse.mat.util.VoidProgressListener;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QueryTest {
    static ISnapshot snapshot;

    /**
     * Transforms both \r\n and \r to \n
     */
    private static String nnl(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static Object[] nnl(Object[] array) {
        for (int i = 0; i<array.length; i++) {
            if (array[i] instanceof String) {
                array[i] = nnl((String)array[i]);
            }
        }
        return array;
    }


    @BeforeClass
    public static void openSnapshot() throws SnapshotException {
        File file = new File("dumps", "mvn1m_jdk18.hprof");
        System.out.println("exists = " + file.exists() + ", file = " + file.getAbsolutePath());
        snapshot = SnapshotFactory.openSnapshot(file, new VoidProgressListener());
    }

    @AfterClass
    public static void closeSnapshot() {
        SnapshotFactory.dispose(snapshot);
        snapshot = null;
    }

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
         execute("select * from java.util.\"HashMap$Node\"",1);
    }

    @Test
    public void testQuotedNestedClassName() throws SQLException {
        execute("select * from \"java.util.HashMap$Node\"",1);
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
        execute("select * from \"instanceof.java.util.HashMap$Node\"",1);
    }

    @Test
    public void testStackTraceCount() throws SQLException {
        returnsInOrder("select count(*) CNT from heap.ThreadStackFrames",
                new String[]{"CNT", "39"});
    }

    @Test
    public void testStackTraces() throws SQLException {
        returnsInOrder("select thread, depth, text from heap.ThreadStackFrames order by 1, 2, 3 limit 2",
                new String[]{"thread|depth|text",
                        "Finalizer|0|at java.lang.Object.wait(J)V (Native Method)",
                        "Finalizer|1|at java.lang.ref.ReferenceQueue.remove(J)Ljava/lang/ref/Reference; (ReferenceQueue.java:142)"});
    }

    @Test
    public void testStackTracesUnnest() throws SQLException {
        returnsInOrder("select thread, depth, text, obj.va from heap.ThreadStackFrames frm, unnest(frm.objects) obj(va) order by 1, 2, 3 limit 2",
                new String[]{"thread|depth|text|va",
                        "Finalizer|1|at java.lang.ref.ReferenceQueue.remove(J)Ljava/lang/ref/Reference; (ReferenceQueue.java:142)|java.lang.ref.ReferenceQueue @ 0x7bfe391c0",
                        "Finalizer|1|at java.lang.ref.ReferenceQueue.remove(J)Ljava/lang/ref/Reference; (ReferenceQueue.java:142)|java.lang.ref.ReferenceQueue$Lock @ 0x7bfe391b0"});
    }

    @Test
    public void countStrings() throws SQLException {
        returnsInOrder("select count(*) CNT from java.lang.String",
                new String[]{"CNT", "3256"});
    }

    @Test
    public void arrayCase() throws SQLException {
        execute("select toString(l.this) from java.lang.\"Object[]\" l", 1);
    }

//    @Test
    public void joinOptimization() throws SQLException {
        // Unfortunately, this is not yet optimized to snapshot.getObject(get_id(u.path))
        returnsInOrder("explain plan for select u.this, retainedSize(s.this) from \"java.lang.String\" s join \"java.net.URL\" u on (s.this = u.path)",
                new String[]{"PLAN", "EnumerableCalc(expr#0..2=[{inputs}], expr#3=[retainedSize($t2)], this=[$t0], EXPR$1=[$t3])\n"
                        + "  EnumerableJoin(condition=[=($1, $2)], joinType=[inner])\n"
                        + "    EnumerableCalc(expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2, $t0)], expr#4=[TO_REFERENCE($t3)], expr#5=['path'], expr#6=[RESOLVE_REFERENCE($t3, $t5)], this=[$t4], path=[$t6])\n"
                        + "      EnumerableTableScan(table=[[HEAP, $ids$:java.net.URL]])\n"
                        + "    EnumerableCalc(expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2, $t0)], expr#4=[TO_REFERENCE($t3)], this=[$t4])\n"
                        + "      EnumerableTableScan(table=[[HEAP, $ids$:java.lang.String]])\n"});
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
        returnsInOrder("select sum(hm.size) sum_hm_size from java.util.HashMap hm", new String[]{"sum_hm_size", "922"});
    }

    @Test
    public void selectShallow() throws SQLException {
        returnsInOrder("select sum(cast(hm.this['@shallow'] as bigint)) sum_shallow from java.util.HashMap hm", new String[]{"sum_shallow", "3840"});
    }

    @Test
    public void selectThisMapField() throws SQLException {
        returnsInOrder("select count(this['table'][0]) count_first_entry from java.util.HashMap", new String[]{"count_first_entry", "71"});
    }

    @Test
    public void selectThisMapFieldMatSyntax() throws SQLException {
        returnsInOrder("select count(this['table.[0]']) count_first_entry from java.util.HashMap", new String[]{"count_first_entry", "71"});
    }

    @Test
    public void selectShallowSizeFunction() throws SQLException {
        returnsInOrder("select sum(shallowSize(this)) shallow_size from java.util.HashMap",
                new String[]{"shallow_size", "3840"});
    }

    @Test
    public void selectRetainedSizeFunction() throws SQLException {
        returnsInOrder("select sum(retainedSize(this)) retained_size from java.util.HashMap",
                new String[]{"retained_size", "114976"});
    }

    @Test
    public void selectOutboundReferences() throws SQLException {
        returnsInOrder("select sum(retainedSize(og.this)) retained_size from java.util.HashMap hm, lateral (select * from table(getOutboundReferences(hm.this))) as og(this)",
                new String[]{"retained_size", "113712"});
    }

    @Test
    public void testReadme() throws SQLException {
        execute("explain plan for select toString(file) file, count(*) cnt, sum(retainedSize(this)) sum_retained, sum(shallowSize(this)) sum_shallow\n"
                + "  from java.net.URL\n"
                + " group by toString(file)\n"
                + "having count(*)>1\n"
                + " order by sum(shallowSize(this)) desc", 5);

        execute("select toString(file) file, count(*) cnt, sum(retainedSize(this)) sum_retained, sum(shallowSize(this)) sum_shallow\n"
                + "  from java.net.URL\n"
                + " group by toString(file)\n"
                + "having count(*)>1\n"
                + " order by sum(retainedSize(this)) desc", 5);
    }

    private void returnsInOrder(String sql, Object[] expected) throws SQLException {
        Object[] actuals = executeToCSV(sql).toArray();
        System.out.println("Arrays.toString(expected) = " + Arrays.toString(expected));
        System.out.println("Arrays.toString(actuals) = " + Arrays.toString(actuals));
        Assert.assertArrayEquals(sql, nnl(expected), nnl(actuals));
    }

    private List<String> executeToCSV(String sql) throws SQLException {
        List<String> res = new ArrayList<String>();
        System.out.println("sql = " + sql);
        Connection con = CalciteDataSource.getConnection(snapshot);
        PreparedStatement ps = con.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        ResultSetMetaData md = rs.getMetaData();
        Joiner joiner = Joiner.on('|');
        List<String> row = new ArrayList<String>();
        final int columnCount = md.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            row.add(md.getColumnName(i));
        }
        res.add(joiner.join(row));
        while(rs.next()) {
            row.clear();
            for (int i = 1; i <= columnCount; i++) {
                row.add(String.valueOf(rs.getObject(i)));
            }
            res.add(joiner.join(row));
        }
        return res;
    }

    private void execute(String sql, int limit) throws SQLException {
        System.out.println("sql = " + sql);
        Connection con = CalciteDataSource.getConnection(snapshot);
        PreparedStatement ps = con.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        ResultSetMetaData md = rs.getMetaData();
        for (int j = 0; rs.next() && j < limit; j++) {
            for (int i = 1; i <= md.getColumnCount(); i++) {
                System.out.println(md.getColumnName(i) + ": " + String.valueOf(rs.getObject(i)));
            }
            System.out.println();
        }
        CalciteDataSource.close(rs, ps, con);
    }
}
