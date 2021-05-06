package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

import java.lang.reflect.Method;

public class HeapFunctionsBase {
  protected static Object resolveReference(Object value) {
    return value instanceof IObject ? HeapReference.valueOf((IObject) value) : value;
  }

  protected static HeapReference resolveReference(ISnapshot snapshot, long address) {
    if (address == 0) {
      // Eclipse MAT always returns "SystemClassLoader" for address=0, so we return null instead
      return null;
    }
    try {
      return HeapReference.valueOf(snapshot.getObject(snapshot.mapAddressToId(address)));
    } catch (SnapshotException e) {
      return null;
    }
  }

  protected static HeapReference ensureHeapReference(Object r) {
    return r instanceof HeapReference ? (HeapReference) r : null;
  }

  protected static String toString(IObject o) {
    String classSpecific = o.getClassSpecificName();
    if (classSpecific != null) {
      return classSpecific;
    }
    return o.getDisplayName();
  }

  protected static Method findMethod(Class<?> cls, String name) {
    for (Method m : cls.getMethods()) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    return null;
  }
}
