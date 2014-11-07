package com.github.vlsi.mat.optiq;

import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class HeapReference implements Comparable<HeapReference> {
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
    public boolean equals(Object o1) {
        if (this == o1) return true;
        if (o1 == null || getClass() != o1.getClass()) return false;

        HeapReference that = (HeapReference) o1;

        if (!o.equals(that.o)) return false;
        if (!snapshot.equals(that.snapshot)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = snapshot.hashCode();
        result = 31 * result + o.hashCode();
        return result;
    }

    @Override
    public String toString() {
        String classSpecific = o.getClassSpecificName();
        if (classSpecific != null)
            return classSpecific;
        return o.getDisplayName();
    }

    @Override
    public int compareTo(HeapReference o) {
        int cmp = this.toString().compareTo(o.toString());
        if (cmp != 0)
            return cmp;
        return getIObject().getObjectId() - o.getIObject().getObjectId();
    }
}
