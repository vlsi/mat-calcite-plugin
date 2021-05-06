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
import org.eclipse.mat.inspections.collectionextract.ExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionsFunctions extends HeapFunctionsBase {

  public abstract static class BaseImplementableFunction extends ReflectiveFunctionBase implements ScalarFunction,
                                                                                                   ImplementableFunction {
    final CallImplementor implementor;

    BaseImplementableFunction(Method method) {
      super(method);
      implementor = RexImpTable.createImplementor(new ReflectiveCallNotNullImplementor(method), NullPolicy.NONE, false);
    }

    @Override
    public CallImplementor getImplementor() {
      return implementor;
    }
  }

  public static class MapFunction extends BaseImplementableFunction {
    MapFunction(Method method) {
      super(method);
    }

    @Override
    public RelDataType getReturnType(RelDataTypeFactory relDataTypeFactory) {
      return relDataTypeFactory.createMapType(relDataTypeFactory.createJavaType(String.class),
                                              relDataTypeFactory.createJavaType(HeapReference.class));
    }
  }

  public static class MultiSetFunction extends BaseImplementableFunction {
    MultiSetFunction(Method method) {
      super(method);
    }

    @Override
    public RelDataType getReturnType(RelDataTypeFactory relDataTypeFactory) {
      return relDataTypeFactory.createMultisetType(relDataTypeFactory.createJavaType(HeapReference.class), -1);
    }
  }

  public static class ArrayFunction extends BaseImplementableFunction {
    private final Class<?> elementType;

    ArrayFunction(Method method, Class<?> elementType) {
      super(method);
      this.elementType = elementType;
    }

    @Override
    public RelDataType getReturnType(RelDataTypeFactory relDataTypeFactory) {
      return relDataTypeFactory.createArrayType(relDataTypeFactory.createJavaType(elementType), -1);
    }
  }

  public static Multimap<String, ScalarFunction> createAll() {
    ImmutableMultimap.Builder<String, ScalarFunction> builder = ImmutableMultimap.builder();
    builder.put("asMap", new MapFunction(findMethod(CollectionsFunctions.class, "asMap")));
    builder.put("asMultiSet", new MultiSetFunction(findMethod(CollectionsFunctions.class, "asMultiSet")));
    builder.put("asArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), HeapReference.class));
    builder.put("asByteArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Byte.class));
    builder.put("asShortArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Short.class));
    builder.put("asIntArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Integer.class));
    builder.put("asLongArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Long.class));
    builder.put("asBooleanArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Boolean.class));
    builder.put("asCharArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Character.class));
    builder.put("asFloatArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Float.class));
    builder.put("asDoubleArray", new ArrayFunction(findMethod(CollectionsFunctions.class, "asArray"), Double.class));
    return builder.build();
  }

  @SuppressWarnings("unused")
  public static Map<String, Object> asMap(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return null;
    }

    try {
      ExtractedMap extractedMap = CollectionsActions.extractMap(ref.getIObject());
      if (extractedMap == null) {
        return Collections.emptyMap();
      } else {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<IObject, IObject> entry : extractedMap) {
          result.put(toString(entry.getKey()),
                     resolveReference(entry.getValue())
                    );
        }
        return result;
      }
    } catch (SnapshotException e) {
      throw new RuntimeException("Unable to extract map from " + r, e);
    }
  }

  @SuppressWarnings("unused")
  public static List<HeapReference> asMultiSet(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return null;
    }

    try {
      ExtractedCollection extractedCollection = CollectionExtractionUtils.extractList(ref.getIObject());
      if (extractedCollection == null) {
        return Collections.emptyList();
      } else {
        List<HeapReference> result = new ArrayList<>();
        for (IObject entry : extractedCollection) {
          result.add((HeapReference) resolveReference(entry));
        }
        return result;
      }
    } catch (SnapshotException e) {
      throw new RuntimeException("Unable to extract collection from " + r, e);
    }
  }

  @SuppressWarnings("unused")
  public static List<?> asArray(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return null;
    }

    IObject iObject = ref.getIObject();
    if (!(iObject instanceof IArray)) {
      return null;
    }

    if (iObject instanceof IPrimitiveArray) {
      IPrimitiveArray arrayObject = (IPrimitiveArray) iObject;
      int length = arrayObject.getLength();
      List<Object> result = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        result.add(arrayObject.getValueAt(i));
      }
      return result;
    } else {
      IObjectArray arrayObject = (IObjectArray) iObject;
      ISnapshot snapshot = arrayObject.getSnapshot();
      int length = arrayObject.getLength();
      List<HeapReference> result = new ArrayList<>(length);
      for (long objectAddress : arrayObject.getReferenceArray()) {
        result.add(resolveReference(snapshot, objectAddress));
      }
      return result;
    }
  }
}
