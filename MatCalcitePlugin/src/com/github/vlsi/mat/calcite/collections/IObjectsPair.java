package com.github.vlsi.mat.calcite.collections;

import org.eclipse.mat.snapshot.model.IObject;

import java.util.Map;

class IObjectsPair implements Map.Entry<IObject, IObject> {
    private final IObject key;
    private final IObject value;

    public IObjectsPair(IObject key, IObject value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public IObject getKey() {
        return key;
    }

    @Override
    public IObject getValue() {
        return value;
    }

    @Override
    public IObject setValue(IObject value) {
        throw new UnsupportedOperationException();
    }
}
