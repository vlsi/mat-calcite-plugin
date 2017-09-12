package com.github.vlsi.mat.calcite;

import com.github.vlsi.mat.calcite.functions.HeapFunctions;

import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IObject;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class HeapReference implements Comparable<HeapReference>, Map {
    private final IObject o;

    public HeapReference(IObject o) {
        this.o = o;
    }

    public static HeapReference valueOf(IObject o) {
        return o == null ? null : new HeapReference(o);
    }

    public IObject getIObject() {
        return o;
    }

    @Override
    public boolean equals(Object o1) {
        if (this == o1) return true;
        if (o1 == null || getClass() != o1.getClass()) return false;

        HeapReference that = (HeapReference) o1;

        return o.equals(that.o);
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

    @Override
    public Object get(Object key) {
        // This Map.get is called by Calcite when this['fieldA'] SQL syntax is used
        if (key == null) {
            return null;
        }
        if (key instanceof Number && getIObject() instanceof IArray) {
            return HeapFunctions.getField(this, "[" + String.valueOf(key) + "]");
        }
        String fieldName = String.valueOf(key);
        if (fieldName.charAt(0) == '@') {
            if ("@shallow".equalsIgnoreCase(fieldName)) {
                return HeapFunctions.shallowSize(this);
            }
            if ("@retained".equalsIgnoreCase(fieldName)) {
                return HeapFunctions.retainedSize(this);
            }
        }
        return HeapFunctions.getField(this, fieldName);
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set keySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry> entrySet() {
        throw new UnsupportedOperationException();
    }
}
