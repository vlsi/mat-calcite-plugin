package com.github.vlsi.mat.optiq;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class HeapReference {
    private final ISnapshot snapshot;
    private final IObject o;

    public HeapReference(ISnapshot snapshot, IObject o) {
        this.snapshot = snapshot;
        this.o = o;
    }

    public IObject getIObject() {
        return o;
    }

    @Override
    public String toString() {
        String classSpecific = o.getClassSpecificName();
        if (classSpecific != null)
            return classSpecific;
        return o.getDisplayName();
    }
}
