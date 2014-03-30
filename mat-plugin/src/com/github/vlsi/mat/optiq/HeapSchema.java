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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

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
		Builder<String, Table> builder = ImmutableMap.builder();
		for (IClass klass : classes) {
			String className = klass.getName();
			builder.put(className, new InstanceByClassTable(snapshot,
					className, false));
			builder.put("instanceof " + className, new InstanceByClassTable(
					snapshot, className, true));
		}
		tableMap = builder.build();
	}

	@Override
	protected Map<String, Table> getTableMap() {
		return tableMap;
	}
}
