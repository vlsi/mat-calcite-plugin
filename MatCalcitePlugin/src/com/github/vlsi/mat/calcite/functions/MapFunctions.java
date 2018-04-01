package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.collections.CollectionsActions;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.calcite.adapter.enumerable.CallImplementor;
import org.apache.calcite.adapter.enumerable.NullPolicy;
import org.apache.calcite.adapter.enumerable.ReflectiveCallNotNullImplementor;
import org.apache.calcite.adapter.enumerable.RexImpTable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ImplementableFunction;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.impl.ReflectiveFunctionBase;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.snapshot.model.IObject;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapFunctions extends HeapFunctionsBase {
    public static class MapFunction extends ReflectiveFunctionBase implements ScalarFunction, ImplementableFunction {
        private final CallImplementor implementor;

        public MapFunction(Method method) {
            super(method);
            implementor = RexImpTable.createImplementor(new ReflectiveCallNotNullImplementor(method), NullPolicy.NONE, false);
        }

        @Override
        public CallImplementor getImplementor() {
            return implementor;
        }

        @Override
        public RelDataType getReturnType(RelDataTypeFactory relDataTypeFactory) {
            return relDataTypeFactory.createMapType(relDataTypeFactory.createJavaType(String.class), relDataTypeFactory.createJavaType(HeapReference.class));
        }
    }

    public static Multimap<String, MapFunction> createAll() {
        ImmutableMultimap.Builder<String, MapFunction> builder = ImmutableMultimap.builder();
        builder.put("asMap", new MapFunction(findMethod(MapFunctions.class, "asMap")));
        return builder.build();
    }

    @SuppressWarnings("unused")
    public static Map asMap(Object r) {
        HeapReference ref = ensureHeapReference(r);
        if (ref == null) {
            return null;
        }

        try {
            ExtractedMap extractedMap = CollectionsActions.extractMap(ref.getIObject());
            if (extractedMap == null) {
                return Collections.emptyMap();
            } else {
                Map result = new HashMap();
                for (Map.Entry<IObject, IObject> entry : extractedMap) {
                    result.put(
                            toString(entry.getKey()),
                            resolveReference(entry.getValue())
                    );
                }
                return result;
            }
        } catch (SnapshotException e) {
            throw new RuntimeException("Unable to extract map from " + r, e);
        }
    }
}
