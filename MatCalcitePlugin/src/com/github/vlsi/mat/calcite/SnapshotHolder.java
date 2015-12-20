package com.github.vlsi.mat.calcite;

import org.eclipse.mat.snapshot.ISnapshot;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SnapshotHolder {
    private static final List<Reference<ISnapshot>> SNAPSHOTS = new CopyOnWriteArrayList<Reference<ISnapshot>>();

    public static ISnapshot get(int index) {
        return SNAPSHOTS.get(index).get();
    }

    public static synchronized int put(ISnapshot snapshot) {
        for (int i = 0; i < SNAPSHOTS.size(); i++) {
            Reference<ISnapshot> ref = SNAPSHOTS.get(i);
            if (ref.get() == snapshot)
                return i;
        }
        SNAPSHOTS.add(new WeakReference<ISnapshot>(snapshot));
        return SNAPSHOTS.size() - 1;
    }
}
