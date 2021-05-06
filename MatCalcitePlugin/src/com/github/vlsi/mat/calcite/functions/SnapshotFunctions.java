package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.SnapshotHolder;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.calcite.adapter.enumerable.CallImplementor;
import org.apache.calcite.adapter.enumerable.NotNullImplementor;
import org.apache.calcite.adapter.enumerable.NullPolicy;
import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.FunctionParameter;
import org.apache.calcite.schema.ImplementableFunction;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.impl.ReflectiveFunctionBase;
import org.eclipse.mat.snapshot.ISnapshot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public class SnapshotFunctions {
  private final ISnapshot snapshot;

  public SnapshotFunctions(int snapshotId) {
    snapshot = SnapshotHolder.get(snapshotId);
  }

  @SuppressWarnings("unused")
  public HeapReference getReference(String address) {
    return HeapFunctionsBase.resolveReference(snapshot, Long.decode(address));
  }

  public static Multimap<String, Function> createAll(ISnapshot snapshot) {
    ImmutableMultimap.Builder<String, Function> builder = ImmutableMultimap.builder();
    builder.put("getReference", getFunction(snapshot, "getReference", String.class));
    return builder.build();
  }

  private static Function getFunction(ISnapshot snapshot, String name, Class<?> ... argumentClasses) {
    return new SnapshotFunction(snapshot, getMethod(SnapshotFunctions.class, name, argumentClasses));
  }

  private static Method getMethod(Class<?> cls, String name, Class<?> ... argumentClasses) {
    try {
      return cls.getMethod(name, argumentClasses);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static <T> Constructor<T> getConstructor(Class<T> cls, Class<?> ... argumentClasses) {
    try {
      return cls.getConstructor(argumentClasses);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static class SnapshotFunction implements ScalarFunction, ImplementableFunction, NotNullImplementor {
    private final ISnapshot snapshot;
    private final Method functionMethod;

    public SnapshotFunction(ISnapshot snapshot, Method functionMethod) {
      this.snapshot = snapshot;
      this.functionMethod = functionMethod;
    }

    @Override
    public CallImplementor getImplementor() {
      return RexImpTable.createImplementor(this, NullPolicy.NONE, false);
    }

    @Override
    public Expression implement(RexToLixTranslator rexToLixTranslator, RexCall rexCall, List<Expression> operands) {
      int snapshotId = SnapshotHolder.put(snapshot);

      return Expressions.call(
          Expressions.new_(getConstructor(SnapshotFunctions.class, Integer.TYPE), Expressions.constant(snapshotId,
              Integer.TYPE)),
          functionMethod, operands);
    }

    @Override
    public RelDataType getReturnType(RelDataTypeFactory typeFactory) {
      return typeFactory.createJavaType(functionMethod.getReturnType());
    }

    @Override
    public List<FunctionParameter> getParameters() {
      return ReflectiveFunctionBase.builder().addMethodParameters(functionMethod).build();
    }
  }
}
