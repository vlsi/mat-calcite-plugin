package com.github.vlsi.mat.optiq;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

import java.util.Collection;

public class IClassesList {
    public final ISnapshot snapshot;
    public final String className;
    public final boolean includeSubClasses;
    private long totalObjects = -1;

    public IClassesList(ISnapshot snapshot, String className, boolean includeSubClasses) {
        this.snapshot = snapshot;
        this.className = className;
        this.includeSubClasses = includeSubClasses;
    }

    public Collection<IClass> getClasses() {
        return getClasses(false);
    }

    public IClass getFirstClass() {
        return getClasses(true).iterator().next();
    }

    public double getTotalObjects() {
        if (totalObjects >= 0)
            return totalObjects;
        long rows = 0;
        for (IClass iClass : getClasses()) {
            rows += iClass.getNumberOfObjects();
        }
        return totalObjects = rows;
    }

    private Collection<IClass> getClasses(boolean justFirstItem) {
        Collection<IClass> classesByName;
        try {
            classesByName = snapshot.getClassesByName(className,
                    includeSubClasses && !justFirstItem);
        } catch (SnapshotException e) {
            throw new IllegalStateException("Unable to get class " + className);
        }
        return classesByName;
    }
}
