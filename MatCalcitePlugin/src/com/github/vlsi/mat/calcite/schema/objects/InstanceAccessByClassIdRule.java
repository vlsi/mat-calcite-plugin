package com.github.vlsi.mat.calcite.schema.objects;

import com.github.vlsi.mat.calcite.SnapshotHolder;
import com.github.vlsi.mat.calcite.rex.ExecutionRexBuilderContext;
import com.github.vlsi.mat.calcite.rex.RexBuilderContext;
import com.google.common.base.Function;
import org.apache.calcite.plan.*;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;

import java.util.ArrayList;
import java.util.List;

public class InstanceAccessByClassIdRule extends RelRule<InstanceAccessByClassIdRule.Config> {
    public static final InstanceAccessByClassIdRule INSTANCE = Config.DEFAULT.toRule();

    public interface Config extends RelRule.Config {
        InstanceAccessByClassIdRule.Config DEFAULT = EMPTY
                .withOperandSupplier(b0 ->
                        b0.operand(InstanceByClassTableScan.class)
                                .anyInputs())
                .as(Config.class);

        @Override
        default InstanceAccessByClassIdRule toRule() {
            return new InstanceAccessByClassIdRule(this);
        }
    }

    public InstanceAccessByClassIdRule(InstanceAccessByClassIdRule.Config config) {
        super(config);
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        InstanceByClassTableScan scan = call.rel(0);
        RelOptTable table = scan.getTable();
        RelOptSchema schema = table.getRelOptSchema();
        List<String> indexName = new ArrayList<String>(table.getQualifiedName());
        indexName.set(indexName.size() - 1, "$ids$:" + indexName.get(indexName.size() - 1));
        RelBuilder relBuilder = call.builder();
        relBuilder.push(
                relBuilder.getScanFactory().createScan(
                        ViewExpanders.simpleContext(relBuilder.getCluster()),
                        schema.getTableForMember(indexName)));

        InstanceByClassTable instanceByClassTable = table.unwrap(InstanceByClassTable.class);
        int snapshotId = SnapshotHolder.put(instanceByClassTable.snapshot);

        RexBuilderContext rexContext = new ExecutionRexBuilderContext(
                scan.getCluster(), snapshotId, relBuilder.field(0));

        List<Function<RexBuilderContext, RexNode>> resolvers = instanceByClassTable.getResolvers();
        List<RexNode> exprs = new ArrayList<RexNode>(resolvers.size());
        for (Function<RexBuilderContext, RexNode> resolver : resolvers) {
            exprs.add(resolver.apply(rexContext));
        }
        call.transformTo(
                relBuilder.projectNamed(exprs, table.getRowType().getFieldNames(), false)
                        .build());
    }
}
