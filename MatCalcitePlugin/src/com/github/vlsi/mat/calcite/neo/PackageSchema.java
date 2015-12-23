package com.github.vlsi.mat.calcite.neo;

import com.github.vlsi.mat.calcite.functions.HeapFunctions;
import com.github.vlsi.mat.calcite.functions.TableFunctions;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

import java.util.*;

public class PackageSchema extends AbstractSchema {
    private final Multimap<String, Function> functions;
    private final Map<String, PackageSchema> subPackages = new HashMap<>();
    private final Map<String, List<IClass>> classes = new HashMap<>();

    private PackageSchema(boolean rootSchema) {
        ImmutableMultimap.Builder<String, Function> builder = ImmutableMultimap.builder();
        if (rootSchema) {
            builder.putAll(ScalarFunctionImpl.createAll(HeapFunctions.class));
            builder.putAll(TableFunctions.createAll());
        }
        functions = builder.build();
    }

    private PackageSchema getPackage(String subSchemaName) {
        PackageSchema subSchema = subPackages.get(subSchemaName);
        if (subSchema == null) {
            subSchema = new PackageSchema(false);
            subPackages.put(subSchemaName, subSchema);
        }
        return subSchema;
    }

    private void addClass(String name, IClass snapshotClass) {
        List<IClass> targetClasses = classes.get(name);
        if (targetClasses == null) {
            targetClasses = new ArrayList<>();
            classes.put(name, targetClasses);
        }
        targetClasses.add(snapshotClass);
    }

    @Override
    protected Map<String, Schema> getSubSchemaMap() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.putAll(subPackages);
        return Collections.unmodifiableMap(schemaMap);
    }

    @Override
    protected Map<String, Table> getTableMap() {
        Map<String, Table> tableMap = new HashMap<>();
        for(Map.Entry<String, List<IClass>> entry : classes.entrySet()) {
            tableMap.put(entry.getKey(), new SnapshotClassTable(entry.getValue()));
        }
        return Collections.unmodifiableMap(tableMap);
    }

    @Override
    protected Multimap<String, Function> getFunctionMultimap() {
        return functions;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    public static PackageSchema resolveSchema(ISnapshot snapshot) {
        PackageSchema defaultSchema = new PackageSchema(true);
        try {
            Collection<IClass> classes = snapshot.getClasses();
            for (IClass snapshotClass : classes) {
                String className = snapshotClass.getName();
                String[] nameParts = className.split("\\.");
                PackageSchema currentSchema = defaultSchema;
                for (int i = 0; i < nameParts.length - 1; i++) {
                    currentSchema = currentSchema.getPackage(nameParts[i]);
                }
                currentSchema.addClass(nameParts[nameParts.length - 1], snapshotClass);
            }
            return defaultSchema;
        } catch (SnapshotException e) {
            throw new RuntimeException("Cannot resolve package schemes", e);
        }
    }
}
