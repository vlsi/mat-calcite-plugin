package com.github.vlsi.mat.calcite.schema.objects;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.functions.IObjectMethods;
import com.github.vlsi.mat.calcite.functions.ISnapshotMethods;
import com.github.vlsi.mat.calcite.rex.RexBuilderContext;
import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.apache.calcite.util.Pair;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

import java.util.*;

public class ClassRowTypeCache {

    public static LoadingCache<RelDataTypeFactory, LoadingCache<IClassesList, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>>> CACHE = CacheBuilder
            .newBuilder()
            .weakKeys()
            .build(new CacheLoader<RelDataTypeFactory, LoadingCache<IClassesList, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>>>() {
                @Override
                public LoadingCache<IClassesList, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>> load(
                        final RelDataTypeFactory typeFactory) throws Exception {
                    return CacheBuilder.newBuilder().weakKeys()
							.build(new ClassRowTypeResolver(typeFactory));
				}
			});

    private static interface ExtraTypes extends IObject.Type {
        int ANY = -1;
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
            }
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
            resolvers.add(ThisComputer.INSTANCE);

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
                names.add(field.getName());
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
                    default:
                        dataType = typeFactory.createJavaType(String.class);
                        break;
                }
                types.add(dataType);
                if (type == IObject.Type.OBJECT)
                    resolvers.add(new ReferencePropertyComputer(field.getName()));
                else
                    resolvers.add(new SimplePropertyComputer(field.getName(), type, dataType));
            }

            return Pair.of(
                    typeFactory.createStructType(types, names),
					resolvers);
		}
	}

	static class ThisComputer implements Function<RexBuilderContext, RexNode> {
		public static final ThisComputer INSTANCE = new ThisComputer();

		public RexNode apply(RexBuilderContext context) {
			RelOptCluster cluster = context.getCluster();
			RelDataTypeFactory typeFactory = cluster.getTypeFactory();
			final SqlFunction UDF =
					new SqlUserDefinedFunction(
							new SqlIdentifier("TO_REFERENCE", SqlParserPos.ZERO),
                            SqlKind.OTHER_FUNCTION,
							ReturnTypes.explicit(typeFactory.createJavaType(HeapReference.class)),
							null,
							OperandTypes.operandMetadata(
							        ImmutableList.of(SqlTypeFamily.ANY),
                                    tf -> ImmutableList.of(tf.createTypeWithNullability(tf.createJavaType(IObject.class), false)),
                                    i -> "iobject",
                                    i -> false),
							ScalarFunctionImpl.create(ISnapshotMethods.class, "toReference")
					);
			return context.getBuilder().makeCall(UDF, context.getIObject());
		}
	}

	static class SimplePropertyComputer implements Function<RexBuilderContext, RexNode> {
		private final String name;
		private final int type;
		private final RelDataType dataType;

		public SimplePropertyComputer(String name, int type, RelDataType dataType) {
			this.name = name;
			this.type = type;
			this.dataType = dataType;
		}

		@Override
		public RexNode apply(RexBuilderContext context) {
			RelOptCluster cluster = context.getCluster();
			RelDataTypeFactory typeFactory = cluster.getTypeFactory();
			final SqlFunction UDF =
					new SqlUserDefinedFunction(
							new SqlIdentifier("RESOLVE_SIMPLE", SqlParserPos.ZERO),
							SqlKind.OTHER_FUNCTION,
							ReturnTypes.explicit(typeFactory.createJavaType(Object.class)),
							null,
                            OperandTypes.operandMetadata(
                                    ImmutableList.of(SqlTypeFamily.ANY, SqlTypeFamily.CHARACTER),
                                    tf -> ImmutableList.of(
                                            tf.createTypeWithNullability(tf.createJavaType(IObject.class), false),
                                            tf.createJavaType(String.class)),
                                    i -> i == 0 ? "iobject" : "fieldName",
                                    i -> false),
							ScalarFunctionImpl.create(IObjectMethods.class, "resolveSimpleValue"));
			RexBuilder b = context.getBuilder();
			RexNode rexNode = b.makeCall(UDF, context.getIObject(), b.makeLiteral(name));
			return b.makeCast(dataType, rexNode);
		}

		@Override
		public String toString() {
			return "Property{field=" + name + "}";
		}
	}

	static class ReferencePropertyComputer implements Function<RexBuilderContext, RexNode> {
		private final String name;

		public ReferencePropertyComputer(String name) {
			this.name = name;
		}

		@Override
		public RexNode apply(RexBuilderContext context) {
			RelOptCluster cluster = context.getCluster();
			RelDataTypeFactory typeFactory = cluster.getTypeFactory();
			final SqlFunction UDF =
					new SqlUserDefinedFunction(
							new SqlIdentifier("RESOLVE_REFERENCE", SqlParserPos.ZERO),
							SqlKind.OTHER_FUNCTION,
							ReturnTypes.explicit(typeFactory.createJavaType(HeapReference.class)),
							null,
                            OperandTypes.operandMetadata(
                                    ImmutableList.of(SqlTypeFamily.ANY, SqlTypeFamily.CHARACTER),
                                    tf -> ImmutableList.of(
                                            tf.createTypeWithNullability(tf.createJavaType(IObject.class), false),
                                            tf.createJavaType(String.class)),
                                    i -> i == 0 ? "iobject" : "fieldName",
                                    i -> false),
							ScalarFunctionImpl.create(IObjectMethods.class, "resolveReferenceValue"));
			RexBuilder b = context.getBuilder();
			return b.makeCall(UDF, context.getIObject(), b.makeLiteral(name));
		}

		@Override
		public String toString() {
			return "Property{field=" + name + "}";
		}
	}
}
