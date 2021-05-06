package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.collections.CollectionsActions;
import com.github.vlsi.mat.calcite.schema.objects.SpecialFields;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.*;

import java.util.Map;

public class HeapFunctions extends HeapFunctionsBase {

  @SuppressWarnings("unused")
  public static int getId(Object r) {
    HeapReference ref = ensureHeapReference(r);
    return ref == null ? -1 : ref.getIObject().getObjectId();
  }

  @SuppressWarnings("unused")
  public static Object getClass(Object r) {
    HeapReference ref = ensureHeapReference(r);
    return ref == null ? null : HeapReference.valueOf(ref.getIObject().getClazz());
  }

  @SuppressWarnings("unused")
  public static String getType(Object r) {
    HeapReference ref = ensureHeapReference(r);
    return ref == null ? "" : ref.getIObject().getClazz().getName();
  }

  @SuppressWarnings("unused")
  public static String toString(Object r) {
    if (r == null) {
      return null;
    }
    return r.toString();
  }

  @SuppressWarnings("unused")
  public static String getStringContent(Object r, int limit) {
    try {
      HeapReference ref = ensureHeapReference(r);
      return ref == null ? "" : PrettyPrinter.objectAsString(ref.getIObject(), limit);
    } catch (SnapshotException e) {
      throw new RuntimeException("Unable to represent as string", e);
    }
  }

  @SuppressWarnings("unused")
  public static String introspect(Object r) {
    if (r instanceof HeapReference) {
      return "HeapReference: " + toString(r);
    } else if (r instanceof Object[]) {
      return "Array, length = " + ((Object[]) r).length;
    } else {
      return "Primitive type: " + toString(r);
    }
  }

  @SuppressWarnings("unused")
  public static Object getByKey(Object r, String key) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return null;
    }

    try {
      for (Map.Entry<IObject, IObject> entry : CollectionsActions.extractMap(ref.getIObject())) {
        if (key.equals(toString(entry.getKey()))) {
          return resolveReference(entry.getValue());
        }
      }
      return null;
    } catch (SnapshotException e) {
      throw new RuntimeException("Unable to lookup key " + key + " in " + r, e);
    }
  }

  @SuppressWarnings("unused")
  public static int getSize(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return -1;
    }

    try {
      ICollectionExtractor collectionExtractor = CollectionExtractionUtils.findCollectionExtractor(ref.getIObject());
      if (collectionExtractor != null && collectionExtractor.hasSize()) {
        return collectionExtractor.getSize(ref.getIObject());
      } else {
        return -1;
      }
    } catch (SnapshotException e) {
      throw new RuntimeException("Unable to obtain collection size for " + r, e);
    }
  }

  @SuppressWarnings("unused")
  public static int length(Object r) {
    HeapReference ref = ensureHeapReference(r);

    if (ref == null) {
      return -1;
    }

    IObject obj = ref.getIObject();

    return obj instanceof IArray ? ((IArray) obj).getLength() : -1;
  }

  @SuppressWarnings("unused")
  public static long shallowSize(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return -1;
    }
    try {
      return ref.getIObject().getSnapshot().getHeapSize(ref.getIObject().getObjectId());
    } catch (SnapshotException e) {
      throw new RuntimeException("Cannot calculate shallow size for " + r, e);
    }
  }

  @SuppressWarnings("unused")
  public static long retainedSize(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return -1;
    }

    try {
      return ref.getIObject().getSnapshot().getRetainedHeapSize(ref.getIObject().getObjectId());
    } catch (SnapshotException e) {
      throw new RuntimeException("Cannot calculate retained size for " + r, e);
    }
  }

  @SuppressWarnings("unused")
  public static Object getField(Object r, String fieldName) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return null;
    }
    IObject iObject = ref.getIObject();
    if (fieldName.charAt(0) == '@') {
      if ("@shallow".equalsIgnoreCase(fieldName)) {
        return HeapFunctions.shallowSize(ref);
      }
      if ("@retained".equalsIgnoreCase(fieldName)) {
        return HeapFunctions.retainedSize(ref);
      }
      if (SpecialFields.CLASS_NAME.equalsIgnoreCase(fieldName)) {
        return IClassMethods.getClassName(iObject.getClazz());
      }
      if (iObject instanceof IClass) {
        if (SpecialFields.CLASS_LOADER.equalsIgnoreCase(fieldName)) {
          return resolveReference(IClassMethods.getClassLoader(iObject));
        } else if (SpecialFields.SUPER.equalsIgnoreCase(fieldName)) {
          return resolveReference(IClassMethods.getSuper(iObject));
        }
      } else if (SpecialFields.CLASS.equalsIgnoreCase(fieldName)) {
        return resolveReference(iObject.getClazz());
      }
    } else if (iObject instanceof IArray
               && fieldName.length() > 2
               && fieldName.charAt(0) == '['
               && fieldName.charAt(fieldName.length() - 1) == ']') {
      try {
        // Field name is '[<number>]' and target object is array.
        // Initially such calls were routed to IObject.resolveValue, which accepts field names as '[<index>]' for arrays.
        // However, this have two problems:
        // 1. This doesn't work with primitive arrays (they always return null)
        // 2. This doesn't correctly work with <null> values - MAT code doesn't handle 0 address properly in this case
        // So, now we handle this case directly in our code
        int index = Integer.parseInt(fieldName.substring(1, fieldName.length() - 1));
        int length = ((IArray) iObject).getLength();
        if (index >= 0 && index < length) {
          if (iObject instanceof IPrimitiveArray) {
            return ((IPrimitiveArray) iObject).getValueAt(index);
          } else if (iObject instanceof IObjectArray) {
            return resolveReference(iObject.getSnapshot(), ((IObjectArray) iObject).getReferenceArray()[index]);
          }
        } else {
          return null;
        }
      } catch (NumberFormatException e) {
        // fall down
      }
    }
    return resolveReference(IObjectMethods.resolveSimpleValue(iObject, fieldName));
  }

  @SuppressWarnings("unused")
  public static Object getStaticField(Object r, String name) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return null;
    }

    IObject iObject = ref.getIObject();
    if (iObject instanceof IClass) {
      IClass iClass = (IClass) iObject;
      for (Field field : iClass.getStaticFields()) {
        if (field.getName().equals(name)) {
          Object value = field.getValue();
          if (value instanceof IObject) {
            return new HeapReference((IObject) value);
          } else {
            return value;
          }
        }
      }
    }
    return null;
  }

  @SuppressWarnings("unused")
  public static long getAddress(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return -1;
    }

    return ref.getIObject().getObjectAddress();
  }

  @SuppressWarnings("unused")
  public static long toLong(String value) {
    return Long.decode(value);
  }

  @SuppressWarnings("unused")
  public static Object getDominator(Object r) {
    HeapReference ref = ensureHeapReference(r);
    if (ref == null) {
      return null;
    }

    try {
      ISnapshot snapshot = ref.getIObject().getSnapshot();
      return HeapReference.valueOf(snapshot.getObject(snapshot.getImmediateDominatorId(ref.getIObject().getObjectId())));
    } catch (SnapshotException e) {
      throw new RuntimeException("Cannot obtain immediate dominator object for " + r, e);
    }
  }

}
