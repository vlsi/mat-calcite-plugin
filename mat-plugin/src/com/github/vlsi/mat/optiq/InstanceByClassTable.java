package com.github.vlsi.mat.optiq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.hydromatic.linq4j.Enumerator;
import net.hydromatic.linq4j.Linq4j;
import net.hydromatic.linq4j.QueryProvider;
import net.hydromatic.linq4j.Queryable;
import net.hydromatic.optiq.SchemaPlus;
import net.hydromatic.optiq.impl.AbstractTableQueryable;
import net.hydromatic.optiq.impl.java.AbstractQueryableTable;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.FieldDescriptor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eigenbase.reltype.RelDataType;
import org.eigenbase.reltype.RelDataTypeFactory;
import org.eigenbase.util.Pair;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.primitives.Ints;

public class InstanceByClassTable extends AbstractQueryableTable {

	private ISnapshot snapshot;
	private String className;
	private boolean includeSubClasses;
	private List<Function<Object[], Object[]>> resolvers;

	static abstract class BaseComputer implements Function<Object[], Object[]> {
		int position;
		ISnapshot snapshot;

		public BaseComputer(int position, ISnapshot snapshot) {
			this.position = position;
			this.snapshot = snapshot;
		}

		public abstract Object compute(Object input);

		@Override
		public Object[] apply(Object[] input) {
			input[position] = compute(input[0]);
			return input;
		}
	}

	static class ShallowSizeComputer extends BaseComputer {
		public ShallowSizeComputer(int position, ISnapshot snapshot) {
			super(position, snapshot);
		}

		@Override
		public Object compute(Object input) {
			int objectId = ((Integer) input).intValue();
			try {
				return snapshot.getHeapSize(objectId);
			} catch (SnapshotException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}

	static class RetainedSizeComputer extends BaseComputer {
		public RetainedSizeComputer(int position, ISnapshot snapshot) {
			super(position, snapshot);
		}

		@Override
		public Object compute(Object input) {
			int objectId = ((Integer) input).intValue();
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

		public PropertyComputer(int position, ISnapshot snapshot, String field) {
			super(position, snapshot);
			this.field = field;
		}

		@Override
		public Object compute(Object input) {
			int objectId = ((Integer) input).intValue();
			try {
				IObject object = snapshot.getObject(objectId);
				Object res = object.resolveValue(field);
				if (res instanceof IObject) {
					String classSpecific = ((IObject) res)
							.getClassSpecificName();
					if (classSpecific != null)
						return classSpecific;
					return ((IObject) res).getDisplayName();
				}
				return res;
			} catch (SnapshotException e) {
				e.printStackTrace();
				return 0;
			}
		}
	}

	public InstanceByClassTable(ISnapshot snapshot, String className,
			boolean includeSubClasses) {
		super(Object[].class);
		this.snapshot = snapshot;
		this.className = className;
		this.includeSubClasses = includeSubClasses;
	}

	@Override
	public Queryable<Object[]> asQueryable(QueryProvider queryProvider,
			SchemaPlus schema, String tableName) {
		// TODO Auto-generated method stub
		return new AbstractTableQueryable<Object[]>(queryProvider, schema,
				this, tableName) {
			@Override
			public Enumerator<Object[]> enumerator() {
				final int columns = resolvers.size();
				Collection<IClass> classesByName;
				try {
					classesByName = snapshot.getClassesByName(className,
							includeSubClasses);
				} catch (SnapshotException e) {
					throw new IllegalStateException("Unable to get class "
							+ className);
				}
				FluentIterable<Object[]> res = FluentIterable
						.from(classesByName)
						.transformAndConcat(
								new Function<IClass, Iterable<Integer>>() {
									@Override
									public Iterable<Integer> apply(IClass input) {
										try {
											return Ints.asList(input
													.getObjectIds());
										} catch (SnapshotException e) {
											e.printStackTrace();
											return Collections.emptyList();
										}
									}
								}).transform(new Function<Integer, Object[]>() {
							@Override
							public Object[] apply(Integer input) {
								Object[] res = new Object[columns];
								res[0] = input;
								return res;
							}

						});
				for (int i = 1; i < resolvers.size(); i++) {
					res = res.transform(resolvers.get(i));
				}
				return Linq4j.iterableEnumerator(res);
			}
		};
	}

	@Override
	public RelDataType getRowType(RelDataTypeFactory typeFactory) {
		List<Function<Object[], Object[]>> resolvers = new ArrayList<Function<Object[], Object[]>>();
		List<String> names = new ArrayList<String>();
		List<RelDataType> types = new ArrayList<RelDataType>();
		try {
			names.add("@ID");
			types.add(typeFactory.createJavaType(int.class));
			resolvers.add(null);

			names.add("@SHALLOW");
			types.add(typeFactory.createJavaType(long.class));
			resolvers.add(new ShallowSizeComputer(resolvers.size(), snapshot));

			names.add("@RETAINED");
			types.add(typeFactory.createJavaType(long.class));
			resolvers.add(new RetainedSizeComputer(resolvers.size(), snapshot));

			Collection<IClass> classes = snapshot.getClassesByName(className,
					includeSubClasses);
			for (IClass clazz : classes) {
				List<FieldDescriptor> fields = clazz.getFieldDescriptors();
				for (FieldDescriptor fieldDescriptor : fields) {
					names.add(fieldDescriptor.getName());
					int type = fieldDescriptor.getType();
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
						dataType = typeFactory.createJavaType(String.class);
						break;
					default:
						dataType = typeFactory.createJavaType(String.class);
						break;
					}
					types.add(dataType);
					resolvers.add(new PropertyComputer(resolvers.size(),
							snapshot, fieldDescriptor.getName()));
				}
				break; // TODO: implement classes with different structure (e.g.
						// different class loaders)
			}
		} catch (SnapshotException e) {
			throw new IllegalStateException("Unable to get class " + className);
		}
		this.resolvers = resolvers;
		return typeFactory.createStructType(Pair.zip(names, types));
	}

}
