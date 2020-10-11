package com.github.vlsi.mat.calcite.schema.objects;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.*;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Calc;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.rules.CalcMergeRule;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rel.rules.ProjectJoinTransposeRule;

import java.util.List;

public class InstanceByClassTableScan extends TableScan
{
    private final InstanceByClassTable instanceByClassTable;

    public InstanceByClassTableScan(RelOptCluster cluster, RelOptTable relOptTable, InstanceByClassTable instanceByClassTable) {
        super(cluster, cluster.traitSet(), ImmutableList.of(), relOptTable);
        this.instanceByClassTable = instanceByClassTable;
    }

    @Override
    public void register(RelOptPlanner planner) {
        planner.addRule(InstanceAccessByClassIdRule.INSTANCE);
        planner.addRule(CoreRules.PROJECT_JOIN_TRANSPOSE);
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
