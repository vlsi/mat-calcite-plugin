package com.github.vlsi.mat.calcite;

import com.github.vlsi.mat.calcite.rex.RexBuilderContext;
import com.github.vlsi.mat.calcite.functions.IObjectMethods;
import com.github.vlsi.mat.calcite.functions.ISnapshotMethods;
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
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.apache.calcite.util.Pair;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

import java.util.ArrayList;
import java.util.List;

public class ClassRowTypeCache {

	public static LoadingCache<RelDataTypeFactory, LoadingCache<IClass, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>>> CACHE = CacheBuilder
			.newBuilder()
			.weakKeys()
			.build(new CacheLoader<RelDataTypeFactory, LoadingCache<IClass, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>>>() {
				@Override
				public LoadingCache<IClass, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>> load(
						final RelDataTypeFactory typeFactory) throws Exception {
					return CacheBuilder.newBuilder().weakKeys()
							.build(new ClassRowTypeResolver(typeFactory));
				}
			});

	private static final class ClassRowTypeResolver
	extends
			CacheLoader<IClass, Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>>> {
		private final RelDataTypeFactory typeFactory;

		private ClassRowTypeResolver(RelDataTypeFactory typeFactory) {
			this.typeFactory = typeFactory;
		}

		@Override
		public Pair<RelDataType, List<Function<RexBuilderContext, RexNode>>> load(
				IClass clazz) throws Exception {
			ISnapshot snapshot = clazz.getSnapshot();
			List<Function<RexBuilderContext, RexNode>> resolvers = new ArrayList<Function<RexBuilderContext, RexNode>>();
			List<String> names = new ArrayList<String>();
			List<RelDataType> types = new ArrayList<RelDataType>();
//			names.add("@ID");
//			types.add(typeFactory.createJavaType(int.class));
//			resolvers.add(ObjectIdComputer.INSTANCE);

			names.add("@THIS");
			types.add(typeFactory.createJavaType(HeapReference.class));
			resolvers.add(ThisComputer.INSTANCE);

			names.add("@SHALLOW");
			types.add(typeFactory.createJavaType(long.class));
			resolvers.add(ShallowSizeComputer.INSTANCE);

			names.add("@RETAINED");
			types.add(typeFactory.createJavaType(long.class));
			resolvers.add(RetainedSizeComputer.INSTANCE);

			for(; clazz != null; clazz = clazz.getSuperClass())
			{
				List<FieldDescriptor> fields = clazz.getFieldDescriptors();
				for (FieldDescriptor fieldDescriptor : fields)
				{
					names.add(fieldDescriptor.getName());
					int type = fieldDescriptor.getType();
					RelDataType dataType;
					switch (type)
					{
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
					types.add(dataType);
					if (type == IObject.Type.OBJECT)
						resolvers.add(new ReferencePropertyComputer(fieldDescriptor.getName()));
					else
						resolvers.add(new SimplePropertyComputer(fieldDescriptor.getName(), type, dataType));
				}
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
							ReturnTypes.explicit(typeFactory.createJavaType(HeapReference.class)),
							null,
							OperandTypes.ANY,
							ImmutableList.of(typeFactory.createTypeWithNullability(typeFactory.createJavaType(IObject.class), false)),
							ScalarFunctionImpl.create(ISnapshotMethods.class, "toReference")
					);
			return context.getBuilder().makeCall(UDF, context.getIObject());
		}
	}

	static class ShallowSizeComputer implements Function<RexBuilderContext, RexNode> {
		public static final ShallowSizeComputer INSTANCE = new ShallowSizeComputer();

		@Override
		public RexNode apply(RexBuilderContext context) {
			RelOptCluster cluster = context.getCluster();
			RelDataTypeFactory typeFactory = cluster.getTypeFactory();
			final SqlFunction UDF =
					new SqlUserDefinedFunction(
							new SqlIdentifier("GET_SHALLOW_SIZE", SqlParserPos.ZERO),
							ReturnTypes.explicit(typeFactory.createJavaType(long.class)),
							null,
							OperandTypes.ANY_ANY,
							ImmutableList.of(typeFactory.createTypeWithNullability(typeFactory.createJavaType(ISnapshot.class), false),
									typeFactory.createJavaType(int.class)),
							ScalarFunctionImpl.create(ISnapshotMethods.class, "getShallowSize"));
			return context.getBuilder().makeCall(UDF, context.getSnapshot(), context.getIObjectId());
		}
	}

	static class RetainedSizeComputer implements Function<RexBuilderContext, RexNode> {
		public static final RetainedSizeComputer INSTANCE = new RetainedSizeComputer();

		@Override
		public RexNode apply(RexBuilderContext context) {
			RelOptCluster cluster = context.getCluster();
			RelDataTypeFactory typeFactory = cluster.getTypeFactory();
			final SqlFunction UDF =
					new SqlUserDefinedFunction(
							new SqlIdentifier("GET_RETAINED_SIZE", SqlParserPos.ZERO),
							ReturnTypes.explicit(typeFactory.createJavaType(long.class)),
							null,
							OperandTypes.ANY_ANY,
							ImmutableList.of(typeFactory.createTypeWithNullability(typeFactory.createJavaType(ISnapshot.class), false),
									typeFactory.createJavaType(int.class)),
							ScalarFunctionImpl.create(ISnapshotMethods.class, "getRetainedSize"));
			return context.getBuilder().makeCall(UDF, context.getSnapshot(), context.getIObjectId());
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
							ReturnTypes.explicit(typeFactory.createJavaType(Object.class)),
							null,
							OperandTypes.ANY_ANY,
							ImmutableList.of(typeFactory.createTypeWithNullability(typeFactory.createJavaType(IObject.class), false),
									typeFactory.createJavaType(int.class)),
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
							ReturnTypes.explicit(typeFactory.createJavaType(HeapReference.class)),
							null,
							OperandTypes.ANY_ANY,
							ImmutableList.of(typeFactory.createTypeWithNullability(typeFactory.createJavaType(IObject.class), false),
									typeFactory.createJavaType(String.class)),
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
