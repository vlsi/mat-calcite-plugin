package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.SnapshotHolder;
import org.apache.calcite.adapter.enumerable.*;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.schema.FunctionParameter;
import org.apache.calcite.schema.ImplementableFunction;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.impl.ReflectiveFunctionBase;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public class ReferenceFunction implements ScalarFunction, ImplementableFunction {

    private ISnapshot snapshot;

    public ReferenceFunction(ISnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public CallImplementor getImplementor() {
        return RexImpTable.createImplementor(new NotNullImplementor() {
            @Override
            public Expression implement(RexToLixTranslator rexToLixTranslator, RexCall rexCall, List<Expression> operands) {
                int snapshotId = SnapshotHolder.put(snapshot);

                return Expressions.call(
                        Expressions.new_(getConstructor(SnapshotFunctions.class, Integer.TYPE), Expressions.constant(snapshotId, Integer.TYPE)),
                        getMethod(SnapshotFunctions.class, "getReference", String.class), operands);
            }
        }, NullPolicy.NONE, false);
    }

    @Override
    public RelDataType getReturnType(RelDataTypeFactory typeFactory) {
        return typeFactory.createJavaType(HeapReference.class);
    }

    @Override
    public List<FunctionParameter> getParameters() {
        return ReflectiveFunctionBase.builder().add(String.class, "address").build();
    }

    private Method getMethod(Class<?> cls, String name, Class ... argumentClasses) {
        try {
            return cls.getMethod(name, argumentClasses);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Constructor<T> getConstructor(Class<T> cls, Class ... argumentClasses) {
        try {
            return cls.getConstructor(argumentClasses);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static class SnapshotFunctions {
        private ISnapshot snapshot;

        public SnapshotFunctions(int snapshotId) {
            snapshot = SnapshotHolder.get(snapshotId);
        }

        @SuppressWarnings("unused")
        public HeapReference getReference(String address) {
            try {
                return HeapReference.valueOf(snapshot.getObject(snapshot.mapAddressToId(Long.decode(address))));
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot get object by address '" + address + "'", e);
            }
        }
    }
}
