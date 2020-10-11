package com.github.vlsi.mat.calcite.neo;

import com.github.vlsi.mat.calcite.HeapReference;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.ImmutableBitSet;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;

import java.util.ArrayList;
import java.util.List;

public class SnapshotThreadStacksTable extends AbstractTable implements ScannableTable {
  private final ISnapshot snapshot;

  public SnapshotThreadStacksTable(ISnapshot snapshot) {
    this.snapshot = snapshot;
  }

  @Override
  public Statistic getStatistic() {
    int counter = 0;
    for (IThreadStack threadStack : getThreadStacks()) {
      counter += threadStack.getStackFrames().length;
    }
    return Statistics.of(counter, ImmutableList.of(ImmutableBitSet.of(0, 1)));
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    RelDataTypeFactory.Builder builder = typeFactory.builder();
    RelDataType anyType = typeFactory.createSqlType(SqlTypeName.ANY);
    builder.add("thread", anyType);
    builder.add("depth", typeFactory.createJavaType(int.class));
    builder.add("text", typeFactory.createJavaType(String.class));
    builder.add("objects", typeFactory.createMultisetType(anyType, -1));
    return builder.build();
  }

  @Override
  public Enumerable<Object[]> scan(DataContext dataContext) {
    return new AbstractEnumerable<Object[]>() {
      @Override
      public Enumerator<Object[]> enumerator() {
        return new StackFramesEnumerator(snapshot, getThreadStacks());
      }
    };
  }

  private IThreadStack[] getThreadStacks() {
    try {
      List<IThreadStack> threadStacks = new ArrayList<>();
      for (IClass threadClass : snapshot.getClassesByName("java.lang.Thread", true)) {
        if (threadClass.getNumberOfObjects() > 0) {
          for (int threadObject : threadClass.getObjectIds()) {
            IThreadStack threadStack = snapshot.getThreadStack(threadObject);
            if (threadStack != null) {
              threadStacks.add(threadStack);
            }
          }
        }
      }
      return threadStacks.toArray(new IThreadStack[threadStacks.size()]);
    } catch (SnapshotException e) {
      throw new RuntimeException(e);
    }
  }

  private static class StackFramesEnumerator extends GroupEnumerator<IThreadStack, IStackFrame[], Object[]> {
    private final ISnapshot snapshot;

    public StackFramesEnumerator(ISnapshot snapshot, IThreadStack[] groups) {
      super(groups);
      this.snapshot = snapshot;
    }

    @Override
    protected IStackFrame[] resolveGroup(IThreadStack group) throws Exception {
      return group.getStackFrames();
    }

    @Override
    protected int rowsCount(IStackFrame[] rows) {
      return rows.length;
    }

    @Override
    protected Object[] resolveRow(IThreadStack group, IStackFrame[] rows, int currentRow) throws Exception {
      Object[] result = new Object[4];
      IStackFrame frame = rows[currentRow];
      result[0] = HeapReference.valueOf(snapshot.getObject(group.getThreadId()));
      result[1] = currentRow;
      result[2] = frame.getText();
      List<HeapReference> objects = new ArrayList<>();
      for (int objectId : frame.getLocalObjectsIds()) {
        objects.add(HeapReference.valueOf(snapshot.getObject(objectId)));
      }
      result[3] = objects;
      return result;
    }
  }
}
