package com.github.vlsi.mat.optiq;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import net.hydromatic.optiq.SchemaPlus;
import net.hydromatic.optiq.Table;
import net.hydromatic.optiq.impl.AbstractSchema;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

public class HeapSchema extends AbstractSchema {
	private Map<String, Table> tableMap;

	public HeapSchema(SchemaPlus parentSchema, String name,
			final ISnapshot snapshot, HeapSchema prototype) {
		super(parentSchema, name);

		if (prototype != null) {
			this.tableMap = prototype.tableMap;
			return;
		}

		Collection<IClass> classes;
		try {
			classes = snapshot.getClasses();
		} catch (SnapshotException e) {
			e.printStackTrace();
			classes = Collections.emptyList();
		}
		tableMap = FluentIterable.from(classes)
				.transform(new Function<IClass, String>() {
					@Override
					public String apply(IClass input) {
						return input.getName();
					}
				}).toMap(new Function<String, Table>() {
					@Override
					public Table apply(String input) {
						return new InstanceByClassTable(snapshot, input, false);
					}
				});
	}

	@Override
	protected Map<String, Table> getTableMap() {
		return tableMap;
	}
}
