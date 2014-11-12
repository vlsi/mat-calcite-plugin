package com.github.vlsi.mat.optiq;

import com.github.vlsi.mat.optiq.rules.InstanceAccessByClassIdRule;
import com.github.vlsi.mat.optiq.rules.PushProjectPastJoinRule;
import org.eigenbase.rel.RelNode;
import org.eigenbase.rel.TableAccessRelBase;
import org.eigenbase.relopt.RelOptCluster;
import org.eigenbase.relopt.RelOptPlanner;
import org.eigenbase.relopt.RelOptTable;
import org.eigenbase.relopt.RelTraitSet;

import java.util.List;

public class InstanceByClassTableScan extends TableAccessRelBase {
    private final InstanceByClassTable instanceByClassTable;

    public InstanceByClassTableScan(RelOptCluster cluster, RelOptTable relOptTable, InstanceByClassTable instanceByClassTable) {
        super(cluster, cluster.traitSetOf(), relOptTable);
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
