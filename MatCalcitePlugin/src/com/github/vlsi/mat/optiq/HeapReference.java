package com.github.vlsi.mat.optiq;

import org.eclipse.mat.snapshot.model.IObject;

public class HeapReference implements Comparable<HeapReference> {
    private final IObject o;

    public HeapReference(IObject o) {
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

        return true;
    }

    @Override
    public int hashCode() {
        return o.hashCode();
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
