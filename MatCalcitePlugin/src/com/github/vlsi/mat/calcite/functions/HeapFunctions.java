package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.*;

import java.util.Map;

public class HeapFunctions {

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
        if (r == null) return null;
        return r.toString();
    }

    public static String introspect(Object r) {
        if (r instanceof HeapReference) {
            return "HeapReference: "+toString(r);
        } else if (r instanceof Object[]) {
            return "Array, length = "+((Object[])r).length;
        } else
        {
            return "Primitive type: "+toString(r);
        }
    }

    @SuppressWarnings("unused")
    public static Object getByKey(Object r, String key) {
        HeapReference ref = ensureHeapReference(r);
        if (ref == null) {
            return null;
        }

        try {
            for (Map.Entry<IObject, IObject> entry : CollectionExtractionUtils.extractMap(ref.getIObject())) {
                if (key.equals(toString(entry.getKey()))) {
                    return entry.getValue();
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

        Object value = IObjectMethods.resolveSimpleValue(ref.getIObject(), fieldName);
        if (value instanceof IObject) {
            return HeapReference.valueOf((IObject)value);
        } else {
            return value;
        }
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
            throw new RuntimeException("Cannot obtain immediate dominator object for "+r, e);
        }

    }

    private static HeapReference ensureHeapReference(Object r) {
        return (r == null || !(r instanceof HeapReference)) ?
                null :
                (HeapReference) r;
    }

    private static String toString(IObject o) {
        String classSpecific = o.getClassSpecificName();
        if (classSpecific != null)
            return classSpecific;
        return o.getDisplayName();
    }
}
