package com.github.vlsi.mat.optiq;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import net.hydromatic.linq4j.Enumerator;
import net.hydromatic.linq4j.Linq4j;
import net.hydromatic.linq4j.QueryProvider;
import net.hydromatic.linq4j.Queryable;
import net.hydromatic.optiq.SchemaPlus;
import net.hydromatic.optiq.impl.AbstractTableQueryable;
import net.hydromatic.optiq.impl.java.AbstractQueryableTable;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;
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
	private List<Function<Integer, Object>> resolvers;

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
					final int pos = i;
					final Function<Integer, Object> resolver = resolvers
							.get(pos);
					res = res.transform(new Function<Object[], Object[]>() {
						@Override
						public Object[] apply(Object[] input) {
							input[pos] = resolver.apply((Integer) input[0]);
							return input;
						}

					});
				}

				return Linq4j.iterableEnumerator(res);
			}
		};
	}


	@Override
	public RelDataType getRowType(RelDataTypeFactory typeFactory) {
		IClass clazz;
		try {
			Collection<IClass> classes;
			classes = snapshot.getClassesByName(className, false /* include subclasses */);
			clazz = classes.iterator().next();
		} catch (SnapshotException e) {
			throw new IllegalStateException("Unable to find class " + className + " in snapshot");
		}

		Pair<RelDataType, List<Function<Integer, Object>>> typeAndResolvers;
		try {
			typeAndResolvers = ClassRowTypeCache.CACHE.get(typeFactory).get(
					clazz);
		} catch (ExecutionException e) {
			throw new IllegalStateException(
					"Unable to identify row type for class " + className);
		}
		this.resolvers = typeAndResolvers.right;

		return typeAndResolvers.left;

	}

}
