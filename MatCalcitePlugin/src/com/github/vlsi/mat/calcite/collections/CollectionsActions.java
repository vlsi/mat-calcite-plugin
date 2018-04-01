package com.github.vlsi.mat.calcite.collections;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

public class CollectionsActions {

    private static class MapExtractorInfo {
        final String className;
        final IMapExtractor extractor;

        MapExtractorInfo(String className, IMapExtractor extractor) {
            this.className = className;
            this.extractor = extractor;
        }
    }

    private static final MapExtractorInfo[] knownExtractors = new MapExtractorInfo[]
            {
                    new MapExtractorInfo("com.github.andrewoma.dexx.collection.HashMap", new DexxHashMapCollectionExtractor()),
                    new MapExtractorInfo("vlsi.utils.CompactHashMap", new CompactHashMapCollectionExtractor())
            };

    public static ExtractedMap extractMap(IObject object) throws SnapshotException {
        IClass clazz = object.getClazz();
        for (MapExtractorInfo info : knownExtractors) {
            if (clazz.doesExtend(info.className)) {
                return CollectionExtractionUtils.extractMap(object, info.className, info.extractor);
            }
        }
        return CollectionExtractionUtils.extractMap(object);
    }
}
