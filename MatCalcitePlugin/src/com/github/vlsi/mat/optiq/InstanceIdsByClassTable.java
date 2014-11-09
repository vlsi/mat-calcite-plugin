package com.github.vlsi.mat.optiq;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import net.hydromatic.linq4j.Enumerator;
import net.hydromatic.linq4j.Linq4j;
import net.hydromatic.linq4j.QueryProvider;
import net.hydromatic.linq4j.Queryable;
import net.hydromatic.optiq.Schema;
import net.hydromatic.optiq.SchemaPlus;
import net.hydromatic.optiq.Statistic;
import net.hydromatic.optiq.Statistics;
import net.hydromatic.optiq.impl.AbstractTableQueryable;
import net.hydromatic.optiq.impl.java.AbstractQueryableTable;
import net.hydromatic.optiq.util.BitSets;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eigenbase.reltype.RelDataType;
import org.eigenbase.reltype.RelDataTypeFactory;

import java.util.BitSet;
import java.util.Collections;

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
                                new Function<IClass, Iterable<Integer>>() {
                                    @Override
                                    public Iterable<Integer> apply(IClass input) {
                                        try {
                                            return Ints.asList(input
                                                    .getObjectIds());
                                        } catch (SnapshotException e) {
                                            e.printStackTrace();
                                            return Collections.emptyList();
                                        }
                                    }
                                });

                return Linq4j.iterableEnumerator(it);
            }
        };
    }

    @Override
    public Statistic getStatistic() {
        ImmutableList<BitSet> uniqueKeys = ImmutableList.of(BitSets.of(0));
        return Statistics.of(classesList.getTotalObjects(), uniqueKeys);
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        return typeFactory.createStructType(
                Collections.singletonList(typeFactory.createJavaType(int.class)),
                Collections.singletonList("@ID")
        );
    }
}
