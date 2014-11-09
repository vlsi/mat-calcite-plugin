package com.github.vlsi.mat.optiq;

import com.github.vlsi.mat.optiq.rex.RexBuilderContext;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import net.hydromatic.optiq.Statistic;
import net.hydromatic.optiq.Statistics;
import net.hydromatic.optiq.TranslatableTable;
import net.hydromatic.optiq.impl.AbstractTable;
import net.hydromatic.optiq.util.BitSets;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
import org.eigenbase.rel.RelNode;
import org.eigenbase.relopt.RelOptTable;
import org.eigenbase.reltype.RelDataType;
import org.eigenbase.reltype.RelDataTypeFactory;
import org.eigenbase.rex.RexNode;
import org.eigenbase.util.Pair;

import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class InstanceByClassTable extends AbstractTable implements TranslatableTable {
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
        IClass clazz = classesList.getFirstClass();

        Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>> typeAndResolvers;
        try {
            typeAndResolvers = ClassRowTypeCache.CACHE.get(typeFactory).get(
                    clazz);
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Unable to identify row type for class " + clazz.getName());
        }
        this.resolvers = typeAndResolvers.right;

        return typeAndResolvers.left;
    }

    @Override
    public Statistic getStatistic() {
        ImmutableList<BitSet> uniqueKeys = ImmutableList.of(BitSets.of(0));
        return Statistics.of(classesList.getTotalObjects(), uniqueKeys);
    }

    @Override
    public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable table) {
        return new InstanceByClassTableScan(context.getCluster(), table, this);
    }
}
