package com.github.vlsi.mat.calcite.schema.objects;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.functions.IClassMethods;
import com.github.vlsi.mat.calcite.functions.IObjectMethods;
import com.github.vlsi.mat.calcite.functions.ISnapshotMethods;

import com.google.common.collect.ImmutableList;
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
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public interface HeapOperatorTable {
  // IObject
  SqlFunction TO_HEAP_REFERENCE = new SqlUserDefinedFunction(
      new SqlIdentifier("TO_HEAP_REFERENCE", SqlParserPos.ZERO),
      SqlKind.OTHER_FUNCTION,
      ReturnTypes.explicit(tf ->
          tf.createTypeWithNullability(tf.createJavaType(HeapReference.class), true)),
      null,
      OperandTypes.operandMetadata(
          ImmutableList.of(SqlTypeFamily.ANY),
          tf -> ImmutableList.of(
              tf.createTypeWithNullability(tf.createJavaType(Object.class), true)),
          i -> "iobject",
          i -> false),
      ScalarFunctionImpl.create(IObjectMethods.class, "toHeapReference"));

  SqlFunction RESOLVE_VALUE = new SqlUserDefinedFunction(
      new SqlIdentifier("RESOLVE_VALUE", SqlParserPos.ZERO),
      SqlKind.OTHER_FUNCTION,
      ReturnTypes.explicit(typeFactory -> typeFactory.createJavaType(Object.class)),
      null,
      OperandTypes.operandMetadata(
          ImmutableList.of(SqlTypeFamily.ANY, SqlTypeFamily.CHARACTER),
          tf -> ImmutableList.of(
              tf.createTypeWithNullability(tf.createJavaType(IObject.class), false),
              tf.createTypeWithNullability(tf.createJavaType(String.class), false)),
          i -> i == 0 ? "iobject" : "fieldName",
          i -> false),
      ScalarFunctionImpl.create(IObjectMethods.class, "resolveSimpleValue"));

  SqlFunction GET_CLASS_OF = new SqlUserDefinedFunction(
      new SqlIdentifier("GET_CLASS_OF", SqlParserPos.ZERO),
      SqlKind.OTHER_FUNCTION,
      ReturnTypes.explicit(tf ->
              tf.createTypeWithNullability(tf.createJavaType(IClass.class), true)),
      null,
      OperandTypes.operandMetadata(
          ImmutableList.of(SqlTypeFamily.ANY),
          tf -> ImmutableList.of(
              tf.createTypeWithNullability(tf.createJavaType(ISnapshot.class), false),
              tf.createTypeWithNullability(tf.createJavaType(int.class), false)),
          i -> i == 0 ? "snapshot" : "id",
          i -> false),
      ScalarFunctionImpl.create(ISnapshotMethods.class, "getClassOf"));

  // IClass
  SqlFunction GET_SUPER = new SqlUserDefinedFunction(
      new SqlIdentifier("GET_SUPER", SqlParserPos.ZERO),
      SqlKind.OTHER_FUNCTION,
      ReturnTypes.explicit(tf ->
          tf.createTypeWithNullability(tf.createJavaType(IClass.class), true)),
      null,
      OperandTypes.operandMetadata(
          ImmutableList.of(SqlTypeFamily.ANY),
          tf -> ImmutableList.of(
              tf.createTypeWithNullability(tf.createJavaType(IObject.class), true)),
          i -> "iclass",
          i -> false),
      ScalarFunctionImpl.create(IClassMethods.class, "getSuper"));

  SqlFunction GET_CLASS_LOADER = new SqlUserDefinedFunction(
      new SqlIdentifier("GET_CLASS_LOADER", SqlParserPos.ZERO),
      SqlKind.OTHER_FUNCTION,
      ReturnTypes.explicit(tf ->
          tf.createTypeWithNullability(tf.createJavaType(IObject.class), true)),
      null,
      OperandTypes.operandMetadata(
          ImmutableList.of(SqlTypeFamily.ANY),
          tf -> ImmutableList.of(
              tf.createTypeWithNullability(tf.createJavaType(IObject.class), false)),
          i -> "iclass",
          i -> false),
      ScalarFunctionImpl.create(IClassMethods.class, "getClassLoader"));

  SqlFunction GET_CLASS_NAME = new SqlUserDefinedFunction(
      new SqlIdentifier("GET_CLASS_NAME", SqlParserPos.ZERO),
      SqlKind.OTHER_FUNCTION,
      ReturnTypes.explicit(tf ->
          tf.createTypeWithNullability(tf.createJavaType(String.class), true)),
      null,
      OperandTypes.operandMetadata(
          ImmutableList.of(SqlTypeFamily.ANY),
          tf -> ImmutableList.of(
              tf.createTypeWithNullability(tf.createJavaType(IObject.class), true)),
          i -> "iclass",
          i -> false),
      ScalarFunctionImpl.create(IClassMethods.class, "getClassName"));
}
