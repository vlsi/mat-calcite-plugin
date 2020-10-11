package com.github.vlsi.mat.calcite.rex;

import com.github.vlsi.mat.calcite.functions.ISnapshotMethods;
import com.github.vlsi.mat.calcite.schema.objects.HeapOperatorTable;

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
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

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

  public RexNode toHeapReference(RexNode node) {
    return getBuilder().makeCall(HeapOperatorTable.TO_HEAP_REFERENCE, node);
  }

  public RexNode getIObject() {
    if (object == null) {
      RelDataTypeFactory typeFactory = getCluster().getTypeFactory();
      RexBuilder b = getBuilder();
      final SqlFunction GET_IOBJECT =
          new SqlUserDefinedFunction(
              new SqlIdentifier("GET_IOBJECT", SqlParserPos.ZERO),
              SqlKind.OTHER_FUNCTION,
              ReturnTypes.explicit(typeFactory.createTypeWithNullability(typeFactory.createJavaType(IObject.class),
                  false)),
              null,
              OperandTypes.operandMetadata(
                  ImmutableList.of(SqlTypeFamily.ANY, SqlTypeFamily.NUMERIC),
                  tf -> ImmutableList.of(
                      typeFactory.createTypeWithNullability(typeFactory.createJavaType(ISnapshot.class), false),
                      typeFactory.createJavaType(int.class)),
                  i -> i == 0 ? "snapshotId" : "objectId",
                  i -> false),
              ScalarFunctionImpl.create(ISnapshotMethods.class, "getIObject"));
      object = b.makeCall(GET_IOBJECT, getSnapshot(), getIObjectId());
    }
    return object;
  }
}
