package com.github.vlsi.mat.calcite.schema.objects;

import org.apache.calcite.plan.*;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rules.ProjectJoinTransposeRule;

import java.util.List;

public class InstanceByClassTableScan extends TableScan
{
    private final InstanceByClassTable instanceByClassTable;

    public InstanceByClassTableScan(RelOptCluster cluster, RelOptTable relOptTable, InstanceByClassTable instanceByClassTable) {
        super(cluster, cluster.traitSet(), relOptTable);
        this.instanceByClassTable = instanceByClassTable;
    }

    @Override
    public void register(RelOptPlanner planner) {
        planner.addRule(InstanceAccessByClassIdRule.INSTANCE);
        planner.addRule(ProjectJoinTransposeRule.INSTANCE);
        // Does not yet work.
        // These rules should convert join (a."@ID" = :var) to "snapshot.getObject(:var)"
//        planner.addRule(NestedLoopsJoinRule.INSTANCE);
//        planner.addRule(InstanceAccessByIdRule.INSTANCE);
    }

    @Override
    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
        assert inputs.isEmpty();
        return new InstanceByClassTableScan(getCluster(), table, instanceByClassTable);
    }
}
