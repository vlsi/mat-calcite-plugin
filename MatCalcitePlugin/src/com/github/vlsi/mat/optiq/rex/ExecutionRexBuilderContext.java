package com.github.vlsi.mat.optiq.rex;

import com.github.vlsi.mat.optiq.SnapshotHolder;
import com.google.common.collect.ImmutableList;
import net.hydromatic.optiq.impl.ScalarFunctionImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eigenbase.relopt.RelOptCluster;
import org.eigenbase.reltype.RelDataTypeFactory;
import org.eigenbase.rex.RexBuilder;
import org.eigenbase.rex.RexNode;
import org.eigenbase.sql.SqlFunction;
import org.eigenbase.sql.SqlIdentifier;
import org.eigenbase.sql.parser.SqlParserPos;
import org.eigenbase.sql.type.OperandTypes;
import org.eigenbase.sql.type.ReturnTypes;
import org.eigenbase.sql.type.SqlTypeName;
import org.eigenbase.sql.validate.SqlUserDefinedFunction;

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
                            ReturnTypes.explicit(typeFactory.createTypeWithNullability(typeFactory.createJavaType(ISnapshot.class), false)),
                            null,
                            OperandTypes.NUMERIC,
                            ImmutableList.of(typeFactory.createJavaType(Integer.class)),
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
