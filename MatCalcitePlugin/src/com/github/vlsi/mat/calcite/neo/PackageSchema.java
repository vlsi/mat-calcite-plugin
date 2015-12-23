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
    private final Map<String, Table> classes = new HashMap<>();

    private PackageSchema() {
        this(ImmutableMultimap.<String, Function>builder().build());
    }

    private PackageSchema(Multimap<String, Function> functions) {
        this.functions = functions;
    }

    private PackageSchema getPackage(String subSchemaName) {

        PackageSchema subSchema = subPackages.get(subSchemaName);
        if (subSchema == null) {
            subSchema = new PackageSchema();
            subPackages.put(subSchemaName, subSchema);
        }
        return subSchema;
    }

    private void addClass(String name, Table table) {
        if (!classes.containsKey(name)) {
            classes.put(name, table);
        }
    }

    @Override
    protected Map<String, Schema> getSubSchemaMap() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.putAll(subPackages);
        return Collections.unmodifiableMap(schemaMap);
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return Collections.unmodifiableMap(classes);
    }

    @Override
    protected Multimap<String, Function> getFunctionMultimap() {
        return functions;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    private static String getClassName(final String fullClassName) {
        int lastDotIndex = fullClassName.lastIndexOf('.');
        return lastDotIndex == -1 ? fullClassName : fullClassName.substring(lastDotIndex + 1);
    }

    private static PackageSchema getPackage(final PackageSchema rootPackage, final String fullClassName) {
        String[] nameParts = fullClassName.split("\\.");
        PackageSchema targetSchema = rootPackage;
        for(int i=0; i< nameParts.length-1; i++) {
            targetSchema = targetSchema.getPackage(nameParts[i]);
        }
        return targetSchema;
    }

    public static PackageSchema resolveSchema(ISnapshot snapshot) {

        try {
            // Create functions for schema
            ImmutableMultimap.Builder<String, Function> builder = ImmutableMultimap.builder();
            builder.putAll(ScalarFunctionImpl.createAll(HeapFunctions.class));
            builder.putAll(TableFunctions.createAll());
            ImmutableMultimap<String, Function> functions = builder.build();

            // Create default schema
            PackageSchema defaultSchema = new PackageSchema(functions);

            // Collect all classes names
            Collection<IClass> classes = snapshot.getClasses();
            HashSet<String> classesNames = new HashSet<>();
            for (IClass iClass : classes) {
                classesNames.add(iClass.getName());
            }

            // Add all classes to schema
            for (String fullClassName : classesNames) {
                String className = getClassName(fullClassName);

                // Add table to target schema and default schema
                SnapshotClassTable regularTable = new SnapshotClassTable(snapshot, fullClassName, false);
                getPackage(defaultSchema, fullClassName).addClass(className, regularTable);
                defaultSchema.addClass(fullClassName, regularTable);

                // Add instanceof
                SnapshotClassTable childrenTable = new SnapshotClassTable(snapshot, fullClassName, true);
                getPackage(defaultSchema.getPackage("instanceof"), fullClassName).addClass(className, childrenTable);
                defaultSchema.addClass("instanceof."+fullClassName, childrenTable);
            }

            // Add thread stacks table
            defaultSchema.getPackage("heap").addClass("ThreadStackFrames", new SnapshotThreadStacksTable(snapshot));

            return defaultSchema;
        } catch (SnapshotException e) {
            throw new RuntimeException("Cannot resolve package schemes", e);
        }
    }
}
