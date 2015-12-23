package com.github.vlsi.mat.calcite.neo;

import com.github.vlsi.mat.calcite.HeapReference;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.linq4j.BaseQueryable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.util.ImmutableBitSet;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class SnapshotClassTable extends AbstractQueryableTable {
    private final IClass[] classes;
    private Field[] fields;

    public SnapshotClassTable(List<IClass> classes) {
        super(Object[].class);
        this.classes = classes.toArray(new IClass[classes.size()]);
    }

    @Override
    public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schema, String expression) {
        return new BaseQueryable<T>(null, Object[].class, null) {
            @Override
            public Enumerator<T> enumerator() {
                return new ClassesEnumerator<>(classes, getFields());
            }
        };
    }

    @Override
    public Statistic getStatistic() {
        int classesCount = 0;
        for (IClass snapshotClass : classes) {
            classesCount += snapshotClass.getNumberOfObjects();
        }
        return Statistics.of(classesCount, ImmutableList.of(ImmutableBitSet.of(1)));
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        RelDataTypeFactory.FieldInfoBuilder builder = typeFactory.builder();
        for (Field field : getFields()) {
            RelDataType dataType;
            switch (field.getType()) {
                case IObject.Type.BOOLEAN:
                    dataType = typeFactory.createJavaType(boolean.class);
                    break;
                case IObject.Type.BYTE:
                    dataType = typeFactory.createJavaType(byte.class);
                    break;
                case IObject.Type.CHAR:
                    dataType = typeFactory.createJavaType(char.class);
                    break;
                case IObject.Type.DOUBLE:
                    dataType = typeFactory.createJavaType(double.class);
                    break;
                case IObject.Type.FLOAT:
                    dataType = typeFactory.createJavaType(float.class);
                    break;
                case IObject.Type.SHORT:
                    dataType = typeFactory.createJavaType(short.class);
                    break;
                case IObject.Type.INT:
                    dataType = typeFactory.createJavaType(int.class);
                    break;
                case IObject.Type.LONG:
                    dataType = typeFactory.createJavaType(long.class);
                    break;
                case IObject.Type.OBJECT:
                    dataType = typeFactory.createJavaType(HeapReference.class);
                    break;
                default:
                    dataType = typeFactory.createJavaType(String.class);
                    break;
            }
            builder.add(field.getName(), dataType);
        }
        return builder.build();
    }

    private Field[] getFields() {
        if (this.fields == null) {
            List<Field> fields = new ArrayList<>();
            // First is virtual 'this' column
            fields.add(new Field());
            // Now, add all fields from given class and its superclasses
            for (IClass snapshotClass = classes[0]; snapshotClass != null; snapshotClass = snapshotClass.getSuperClass()) {
                for (FieldDescriptor descriptor : snapshotClass.getFieldDescriptors()) {
                    fields.add(new Field(descriptor));
                }

            }
            this.fields = fields.toArray(new Field[fields.size()]);
        }
        return this.fields;
    }

    private interface FieldResolver {
        Object resolve(IObject object, String name);
    }

    private static final FieldResolver THIS_RESOLVER = new FieldResolver() {
        @Override
        public Object resolve(IObject object, String name) {
            return HeapReference.valueOf(object);
        }
    };

    private static final FieldResolver SIMPLE_RESOLVER = new FieldResolver() {
        @Override
        public Object resolve(IObject object, String name) {
            try {
                return object.resolveValue(name);
            } catch (SnapshotException e) {
                throw new IllegalArgumentException("Cannot resolve simple field '" + name + "' for object '" + object + "'", e);
            }
        }
    };

    private static final FieldResolver REFERENCE_RESOLVER = new FieldResolver() {
        @Override
        public Object resolve(IObject object, String name) {
            try {
                return HeapReference.valueOf((IObject) object.resolveValue(name));
            } catch (SnapshotException e) {
                throw new IllegalArgumentException("Cannot resolve reference field '" + name + "' for object '" + object + "'", e);
            }
        }
    };

    private static class Field {
        private final String name;
        private final int type;
        private final FieldResolver resolver;

        public Field() {
            name = "this";
            type = IObject.Type.OBJECT;
            resolver = THIS_RESOLVER;
        }

        public Field(FieldDescriptor descriptor) {
            name = descriptor.getName();
            type = descriptor.getType();
            if (type == IObject.Type.OBJECT) {
                resolver = REFERENCE_RESOLVER;
            } else {
                resolver = SIMPLE_RESOLVER;
            }
        }

        public String getName() {
            return name;
        }

        public int getType() {
            return type;
        }

        public Object resolve(IObject object) {
            return resolver.resolve(object, getName());
        }
    }

    private static class ClassesEnumerator<T> implements Enumerator<T> {
        private final IClass[] classes;
        private final Field[] fields;

        private int currentClass = -1;
        private int currentObject = -1;
        private int[] objects;
        private Object[] currentResult;

        public ClassesEnumerator(IClass[] classes, Field[] fields) {
            this.classes = classes;
            this.fields = fields;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T current() {
            if (currentResult == null) {
                throw new NoSuchElementException();
            } else {
                // It looks like hack, but if there is only one element in the row, then we should return
                // its value, not the array (otherwise, the entire array will be handled as column value).
                // It
                return (T)(currentResult.length == 1 ? currentResult[0] : currentResult);
            }
        }

        @Override
        public boolean moveNext() {
            do {
                if (advanceObject()) {
                    return true;
                }
            } while (advanceClass());
            return false;
        }

        @Override
        public void reset() {
            currentClass = -1;
            currentObject = -1;
            currentResult = null;
        }

        @Override
        public void close() {
            reset();
        }

        private boolean advanceObject() {
            if (currentClass == -1) {
                return false;
            } else if (currentObject < objects.length - 1) {
                currentObject++;
                resolveObject();
                return true;
            } else {
                return false;
            }
        }

        private boolean advanceClass() {
            if (currentClass < classes.length - 1) {
                currentClass++;
                currentObject = -1;
                resolveClass();
                return true;
            } else {
                currentResult = null;
                return false;
            }
        }

        private void resolveClass() {
            try {
                objects = classes[currentClass].getObjectIds();
            } catch (SnapshotException e) {
                throw new RuntimeException(e);
            }
        }

        private void resolveObject() {
            try {
                currentResult = resolveObject(classes[currentClass], objects[currentObject], fields);
            } catch (SnapshotException e) {
                throw new RuntimeException(e);
            }
        }

        private Object[] resolveObject(IClass snapshotClass, int objectId, Field[] fields) throws SnapshotException {
            Object[] result = new Object[fields.length];
            IObject object = snapshotClass.getSnapshot().getObject(objectId);
            for (int i = 0; i < fields.length; i++) {
                result[i] = fields[i].resolve(object);
            }
            return result;
        }
    }
}
