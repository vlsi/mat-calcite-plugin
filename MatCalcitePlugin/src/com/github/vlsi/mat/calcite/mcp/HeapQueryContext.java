package com.github.vlsi.mat.calcite.mcp;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.ContextDerivedData;
import org.eclipse.mat.query.annotations.Argument;
import org.eclipse.mat.query.registry.QueryContextImpl;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.query.RetainedSizeDerivedData;

import java.io.File;
import java.text.ParsePosition;

/**
 * Query context of a heap dump, for refining MAT query results ({@code RefinedResultBuilder}). MAT has the same thing
 * as {@code org.eclipse.mat.internal.snapshot.SnapshotQueryContext}, but that package is friend-restricted to MAT's
 * own UI, so this plugin keeps to public API. Command lines are parsed by {@code SnapshotQuery} with MAT's context;
 * this one does not parse arguments.
 */
final class HeapQueryContext extends QueryContextImpl {
  private final ISnapshot snapshot;

  HeapQueryContext(ISnapshot snapshot) {
    this.snapshot = snapshot;
  }

  @Override
  public File getPrimaryFile() {
    return new File(snapshot.getSnapshotInfo().getPath());
  }

  @Override
  public String getPrefix() {
    return snapshot.getSnapshotInfo().getPrefix();
  }

  @Override
  public String mapToExternalIdentifier(int objectId) throws SnapshotException {
    return "0x" + Long.toHexString(snapshot.mapIdToAddress(objectId));
  }

  @Override
  public int mapToObjectId(String externalIdentifier) throws SnapshotException {
    return snapshot.mapAddressToId(Long.parseUnsignedLong(externalIdentifier.substring(2), 16));
  }

  @Override
  public boolean available(Class<?> type, Argument.Advice advice) {
    if (type.isAssignableFrom(ISnapshot.class) && advice == null || type.isAssignableFrom(SnapshotInfo.class)) {
      return true;
    }
    return super.available(type, advice);
  }

  @Override
  public Object get(Class<?> type, Argument.Advice advice) {
    if (type.isAssignableFrom(ISnapshot.class) && advice == null) {
      return snapshot;
    }
    if (type.isAssignableFrom(SnapshotInfo.class)) {
      return snapshot.getSnapshotInfo();
    }
    return super.get(type, advice);
  }

  @Override
  public boolean parses(Class<?> type, Argument.Advice advice) {
    return false;
  }

  @Override
  public Object parse(Class<?> type, Argument.Advice advice, String[] args, ParsePosition pos) {
    throw new UnsupportedOperationException("Arguments are parsed by SnapshotQuery");
  }

  @Override
  public ContextDerivedData getContextDerivedData() {
    return new RetainedSizeDerivedData(snapshot);
  }
}
