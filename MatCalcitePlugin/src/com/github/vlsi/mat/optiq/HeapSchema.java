package com.github.vlsi.mat.optiq;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.hydromatic.optiq.Function;
import net.hydromatic.optiq.Table;
import net.hydromatic.optiq.impl.AbstractSchema;
import net.hydromatic.optiq.impl.ScalarFunctionImpl;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

public class HeapSchema extends AbstractSchema {
    private Map<String, Table> tableMap;
    private Multimap<String, Function> functionMultimap;

    public HeapSchema(final ISnapshot snapshot) {
        Collection<IClass> classes;
        try {
            classes = snapshot.getClasses();
        } catch (SnapshotException e) {
            e.printStackTrace();
            classes = Collections.emptyList();
        }
        Builder<String, Table> builder = ImmutableMap.builder();
        HashSet<String> knownClasses = new HashSet<String>((int) (classes.size() / 0.75f));
        for (IClass klass : classes) {
            String className = klass.getName();
            if (!knownClasses.add(className))
                continue;
            builder.put(className, new InstanceByClassTable(snapshot,
                    className, false));
            builder.put("instanceof " + className, new InstanceByClassTable(
                    snapshot, className, true));
        }
        tableMap = builder.build();

        ImmutableMultimap.Builder<String, Function> b = ImmutableMultimap.builder();
        b.putAll(ScalarFunctionImpl.createAll(HeapFunctions.class));
        functionMultimap = b.build();
    }

    @Override
    protected Multimap<String, Function> getFunctionMultimap() {
        return functionMultimap;
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return tableMap;
    }
}
