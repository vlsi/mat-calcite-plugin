package com.github.vlsi.mat.calcite;

import com.github.vlsi.mat.calcite.rules.InstanceAccessByClassIdRule;
import com.github.vlsi.mat.calcite.rules.PushProjectPastJoinRule;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableScan;

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
        // Workaround until this rule is fixed in Calcite
        planner.addRule(PushProjectPastJoinRule.INSTANCE);
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
