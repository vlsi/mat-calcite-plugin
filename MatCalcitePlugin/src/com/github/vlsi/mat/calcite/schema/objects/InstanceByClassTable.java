package com.github.vlsi.mat.calcite.schema.objects;

import com.github.vlsi.mat.calcite.rex.RexBuilderContext;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Pair;
import org.eclipse.mat.snapshot.ISnapshot;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class InstanceByClassTable extends AbstractTable implements TranslatableTable
{
    public final ISnapshot snapshot;
    public final IClassesList classesList;
    private List<Function<RexBuilderContext, RexNode>> resolvers;

    public InstanceByClassTable(IClassesList classesList) {
        this.classesList = classesList;
        this.snapshot = classesList.snapshot;
    }

    public List<Function<RexBuilderContext, RexNode>> getResolvers() {
        return resolvers;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>> typeAndResolvers;
        try {
            typeAndResolvers = ClassRowTypeCache.CACHE.get(typeFactory).get(
                    classesList);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Unable to identify row type for class " + classesList);
        }
        this.resolvers = typeAndResolvers.right;

        return typeAndResolvers.left;
    }

    @Override
    public Statistic getStatistic() {
        List<ImmutableBitSet> uniqueKeys = ImmutableList.of(ImmutableBitSet.of(0));
        return Statistics.of(classesList.getTotalObjects(), uniqueKeys);
    }

    @Override
    public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable table) {
        return new InstanceByClassTableScan(context.getCluster(), table, this);
    }
}
