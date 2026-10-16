package com.github.vlsi.mat.calcite.schema.objects;

import com.github.vlsi.mat.calcite.QueryCancellation;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.util.ImmutableBitSet;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;

import java.util.Collections;
import java.util.List;

public class InstanceIdsByClassTable extends AbstractQueryableTable {
  private final ISnapshot snapshot;
  private final IClassesList classesList;

  public InstanceIdsByClassTable(IClassesList classesList) {
    super(Object[].class);
    this.classesList = classesList;
    this.snapshot = classesList.snapshot;
  }

  @Override
  public Schema.TableType getJdbcTableType() {
    return Schema.TableType.SYSTEM_TABLE;
  }

  @Override
  public Queryable<Integer> asQueryable(QueryProvider queryProvider, SchemaPlus schemaPlus, String tableName) {
    return new AbstractTableQueryable<Integer>(queryProvider, schemaPlus, this, tableName) {
      @Override
      public Enumerator<Integer> enumerator() {
        FluentIterable<Integer> it = FluentIterable
            .from(classesList.getClasses())
            .transformAndConcat(
                input -> {
                  try {
                    return Ints.asList(input.getObjectIds());
                  } catch (SnapshotException e) {
                    e.printStackTrace();
                    return Collections.emptyList();
                  }
                });

        return new CancellableEnumerator<>(Linq4j.iterableEnumerator(it));
      }
    };
  }

  @Override
  public Statistic getStatistic() {
    List<ImmutableBitSet> uniqueKeys = ImmutableList.of(ImmutableBitSet.of(0));
    return Statistics.of(classesList.getTotalObjects(), uniqueKeys);
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    return typeFactory.createStructType(
        Collections.singletonList(typeFactory.createJavaType(int.class)),
        Collections.singletonList("@ID")
    );
  }

  /**
   * Stops the scan when its query is cancelled ({@link QueryCancellation}), e.g. from MAT's progress view. Calcite
   * checks its own cancel flag only between result rows, so without this an aggregation over a large class could not
   * be stopped until it had read every object.
   */
  static final class CancellableEnumerator<T> implements Enumerator<T> {
    private final Enumerator<T> delegate;

    CancellableEnumerator(Enumerator<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T current() {
      return delegate.current();
    }

    @Override
    public boolean moveNext() {
      QueryCancellation.check();
      return delegate.moveNext();
    }

    @Override
    public void reset() {
      delegate.reset();
    }

    @Override
    public void close() {
      delegate.close();
    }
  }
}
