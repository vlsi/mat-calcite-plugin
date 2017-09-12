package com.github.vlsi.mat.calcite.schema.objects;

import org.eclipse.mat.snapshot.model.FieldDescriptor;

public class Field {
    private final String name;
    private final int type;

    public Field(String name, int type) {
        this.name = name;
        this.type = type;
    }

    public Field(FieldDescriptor descriptor) {
        this(descriptor.getName(), descriptor.getType());
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        return type == field.type && name.equals(field.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type;
        return result;
    }
}
