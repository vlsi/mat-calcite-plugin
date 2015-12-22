package com.github.vlsi.mat.calcite;

import com.github.vlsi.mat.calcite.functions.TableFunctions;
import com.github.vlsi.mat.calcite.functions.HeapFunctions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

public class HeapSchema extends AbstractSchema
{
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
            IClassesList classOnly = new IClassesList(snapshot, className, false);
            IClassesList withSubClasses = new IClassesList(snapshot, className, true);

            builder.put(className, new InstanceByClassTable(classOnly));
            builder.put("instanceof " + className, new InstanceByClassTable(withSubClasses));

            // These are "index" tables that produce just object ids.
            // The tables are not supposed to be used by end-user
            builder.put("$ids$:" + className, new InstanceIdsByClassTable(classOnly));
            builder.put("$ids$:instanceof " + className, new InstanceIdsByClassTable(withSubClasses));
        }
        tableMap = builder.build();

        ImmutableMultimap.Builder<String, Function> b = ImmutableMultimap.builder();
        b.putAll(ScalarFunctionImpl.createAll(HeapFunctions.class));
        b.putAll(TableFunctions.createAll());
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
