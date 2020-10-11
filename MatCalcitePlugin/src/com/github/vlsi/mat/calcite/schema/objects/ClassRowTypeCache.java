package com.github.vlsi.mat.calcite.schema.objects;

import com.github.vlsi.mat.calcite.rex.RexBuilderContext;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.Pair;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import static com.github.vlsi.mat.calcite.schema.objects.SnapshotRexExpressions.getClassLoader;
import static com.github.vlsi.mat.calcite.schema.objects.SnapshotRexExpressions.getClassName;
import static com.github.vlsi.mat.calcite.schema.objects.SnapshotRexExpressions.getClassOf;
import static com.github.vlsi.mat.calcite.schema.objects.SnapshotRexExpressions.getSuper;
import static com.github.vlsi.mat.calcite.schema.objects.SnapshotRexExpressions.resolveField;

public class ClassRowTypeCache {

  public static LoadingCache<RelDataTypeFactory, LoadingCache<IClassesList, Pair<RelDataType,
      List<Function<RexBuilderContext, RexNode>>>>> CACHE = CacheBuilder
      .newBuilder()
      .weakKeys()
      .build(new CacheLoader<RelDataTypeFactory, LoadingCache<IClassesList, Pair<RelDataType,
          List<Function<RexBuilderContext, RexNode>>>>>() {
        @Override
        public LoadingCache<IClassesList, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>> load(
            final RelDataTypeFactory typeFactory) throws Exception {
          return CacheBuilder.newBuilder().weakKeys()
              .build(new ClassRowTypeResolver(typeFactory));
        }
      });

  private interface ExtraTypes extends IObject.Type {
    int ANY = -1;
    int CHARACTER = -2;
  }

  private static final class ClassRowTypeResolver
      extends
      CacheLoader<IClassesList, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>> {
    private final RelDataTypeFactory typeFactory;

    private ClassRowTypeResolver(RelDataTypeFactory typeFactory) {
      this.typeFactory = typeFactory;
    }

    private LinkedHashMap<String, Field> getAllInstanceFields(IClass clazz) {
      // In case base class and subclass have a field with the same name, we just use the one from a subclass
      // TODO: use org.eclipse.mat.snapshot.model.IInstance.getFields() in those cases
      LinkedHashMap<String, Field> seenFields = new LinkedHashMap<>();
      for (IClass i = clazz; i != null; i = i.getSuperClass()) {
        List<FieldDescriptor> fds = i.getFieldDescriptors();
        // Iterate fields in a reversed order in order.
        // We iterate class hierarchy in reverse as well (subclass -> superclass),
        for (ListIterator<FieldDescriptor> it = fds.listIterator(fds.size()); it.hasPrevious(); ) {
          FieldDescriptor fd = it.previous();
          if (!seenFields.containsKey(fd.getName())) {
            seenFields.put(fd.getName(), new Field(fd));
          }
        }
        if (IClass.JAVA_LANG_CLASS.equals(i.getName())) {
          seenFields.put(SpecialFields.CLASS_LOADER, new Field(SpecialFields.CLASS_LOADER, IObject.Type.OBJECT));
          seenFields.put(SpecialFields.SUPER, new Field(SpecialFields.SUPER, IObject.Type.OBJECT));
          seenFields.put(SpecialFields.CLASS_NAME, new Field(SpecialFields.CLASS_NAME, ExtraTypes.CHARACTER));
          // Hide the default "Class.name" field as it might be null
          seenFields.remove("name");
        }
      }
      seenFields.put(SpecialFields.CLASS, new Field(SpecialFields.CLASS, IObject.Type.OBJECT));
      return seenFields;
    }

    @Override
    public Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>> load(
        IClassesList classesList) throws Exception {
      List<Function<RexBuilderContext, RexNode>> resolvers = new ArrayList<Function<RexBuilderContext, RexNode>>();
      List<String> names = new ArrayList<String>();
      List<RelDataType> types = new ArrayList<RelDataType>();

      names.add("this");
      RelDataType anyNull = typeFactory.createSqlType(SqlTypeName.ANY);
      types.add(anyNull);
      resolvers.add(SnapshotRexExpressions::computeThis);

      // In case multiple classes have a field with different datatype, we just make field type "ANY"

      LinkedHashMap<String, Field> fields = null;
      for (IClass aClass : classesList.getRootClasses()) {
        LinkedHashMap<String, Field> allInstanceFields = getAllInstanceFields(aClass);
        if (fields == null) {
          fields = allInstanceFields;
          continue;
        }
        for (Iterator<Map.Entry<String, Field>> it = fields.entrySet().iterator(); it.hasNext(); ) {
          Map.Entry<String, Field> entry = it.next();
          String fieldName = entry.getKey();
          Field field = allInstanceFields.get(fieldName);
          if (field == null) {
            // Keep just common fields
            it.remove();
            continue;
          }
          if (entry.getValue().getType() == field.getType()) {
            continue;
          }
          // If data type differs, use "ANY" type
          entry.setValue(new Field(fieldName, ExtraTypes.ANY));
        }
      }
      ;

      List<Field> fieldsInOrder = fields == null ? Collections.<Field>emptyList() : new ArrayList<>(fields.values());
      Collections.reverse(fieldsInOrder);

      for (Field field : fieldsInOrder) {
        int type = field.getType();
        RelDataType dataType;
        switch (type) {
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
          dataType = anyNull;
          break;
        case ExtraTypes.ANY:
          dataType = anyNull;
          break;
        case ExtraTypes.CHARACTER:
          // fall-through
        default:
          dataType = typeFactory.createJavaType(String.class);
          break;
        }
        types.add(dataType);
        String fieldName = field.getName();
        Function<RexBuilderContext, RexNode> columnCalc;
        switch (fieldName) {
        case SpecialFields.CLASS:
          // This is Object#getClass
          // For java.lang.Class it returns "java.lang.Class"
          columnCalc = (RexBuilderContext context) ->
              getClassOf(context, context.getIObjectId());
          break;
        case SpecialFields.SUPER:
          // This property is available only for classes
          // It is assumed that getIObject would return IClass
          columnCalc = (RexBuilderContext context) ->
              getSuper(context, context.getIObject());
          break;
        case SpecialFields.CLASS_LOADER:
          // This property is available only for classes
          // It is assumed that getIObject would return IClass
          columnCalc = (RexBuilderContext context) ->
              getClassLoader(context, context.getIObject());
          break;
        case SpecialFields.CLASS_NAME:
          // This property is available only for classes
          // It is assumed that getIObject would return IClass
          columnCalc = (RexBuilderContext context) ->
              getClassName(context, context.getIObject());
          fieldName = "name";
          break;
        default:
          String resolvedField = fieldName;
          columnCalc = (RexBuilderContext context) ->
              resolveField(context, resolvedField);
        }
        if (type == IObject.Type.OBJECT) {
          // Wrap object fields with HeapReference
          Function<RexBuilderContext, RexNode> prev = columnCalc;
          columnCalc = (RexBuilderContext context) ->
              context.toHeapReference(prev.apply(context));
        } else if (dataType != anyNull) {
          Function<RexBuilderContext, RexNode> prev = columnCalc;
          columnCalc = (RexBuilderContext context) ->
              context.getBuilder().makeCast(dataType, prev.apply(context));
        }
        names.add(fieldName);
        resolvers.add(columnCalc);
      }

      return Pair.of(
          typeFactory.createStructType(types, names),
          resolvers);
    }
  }
}
