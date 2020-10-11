package com.github.vlsi.mat.calcite.neo;

import com.github.vlsi.mat.calcite.functions.CollectionsFunctions;
import com.github.vlsi.mat.calcite.functions.HeapFunctions;
import com.github.vlsi.mat.calcite.functions.SnapshotFunctions;
import com.github.vlsi.mat.calcite.functions.TableFunctions;
import com.github.vlsi.mat.calcite.schema.objects.IClassesList;
import com.github.vlsi.mat.calcite.schema.objects.InstanceByClassTable;
import com.github.vlsi.mat.calcite.schema.objects.InstanceIdsByClassTable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.advise.SqlAdvisorGetHintsFunction;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class PackageSchema extends AbstractSchema {
  private final Multimap<String, Function> functions;
  private final Map<String, PackageSchema> subPackages = new HashMap<>();
  private final Map<String, Table> classes = new HashMap<>();

  private PackageSchema() {
    this(ImmutableMultimap.<String, Function>of());
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

  private void addClass(String className, IClassesList classesList) {
    addClass(className, new InstanceByClassTable(classesList));
    addClass("$ids$:" + className, new InstanceIdsByClassTable(classesList));
  }

  @Override
  protected Map<String, Schema> getSubSchemaMap() {
    return ImmutableMap.<String, Schema>copyOf(subPackages);
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
    for (int i = 0; i < nameParts.length - 1; i++) {
      targetSchema = targetSchema.getPackage(nameParts[i]);
    }
    return targetSchema;
  }

  public static PackageSchema resolveSchema(ISnapshot snapshot) {

    try {
      // Create functions for schema
      ImmutableMultimap.Builder<String, Function> builder = ImmutableMultimap.builder();
      builder.putAll(ScalarFunctionImpl.functions(HeapFunctions.class));
      builder.putAll(CollectionsFunctions.createAll());
      builder.putAll(TableFunctions.createAll());
      builder.putAll(SnapshotFunctions.createAll(snapshot));
      builder.put("getHints", new SqlAdvisorGetHintsFunction());
      ImmutableMultimap<String, Function> functions = builder.build();

      // Create default schema
      PackageSchema defaultSchema = new PackageSchema(functions);

      // Collect all classes names
      Collection<IClass> classes = snapshot.getClasses();
      HashSet<String> classesNames = new HashSet<>();
      for (IClass iClass : classes) {
        classesNames.add(iClass.getName());
      }

      PackageSchema instanceOfPackage = defaultSchema.getPackage("instanceof");

      // Add all classes to schema
      for (String fullClassName : classesNames) {
        IClassesList classOnly = new IClassesList(snapshot, fullClassName, false);

        // Make class available via "package.name.ClassName" (full class name in a root schema)
        defaultSchema.addClass(fullClassName, classOnly);

        String simpleClassName = getClassName(fullClassName);

        // Make class available via package.name.ClassName (schema.schema.Class)
        PackageSchema packageSchema = getPackage(defaultSchema, fullClassName);
        packageSchema.addClass(simpleClassName, classOnly);

        // Add instanceof
        IClassesList withSubClasses = new IClassesList(snapshot, fullClassName, true);

        // Make class available via "instanceof.package.name.ClassName"
        defaultSchema.addClass("instanceof." + fullClassName, withSubClasses);

        // Make class available via instanceof.package.name.ClassName
        PackageSchema instanceOfSchema = getPackage(instanceOfPackage, fullClassName);
        instanceOfSchema.addClass(simpleClassName, withSubClasses);

      }

      // Add thread stacks table
      defaultSchema.getPackage("native").addClass("ThreadStackFrames", new SnapshotThreadStacksTable(snapshot));

      return defaultSchema;
    } catch (SnapshotException e) {
      throw new RuntimeException("Cannot resolve package schemes", e);
    }
  }
}
