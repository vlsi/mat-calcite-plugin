package com.github.vlsi.mat.optiq;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eigenbase.reltype.RelDataType;
import org.eigenbase.reltype.RelDataTypeFactory;
import org.eigenbase.util.Pair;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class ClassRowTypeCache {

	public static LoadingCache<RelDataTypeFactory, LoadingCache<IClass, Pair<RelDataType, List<Function<Integer, Object>>>>> CACHE = CacheBuilder
			.newBuilder()
			.weakKeys()
			.build(new CacheLoader<RelDataTypeFactory, LoadingCache<IClass, Pair<RelDataType, List<Function<Integer, Object>>>>>() {
				@Override
				public LoadingCache<IClass, Pair<RelDataType, List<Function<Integer, Object>>>> load(
						final RelDataTypeFactory typeFactory) throws Exception {
					return CacheBuilder.newBuilder().weakKeys()
							.build(new ClassRowTypeResolver(typeFactory));
				}
			});

	private static final class ClassRowTypeResolver
	extends
	CacheLoader<IClass, Pair<RelDataType, List<Function<Integer, Object>>>> {
		private final RelDataTypeFactory typeFactory;

		private ClassRowTypeResolver(RelDataTypeFactory typeFactory) {
			this.typeFactory = typeFactory;
		}

		@Override
		public Pair<RelDataType, List<Function<Integer, Object>>> load(
				IClass clazz) throws Exception {
			ISnapshot snapshot = clazz.getSnapshot();
			List<Function<Integer, Object>> resolvers = new ArrayList<Function<Integer, Object>>();
			List<String> names = new ArrayList<String>();
			List<RelDataType> types = new ArrayList<RelDataType>();
			names.add("@ID");
			types.add(typeFactory.createJavaType(int.class));
			resolvers.add(null);

			names.add("@SHALLOW");
			types.add(typeFactory.createJavaType(long.class));
			resolvers.add(new ShallowSizeComputer(snapshot));

			names.add("@RETAINED");
			types.add(typeFactory.createJavaType(long.class));
			resolvers.add(new RetainedSizeComputer(snapshot));

			while (clazz != null)
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
					resolvers.add(new PropertyComputer(snapshot, fieldDescriptor
							.getName()));
				}
				clazz = clazz.getSuperClass();
			}
			return Pair.of(
					typeFactory.createStructType(Pair.zip(names, types)),
					resolvers);
		}
	}

	static abstract class BaseComputer implements Function<Integer, Object> {
		ISnapshot snapshot;

		public BaseComputer(ISnapshot snapshot) {
			this.snapshot = snapshot;
		}
	}

	static class ShallowSizeComputer extends BaseComputer {
		public ShallowSizeComputer(ISnapshot snapshot) {
			super(snapshot);
		}

		@Override
		public Object apply(Integer input) {
			int objectId = input.intValue();
			try {
				return snapshot.getHeapSize(objectId);
			} catch (SnapshotException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}

	static class RetainedSizeComputer extends BaseComputer {
		public RetainedSizeComputer(ISnapshot snapshot) {
			super(snapshot);
		}

		@Override
		public Object apply(Integer input) {
			int objectId = input.intValue();
			try {
				return snapshot.getRetainedHeapSize(objectId);
			} catch (SnapshotException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}

	static class PropertyComputer extends BaseComputer {
		private String field;

		public PropertyComputer(ISnapshot snapshot, String field) {
			super(snapshot);
			this.field = field;
		}

		@Override
		public Object apply(Integer input) {
			int objectId = input.intValue();
			try {
				IObject object = snapshot.getObject(objectId);
				Object res = object.resolveValue(field);
				if (res instanceof IObject) {
					return new HeapReference(snapshot, (IObject) res);
				}
				return res;
			} catch (SnapshotException e) {
				e.printStackTrace();
				return 0;
			}
		}

		@Override
		public String toString() {
			return "Property{field=" + field + "}";
		}
	}
}
