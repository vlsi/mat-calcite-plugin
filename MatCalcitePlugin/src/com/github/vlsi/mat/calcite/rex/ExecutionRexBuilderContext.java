package com.github.vlsi.mat.calcite.rex;

import com.github.vlsi.mat.calcite.SnapshotHolder;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class ExecutionRexBuilderContext extends RexBuilderContext {
    private final int snapshotId;
    private final RexNode objectId;

    private RexNode snapshot;

    public ExecutionRexBuilderContext(RelOptCluster cluster, int snapshotId, RexNode objectId) {
        super(cluster);
        this.snapshotId = snapshotId;
        this.objectId = objectId;
    }

    @Override
    public RexNode getSnapshot() {
        if (snapshot == null) {
            RelDataTypeFactory typeFactory = getCluster().getTypeFactory();
            RexBuilder b = getBuilder();
            final SqlFunction UDF =
                    new SqlUserDefinedFunction(
                            new SqlIdentifier("GET_SNAPSHOT", SqlParserPos.ZERO),
                            SqlKind.OTHER_FUNCTION,
                            ReturnTypes.explicit(typeFactory.createTypeWithNullability(typeFactory.createJavaType(ISnapshot.class), false)),
                            null,
                            OperandTypes.operandMetadata(
                                    ImmutableList.of(SqlTypeFamily.NUMERIC),
                                    tf -> ImmutableList.of(
                                            tf.createJavaType(Integer.class)),
                                    i -> "snapshotId",
                                    i -> false),
                            ScalarFunctionImpl.create(SnapshotHolder.class, "get"));
            snapshot = b.makeCall(UDF, b.makeLiteral(snapshotId, typeFactory.createSqlType(SqlTypeName.INTEGER), false));
        }
        return snapshot;
    }

    @Override
    public RexNode getIObjectId() {
        return objectId;
    }
}
