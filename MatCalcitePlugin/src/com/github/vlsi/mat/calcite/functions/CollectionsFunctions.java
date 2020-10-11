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
import org.eclipse.mat.snapshot.model.IObject;

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

  public static Multimap<String, ScalarFunction> createAll() {
    ImmutableMultimap.Builder<String, ScalarFunction> builder = ImmutableMultimap.builder();
    builder.put("asMap", new MapFunction(findMethod(CollectionsFunctions.class, "asMap")));
    builder.put("asMultiSet", new MultiSetFunction(findMethod(CollectionsFunctions.class, "asMultiSet")));
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

  @SuppressWarnings("unused")
  public static List asMultiSet(Object r) {
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
}
