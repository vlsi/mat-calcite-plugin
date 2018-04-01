package com.github.vlsi.mat.calcite.functions;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.github.vlsi.mat.calcite.collections.CollectionsActions;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.linq4j.BaseQueryable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.QueryableTable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.TableFunction;
import org.apache.calcite.schema.impl.TableFunctionImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.ImmutableBitSet;
import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.util.VoidProgressListener;

import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.schema.references.OutboundReferencesTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

public class TableFunctions {

    public static Multimap<String, TableFunction> createAll() {
        ImmutableMultimap.Builder<String, TableFunction> builder = ImmutableMultimap.builder();
        builder.put("getValues", TableFunctionImpl.create(TableFunctions.class, "getValues"));
        builder.put("getMapEntries", TableFunctionImpl.create(TableFunctions.class, "getMapEntries"));
        builder.put("getRetainedSet", TableFunctionImpl.create(TableFunctions.class, "getRetainedSet"));
        builder.put("getOutboundReferences", TableFunctionImpl.create(TableFunctions.class, "getOutboundReferences"));
        builder.put("getInboundReferences", TableFunctionImpl.create(TableFunctions.class, "getInboundReferences"));
        return builder.build();
    }

    @SuppressWarnings("unused")
    public static QueryableTable getValues(Object r) {
        List<HeapReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference) r;
            try {
                ICollectionExtractor collectionExtractor = CollectionExtractionUtils.findCollectionExtractor(ref.getIObject());
                if (collectionExtractor == null) {
                    references = Collections.emptyList();
                } else {
                    references = collectReferences(ref.getIObject().getSnapshot(), collectionExtractor.extractEntryIds(ref.getIObject()));
                }
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract values from " + ref, e);
            }
        }
        return new HeapReferenceTable(references, false);
    }

    @SuppressWarnings("unused")
    public static QueryableTable getMapEntries(Object r) {
        List<HeapReference[]> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference) r;
            try {
                ExtractedMap extractedMap = CollectionsActions.extractMap(ref.getIObject());
                if (extractedMap == null) {
                    references = Collections.emptyList();
                } else {
                    references = new ArrayList<>();
                    for (Map.Entry<IObject, IObject> entry : extractedMap) {
                        references.add(new HeapReference[]{
                                HeapReference.valueOf(entry.getKey()),
                                HeapReference.valueOf(entry.getValue())
                        });
                    }
                }
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract values from " + ref, e);
            }
        }
        return new HeapReferencesTable(new String[]{"key", "value"}, references);
    }

    @SuppressWarnings("unused")
    public static QueryableTable getRetainedSet(Object r) {
        List<HeapReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference) r;
            ISnapshot snapshot = ref.getIObject().getSnapshot();
            try {
                references = collectReferences
                        (
                                snapshot,
                                snapshot.getRetainedSet(new int[]{ref.getIObject().getObjectId()}, new VoidProgressListener())
                        );
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract retained set from " + r, e);
            }
        }
        return new HeapReferenceTable(references, true);
    }

    @SuppressWarnings("unused")
    public static QueryableTable getOutboundReferences(Object r) {
        List<NamedReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference) r;
            references = ref.getIObject().getOutboundReferences();
        }
        return new OutboundReferencesTable(references);
    }

    @SuppressWarnings("unused")
    public static QueryableTable getInboundReferences(Object r) {
        List<HeapReference> references;
        if (!(r instanceof HeapReference)) {
            references = Collections.emptyList();
        } else {
            HeapReference ref = (HeapReference) r;
            ISnapshot snapshot = ref.getIObject().getSnapshot();
            try {
                references = collectReferences
                        (
                                snapshot,
                                snapshot.getInboundRefererIds(ref.getIObject().getObjectId())
                        );
            } catch (SnapshotException e) {
                throw new RuntimeException("Cannot extract inbound references from " + r, e);
            }
        }
        return new HeapReferenceTable(references, true);
    }

    private static List<HeapReference> collectReferences(ISnapshot snapshot, int[] objectIds) throws SnapshotException {
        if (objectIds != null && objectIds.length > 0) {
            List<HeapReference> references = new ArrayList<>();
            for (int objectId : objectIds) {
                references.add(HeapReference.valueOf(snapshot.getObject(objectId)));
            }
            return references;
        } else {
            return Collections.emptyList();
        }
    }

    private static class HeapReferenceTable extends ValuesListTable<HeapReference> {
        HeapReferenceTable(Collection<HeapReference> references, boolean unique) {
            super(HeapReference.class, new String[]{"this"}, references, unique);
        }
    }

    private static class HeapReferencesTable extends ValuesListTable<HeapReference[]> {
        HeapReferencesTable(String[] columnNames, Collection<HeapReference[]> references) {
            super(HeapReference[].class, columnNames, references, false);
        }
    }

    private static class ValuesListTable<RowType> extends AbstractQueryableTable {
        private final static List<ImmutableBitSet> UNIQUE_KEYS_STATISTICS = ImmutableList.of(ImmutableBitSet.of(0));
        private final static List<ImmutableBitSet> NON_UNIQUE_KEYS_STATISTICS = ImmutableList.of(ImmutableBitSet.of());

        private final String[] columnNames;
        private final Collection<RowType> values;
        private final boolean unique;

        ValuesListTable(Type targetRowType, String[] columnNames, Collection<RowType> values, boolean unique) {
            super(targetRowType);
            this.columnNames = columnNames;
            this.values = values;
            this.unique = unique;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schemaPlus, String s) {
            BaseQueryable<RowType> queryable = new BaseQueryable<RowType>(null, getElementType(), null) {
                @Override
                public Enumerator<RowType> enumerator() {
                    return Linq4j.enumerator(values);
                }
            };
            return (Queryable<T>) queryable;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory relDataTypeFactory) {
            RelDataTypeFactory.FieldInfoBuilder builder = relDataTypeFactory.builder();
            for (String columnName : columnNames) {
                builder.add(columnName, relDataTypeFactory.createSqlType(SqlTypeName.ANY));
            }
            return builder.build();
        }

        @Override
        public Statistic getStatistic() {
            return Statistics.of(values.size(), unique ? UNIQUE_KEYS_STATISTICS : NON_UNIQUE_KEYS_STATISTICS);
        }
    }
}

