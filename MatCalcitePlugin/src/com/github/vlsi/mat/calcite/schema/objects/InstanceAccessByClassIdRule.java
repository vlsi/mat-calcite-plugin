package com.github.vlsi.mat.calcite.schema.objects;

import com.github.vlsi.mat.calcite.SnapshotHolder;
import com.github.vlsi.mat.calcite.rex.ExecutionRexBuilderContext;
import com.github.vlsi.mat.calcite.rex.RexBuilderContext;
import com.github.vlsi.mat.calcite.rules.DefaultRuleConfig;

import org.apache.calcite.plan.*;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class InstanceAccessByClassIdRule extends RelRule<RelRule.Config> {
  public static final InstanceAccessByClassIdRule INSTANCE =
      new InstanceAccessByClassIdRule(
          DefaultRuleConfig.EMPTY
              .withOperandSupplier(
                  b0 ->
                      b0.operand(InstanceByClassTableScan.class)
                          .anyInputs()
              )
      );

  public InstanceAccessByClassIdRule(RelRule.Config config) {
    super(config);
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    InstanceByClassTableScan scan = call.rel(0);
    RelOptTable table = scan.getTable();
    RelOptSchema schema = table.getRelOptSchema();
    List<String> indexName = new ArrayList<>(table.getQualifiedName());
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
    List<RexNode> exprs = new ArrayList<>(resolvers.size());
    for (Function<RexBuilderContext, RexNode> resolver : resolvers) {
      exprs.add(resolver.apply(rexContext));
    }
    call.transformTo(
        relBuilder.projectNamed(exprs, table.getRowType().getFieldNames(), false)
            .build());
  }
}
