package com.github.vlsi.mat.optiq.functions;

import com.github.vlsi.mat.optiq.HeapReference;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IObject;

public class IObjectMethods {
    public static Object resolveSimpleValue(IObject object, String name) {
        try {
            return object.resolveValue(name);
        } catch (SnapshotException e) {
            throw new IllegalArgumentException("Unable to resolve value " + name + " for object " + object, e);
        }
    }

    public static HeapReference resolveReferenceValue(IObject object, String name) {
        try {
            Object o = object.resolveValue(name);
            return HeapReference.valueOf((IObject) o);
        } catch (SnapshotException e) {
            throw new IllegalArgumentException("Unable to resolve value " + name + " for object " + object, e);
        }
    }
}
