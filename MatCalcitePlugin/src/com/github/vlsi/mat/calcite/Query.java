package com.github.vlsi.mat.calcite;

import org.eclipse.mat.query.IQuery;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.annotations.Category;
import org.eclipse.mat.query.annotations.CommandName;
import org.eclipse.mat.query.annotations.Name;
import org.eclipse.mat.query.results.TextResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.util.IProgressListener;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;

@Name("SQL via Calcite")
@Category("SQL")
@CommandName("calcite")
public class Query implements IQuery {
  public static final Pattern EXPLAIN_PLAN = Pattern.compile("explain\\s+plan\\s+for", Pattern.CASE_INSENSITIVE);
  public static final Pattern TRIM_ENUMERABLE = Pattern.compile("Enumerable(\\w+)Rel");
  // EnumerableTableScan(table=[[HEAP, java, net, $ids$:URL]])
  public static final Pattern RENAME_INDEX = Pattern.compile("EnumerableTableScan\\(table=\\[\\[HEAP, ((?:[^\\$][^," +
      "\\]]*+, )*+)\\$ids\\$:([^]]+)\\]\\]\\)");
  public static final Pattern RENAME_JOIN = Pattern.compile("EnumerableJoin");
  public static final Pattern RENAME_CALC = Pattern.compile("EnumerableCalc");

  @Argument
  public ISnapshot snapshot;

  @Argument(flag = Argument.UNFLAGGED)
  public String sql;

  @Override
  public IResult execute(IProgressListener listener) throws Exception {
    Connection con = null;
    Statement st = null;
    ResultSet rs = null;

    try {
      con = CalciteDataSource.getConnection(snapshot);

      st = con.createStatement();
      rs = st.executeQuery(sql);

      RowSetFactory rowSetFactory = RowSetProvider.newFactory();
      CachedRowSet rowSet = rowSetFactory.createCachedRowSet();
      rowSet.populate(rs);

      ResultSetMetaData md = rowSet.getMetaData();
      if (md.getColumnCount() == 1 && "PLAN".equals(md.getColumnName(1))
          && rowSet.size() == 1
          && EXPLAIN_PLAN.matcher(sql).find()) {
        rowSet.absolute(1);
        String plan = rowSet.getString(1);
        System.out.println("plan = " + plan);
        {
          Matcher ind = RENAME_INDEX.matcher(plan);
          StringBuffer sb = new StringBuffer();
          while (ind.find()) {
            String className = ind.group(1).replace(", ", ".") + ind.group(2);
            ind.appendReplacement(sb, "");
            sb.append("GetObjectIdsByClass (class=").append(className).append(")");
          }
          ind.appendTail(sb);
          plan = sb.toString();
        }
        plan = RENAME_JOIN.matcher(plan).replaceAll("HashJoin ");
        plan = RENAME_CALC.matcher(plan).replaceAll("View ");
        plan = TRIM_ENUMERABLE.matcher(plan).replaceAll("$1 ");
        return new TextResult(plan, false);
      }

      return new RowSetTable(rowSet);
    } finally {
      CalciteDataSource.close(rs, st, con);
    }
  }

}
