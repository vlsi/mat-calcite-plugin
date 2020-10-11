package com.github.vlsi.mat.calcite.functions;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.schema.objects.SpecialFields;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

@SuppressWarnings("unused")
public class IObjectMethods {
    public static Object resolveSimpleValue(IObject object, String name) {
        try {
            if (object instanceof IClass) {
                IClass clazz = (IClass) object;
                if ("name".equalsIgnoreCase(name)) {
                    return IClassMethods.getClassName(object);
                }
            }
            return object.resolveValue(name);
        } catch (SnapshotException e) {
            throw new IllegalArgumentException("Unable to resolve value " + name + " for object " + object, e);
        }
    }

    public static HeapReference toHeapReference(Object object) {
        return HeapReference.valueOf((IObject) object);
    }
}
