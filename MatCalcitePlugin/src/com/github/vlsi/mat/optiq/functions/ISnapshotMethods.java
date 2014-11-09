package com.github.vlsi.mat.optiq.functions;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;

public class ISnapshotMethods {
    public static long getShallowSize(ISnapshot snapshot, int id) {
        try {
            return snapshot.getHeapSize(id);
        } catch (SnapshotException e) {
            throw new IllegalArgumentException("Unable to get shallow size of object " + id + " in heap " + String.valueOf(snapshot), e);
        }
    }

    public static long getRetainedSize(ISnapshot snapshot, int id) {
        try {
            return snapshot.getRetainedHeapSize(id);
        } catch (SnapshotException e) {
            throw new IllegalArgumentException("Unable to get retained size of object " + id + " in heap " + String.valueOf(snapshot), e);
        }
    }

    public static IObject getIObject(ISnapshot snapshot, int id) {
        try {
            return snapshot.getObject(id);
        } catch (SnapshotException e) {
            throw new IllegalArgumentException("Unable to get object " + id + " in heap " + String.valueOf(snapshot), e);
        }
    }
}
