package com.github.vlsi.mat.calcite.schema.references;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.ImmutableBitSet;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.NamedReference;

import com.github.vlsi.mat.calcite.HeapReference;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

public class OutboundReferencesTable extends AbstractQueryableTable {
    private final static List<ImmutableBitSet> NON_UNIQUE_KEYS_STATISTICS = ImmutableList.of(ImmutableBitSet.of());

    private final List<NamedReference> references;

    public OutboundReferencesTable(List<NamedReference> references) {
        super(Object[].class);
        this.references = references;
    }

    @Override
    public Queryable<Object[]> asQueryable(QueryProvider queryProvider, SchemaPlus schemaPlus, String tableName) {
        return new AbstractTableQueryable<Object[]>(queryProvider, schemaPlus, this, tableName) {
            @Override
            public Enumerator<Object[]> enumerator() {
                FluentIterable<Object[]> it = FluentIterable
                        .from(references)
                        .transform(new Function<NamedReference, Object[]>() {
                            @Nullable
                            @Override
                            public Object[] apply(@Nullable NamedReference namedReference) {
                                HeapReference ref = null;
                                try {
                                    ref = HeapReference.valueOf(namedReference.getObject());
                                } catch (SnapshotException e) {
                                    e.printStackTrace();
                                }
                                return new Object[]{namedReference.getName(), ref};
                            }
                        });

                return Linq4j.iterableEnumerator(it);
            }
        };
    }

    @Override
    public Statistic getStatistic() {
        return Statistics.of(references.size(), NON_UNIQUE_KEYS_STATISTICS);
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        return typeFactory.builder()
                .add("name", typeFactory.createJavaType(String.class))
                .add("this", typeFactory.createSqlType(SqlTypeName.ANY))
                .build();
    }
}
