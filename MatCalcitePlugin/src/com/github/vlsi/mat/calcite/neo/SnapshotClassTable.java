package com.github.vlsi.mat.calcite.neo;

import com.github.vlsi.mat.calcite.HeapReference;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

import java.util.*;

public class SnapshotClassTable extends AbstractTable implements ScannableTable {
    private final ISnapshot snapshot;
    private final String className;
    private final boolean includeSubClasses;

    private Field[] fields; // Will be calculated on first invocation

    public SnapshotClassTable(ISnapshot snapshot, String className, boolean includeSubClasses) {
        this.snapshot = snapshot;
        this.className = className;
        this.includeSubClasses = includeSubClasses;
    }

    @Override
    public Statistic getStatistic() {
        int classesCount = 0;
        for (IClass snapshotClass : getClasses()) {
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

    @Override
    public Enumerable<Object[]> scan(DataContext dataContext) {
        return new AbstractEnumerable<Object[]>() {
            @Override
            public Enumerator<Object[]> enumerator() {
                return new ClassesEnumerator(getClasses(), getFields());
            }
        };
    }

    private IClass[] getClasses() {
        try {
            Collection<IClass> classes = snapshot.getClassesByName(className, includeSubClasses);
            return classes.toArray(new IClass[classes.size()]);
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Field> resolveClassesFields() {
        try {
            List<Field> currentFields = null;
            for (IClass currentClass : snapshot.getClassesByName(className, false)) {
                if (currentFields == null) {
                    currentFields = resolveClassFields(currentClass);
                } else {
                    HashSet<Field> checkFields = new HashSet<>(resolveClassFields(currentClass));
                    for (Iterator<Field> it = currentFields.iterator(); it.hasNext();) {
                        if (!checkFields.contains(it.next())) {
                            it.remove();
                        }
                    }
                }
            }
            return currentFields;
        } catch (SnapshotException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Field> resolveClassFields(final IClass snapshotClass) {
        List<Field> fields = new ArrayList<>();
        for (IClass currentClass = snapshotClass; currentClass != null; currentClass = currentClass.getSuperClass()) {
            for (FieldDescriptor descriptor : currentClass.getFieldDescriptors()) {
                fields.add(new Field(descriptor));
            }
        }
        return fields;
    }

    private Field[] getFields() {
        if (this.fields == null) {
            List<Field> fields = new ArrayList<>();
            // First is virtual 'this' column
            fields.add(new Field());
            // Now, add all fields from given class and its superclasses
            fields.addAll(resolveClassesFields());
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

    private static class ClassesEnumerator extends GroupEnumerator<IClass, int[], Object[]> {
        private final Field[] fields;

        public ClassesEnumerator(IClass[] classes, Field[] fields) {
            super(classes);
            this.fields = fields;
        }

        @Override
        protected int[] resolveGroup(IClass snapshotClass) throws Exception {
            return snapshotClass.getObjectIds();
        }

        @Override
        protected int rowsCount(int[] rows) {
            return rows.length;
        }

        @Override
        protected Object[] resolveRow(IClass snapshotClass, int[] rows, int currentRow) throws Exception {
            Object[] result = new Object[fields.length];
            IObject object = snapshotClass.getSnapshot().getObject(rows[currentRow]);
            for (int i = 0; i < fields.length; i++) {
                result[i] = fields[i].resolve(object);
            }
            return result;
        }
    }
}
