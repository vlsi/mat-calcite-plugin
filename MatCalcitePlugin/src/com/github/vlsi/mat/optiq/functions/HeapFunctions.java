package com.github.vlsi.mat.optiq.functions;

import com.github.vlsi.mat.optiq.HeapReference;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.*;

public class HeapFunctions {
    public static int get_id(Object r) {
        HeapReference ref = ensureHeapReference(r);
        return ref == null ? -1 : ref.getIObject().getObjectId();
    }

    public static String get_type(Object r) {
        HeapReference ref = ensureHeapReference(r);
        return ref == null ? "" : ref.getIObject().getClazz().getName();
    }

    public static String toString(Object r) {
        if (r == null) return null;
        return r.toString();
    }

    public static Object get_by_key(Object r, String key) {
        HeapReference ref = ensureHeapReference(r);
        if (ref == null) {
            return null;
        }
        IObject iObject = ref.getIObject();
        ISnapshot snapshot = iObject.getSnapshot();
        String className = iObject.getClazz().getName();

        if (!"java.util.HashMap".equals(className))
        {
            throw new RuntimeException("Unsupported map type: "+className);
        }

        try
        {
            IObjectArray table = (IObjectArray)iObject.resolveValue("table");
            long[] referenceArray = table.getReferenceArray();
            for (long entryAddress : referenceArray)
            {
                if (entryAddress != 0)
                {
                    int entryId = snapshot.mapAddressToId(entryAddress);
                    IObject entry = snapshot.getObject(entryId);
                    while (entry != null)
                    {
                        IObject keyObject = (IObject) entry.resolveValue("key");
                        if (key.equals(toString(keyObject)))
                        {
                            IObject valueObject = (IObject) entry.resolveValue(("value"));
                            return HeapReference.valueOf(valueObject);
                        }

                        entry = (IObject)entry.resolveValue("next");
                    }
                }
            }
        } catch (SnapshotException e)
        {
            throw new RuntimeException("Unable to lookup key " + key + " in " + r, e);
        }

        return null;
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
