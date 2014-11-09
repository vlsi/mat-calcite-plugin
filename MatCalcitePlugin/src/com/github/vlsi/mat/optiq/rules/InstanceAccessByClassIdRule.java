package com.github.vlsi.mat.optiq.rules;

import com.github.vlsi.mat.optiq.InstanceByClassTable;
import com.github.vlsi.mat.optiq.InstanceByClassTableScan;
import com.github.vlsi.mat.optiq.SnapshotHolder;
import com.github.vlsi.mat.optiq.rex.ExecutionRexBuilderContext;
import com.github.vlsi.mat.optiq.rex.RexBuilderContext;
import com.google.common.base.Function;
import org.eigenbase.rel.TableAccessRel;
import org.eigenbase.relopt.*;
import org.eigenbase.rex.RexInputRef;
import org.eigenbase.rex.RexNode;

import java.util.ArrayList;
import java.util.List;

public class InstanceAccessByClassIdRule extends RelOptRule {
    public static final InstanceAccessByClassIdRule INSTANCE = new InstanceAccessByClassIdRule();

    public InstanceAccessByClassIdRule() {
        super(operand(InstanceByClassTableScan.class, none()), "InstanceAccessByClassIdRule");
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        InstanceByClassTableScan scan = call.rel(0);
        RelOptTable table = scan.getTable();
        RelOptSchema schema = table.getRelOptSchema();
        List<String> indexName = new ArrayList<String>(table.getQualifiedName());
        indexName.set(indexName.size() - 1, "$ids$:" + indexName.get(indexName.size() - 1));
        TableAccessRel ids = new TableAccessRel(scan.getCluster(), schema.getTableForMember(indexName));

        InstanceByClassTable instanceByClassTable = table.unwrap(InstanceByClassTable.class);
        int snapshotId = SnapshotHolder.put(instanceByClassTable.snapshot);

        RelOptCluster cluster = scan.getCluster();
        RexInputRef objectId = cluster.getRexBuilder().makeInputRef(ids, 0);
        RexBuilderContext rexContext = new ExecutionRexBuilderContext(cluster, snapshotId, objectId);

        List<Function<RexBuilderContext, RexNode>> resolvers = instanceByClassTable.getResolvers();
        List<RexNode> exprs = new ArrayList<RexNode>(resolvers.size());
        for (Function<RexBuilderContext, RexNode> resolver : resolvers) {
            exprs.add(resolver.apply(rexContext));
        }
        call.transformTo(RelOptUtil.createProject(ids, exprs, table.getRowType().getFieldNames(), true));
    }
}
