package com.github.vlsi.mat.calcite.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IObjectArray;

import java.util.*;
import java.util.Map.Entry;

public class CompactHashMapCollectionExtractor implements IMapExtractor {
    @Override
    public boolean hasSize() {
        return true;
    }

    @Override
    public Integer getSize(IObject iObject) throws SnapshotException {
        // TODO more efficient
        int size = 0;
        for(Iterator<Entry<IObject, IObject>> it = extractMapEntries(iObject); it.hasNext(); it.next()) {
            size++;
        }
        return size;
    }

    @Override
    public boolean hasExtractableContents() {
        return true;
    }

    @Override
    public Iterator<Map.Entry<IObject, IObject>> extractMapEntries(IObject iObject) throws SnapshotException {
        List<Entry<IObject, IObject>> result = new ArrayList<>();

        ISnapshot snapshot = iObject.getSnapshot();
        IObject v1 = (IObject)iObject.resolveValue("v1");
        IObject v2 = (IObject)iObject.resolveValue("v2");
        IObject v3 = (IObject)iObject.resolveValue("v3");
        HashSet<String> explicitNames = new HashSet<>(); // Keys explicitly set in key2slot map

        IObject mapKlass = (IObject) iObject.resolveValue("klass");
        for(Entry<IObject, IObject> entry : CollectionsActions.extractMap((IObject) mapKlass.resolveValue("key2slot"))) {
            IObject key = entry.getKey();
            IObject value;

            int slot = (Integer) entry.getValue().resolveValue("value");
            switch (slot) {
                case -1:
                    value = v1;
                    break;
                case -2:
                    value = v2;
                    break;
                case -3:
                    value = v3;
                    break;
                default:
                    value = getObject(snapshot, ((IObjectArray)v1).getReferenceArray()[slot]);
            }

            result.add(new IObjectsPair(key, value));
            explicitNames.add(toString(key));
        }

        // This is not entirely correct, as we are comparing String representation of the keys instead of real keys,
        // but it's best what we can do here
        if (getClassName(mapKlass).equals("vlsi.utils.CompactHashMapClassWithDefaults")) {
            for(Entry<IObject, IObject> entry : CollectionsActions.extractMap((IObject) mapKlass.resolveValue("defaultValues"))) {
                IObject key = entry.getKey();
                IObject value = entry.getValue();

                if (!explicitNames.contains(toString(key))) {
                    result.add(new IObjectsPair(key, value));
                }
            }
        }

        return result.iterator();
    }

    // Internal

    private String getClassName(IObject obj) {
        return obj.getClazz().getName();
    }

    private IObject getObject(ISnapshot snapshot, long address) throws SnapshotException {
        return address ==0 ? null : snapshot.getObject(snapshot.mapAddressToId(address));
    }

    private String toString(IObject object) {
        String name = object.getClassSpecificName();
        return name != null ? name : object.getDisplayName();
    }

    // Not implemented

    @Override
    public boolean hasCollisionRatio() {
        return false;
    }

    @Override
    public Double getCollisionRatio(IObject iObject) throws SnapshotException {
        return null;
    }

    @Override
    public boolean hasCapacity() {
        return false;
    }

    @Override
    public Integer getCapacity(IObject iObject) throws SnapshotException {
        return null;
    }

    @Override
    public boolean hasFillRatio() {
        return false;
    }

    @Override
    public Double getFillRatio(IObject iObject) throws SnapshotException {
        return null;
    }

    @Override
    public int[] extractEntryIds(IObject iObject) throws SnapshotException {
        return new int[0];
    }

    @Override
    public boolean hasExtractableArray() {
        return false;
    }

    @Override
    public IObjectArray extractEntries(IObject iObject) throws SnapshotException {
        return null;
    }

    @Override
    public Integer getNumberOfNotNullElements(IObject iObject) throws SnapshotException {
        return null;
    }
}
