package com.github.vlsi.mat.calcite.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DexxHashMapCollectionExtractor implements IMapExtractor {

  @Override
  public boolean hasSize() {
    return true;
  }

  @Override
  public Integer getSize(IObject iObject) throws SnapshotException {
    IObject chmObj = getCHM(iObject);

    if (chmObj != null) {
      String typeName = getClassName(chmObj);
      if ("com.github.andrewoma.dexx.collection.internal.hashmap.HashMap1".equals(typeName)) {
        return 1;
      } else if ("com.github.andrewoma.dexx.collection.internal.hashmap.CompactHashMap".equals(typeName)) {
        return 0;
      } else if ("com.github.andrewoma.dexx.collection.internal.hashmap.HashMapCollision1".equals(typeName)) {
        int count = 0;
        IObject item = (IObject) chmObj.resolveValue("kvs");
        while (getClassName(item).equals("com.github.andrewoma.dexx.collection.internal.hashmap.ListMap$Node")) {
          count++;
          item = (IObject) chmObj.resolveValue("this$0");
        }
        return count;
      } else if ("com.github.andrewoma.dexx.collection.internal.hashmap.HashTrieMap".equals(typeName)) {
        return (Integer) chmObj.resolveValue("size");
      }
    }
    return null;
  }

  @Override
  public boolean hasExtractableContents() {
    return true;
  }

  @Override
  public Iterator<Map.Entry<IObject, IObject>> extractMapEntries(IObject iObject) throws SnapshotException {
    List<Map.Entry<IObject, IObject>> result = new ArrayList<>();
    IObject chmObj = getCHM(iObject);
    if (chmObj != null) {
      extractPairs(chmObj, result);
    }
    return result.iterator();
  }

  // Internal

  private String getClassName(IObject obj) {
    return obj.getClazz().getName();
  }

  private IObject getCHM(IObject iObject) throws SnapshotException {
    Object obj = iObject.resolveValue("compactHashMap");
    return obj instanceof IObject ? (IObject) obj : null;
  }

  private void extractPairs(IObject chmObj, List<Map.Entry<IObject, IObject>> result) throws SnapshotException {
    String typeName = getClassName(chmObj);
    if ("com.github.andrewoma.dexx.collection.Pair".equals(typeName)) {
      // Single entry
      IObject key = (IObject) chmObj.resolveValue("component1");
      IObject value = (IObject) chmObj.resolveValue("component2");
      result.add(new IObjectsPair(key, value));
    } else if ("com.github.andrewoma.dexx.collection.internal.hashmap.HashMap1".equals(typeName)) {
      // Single entry
      extractPairs((IObject) chmObj.resolveValue("value"), result);
    } else if ("com.github.andrewoma.dexx.collection.internal.hashmap.HashMapCollision1".equals(typeName)) {
      // Multiple entries with hash collision - chain of nested Node classes in 'kvs' field
      extractPairs((IObject) chmObj.resolveValue("kvs"), result);
    } else if ("com.github.andrewoma.dexx.collection.internal.hashmap.ListMap$Node".equals(typeName)) {
      // Get current value
      extractPairs((IObject) chmObj.resolveValue("value"), result);
      // Try to get next object in the list
      extractPairs((IObject) chmObj.resolveValue("this$0"), result);
    } else if ("com.github.andrewoma.dexx.collection.internal.hashmap.HashTrieMap".equals(typeName)) {
      // Multiple entries
      Object obj = chmObj.resolveValue("elems");
      if (obj instanceof IObjectArray) {
        ISnapshot snapshot = chmObj.getSnapshot();
        for (long elem : ((IObjectArray) obj).getReferenceArray()) {
          if (elem != 0) {
            extractPairs(snapshot.getObject(snapshot.mapAddressToId(elem)), result);
          }
        }
      }
    }
  }

  // Not implemented

  @Override
  public boolean hasCollisionRatio() {
    return false;
  }

  @Override
  public Double getCollisionRatio(IObject iObject) throws SnapshotException {
    return null;
  }

  @Override
  public boolean hasCapacity() {
    return false;
  }

  @Override
  public Integer getCapacity(IObject iObject) throws SnapshotException {
    return null;
  }

  @Override
  public boolean hasFillRatio() {
    return false;
  }

  @Override
  public Double getFillRatio(IObject iObject) throws SnapshotException {
    return null;
  }

  @Override
  public int[] extractEntryIds(IObject iObject) throws SnapshotException {
    // TODO: return values?
    return new int[0];
  }

  @Override
  public boolean hasExtractableArray() {
    return false;
  }

  @Override
  public IObjectArray extractEntries(IObject iObject) throws SnapshotException {
    return null;
  }

  @Override
  public Integer getNumberOfNotNullElements(IObject iObject) throws SnapshotException {
    return null;
  }
}
