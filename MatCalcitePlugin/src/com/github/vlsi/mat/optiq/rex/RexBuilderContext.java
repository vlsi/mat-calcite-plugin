package com.github.vlsi.mat.optiq.rex;

import com.github.vlsi.mat.optiq.functions.ISnapshotMethods;
import com.google.common.collect.ImmutableList;
import net.hydromatic.optiq.impl.ScalarFunctionImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eigenbase.relopt.RelOptCluster;
import org.eigenbase.reltype.RelDataTypeFactory;
import org.eigenbase.rex.RexBuilder;
import org.eigenbase.rex.RexNode;
import org.eigenbase.sql.SqlFunction;
import org.eigenbase.sql.SqlIdentifier;
import org.eigenbase.sql.parser.SqlParserPos;
import org.eigenbase.sql.type.OperandTypes;
import org.eigenbase.sql.type.ReturnTypes;
import org.eigenbase.sql.validate.SqlUserDefinedFunction;

public abstract class RexBuilderContext {
    private final RelOptCluster cluster;
    private RexNode object;

    public RexBuilderContext(RelOptCluster cluster) {
        this.cluster = cluster;
    }

    public RexBuilder getBuilder() {
        return cluster.getRexBuilder();
    }

    public RelOptCluster getCluster() {
        return cluster;
    }

    public abstract RexNode getSnapshot();

    public abstract RexNode getIObjectId();

    public RexNode getIObject() {
        if (object == null) {
            RelDataTypeFactory typeFactory = getCluster().getTypeFactory();
            RexBuilder b = getBuilder();
            final SqlFunction GET_IOBJECT =
                    new SqlUserDefinedFunction(
                            new SqlIdentifier("GET_IOBJECT", SqlParserPos.ZERO),
                            ReturnTypes.explicit(typeFactory.createTypeWithNullability(typeFactory.createJavaType(IObject.class), false)),
                            null,
                            OperandTypes.ANY_ANY,
                            ImmutableList.of(typeFactory.createTypeWithNullability(typeFactory.createJavaType(ISnapshot.class), false),
                                    typeFactory.createJavaType(int.class)),
                            ScalarFunctionImpl.create(ISnapshotMethods.class, "getIObject"));
            object = b.makeCall(GET_IOBJECT, getSnapshot(), getIObjectId());
        }
        return object;
    }
}
