package com.github.vlsi.mat.calcite.neo;

import org.apache.calcite.linq4j.Enumerator;

import java.util.NoSuchElementException;

public abstract class GroupEnumerator<GroupType, RowsType, ResultType> implements Enumerator<ResultType> {
  private final GroupType[] groups;
  private RowsType rows;
  private int rowsCount;

  private int currentRow = -1;
  private int currentGroup = -1;

  private ResultType currentResult;

  public GroupEnumerator(GroupType[] groups) {
    this.groups = groups;
  }

  @Override
  public ResultType current() {
    if (currentResult == null) {
      throw new NoSuchElementException();
    } else {
      return currentResult;
    }
  }

  @Override
  public boolean moveNext() {
    do {
      if (advanceRow()) {
        return true;
      }
    } while (advanceGroup());
    return false;
  }

  @Override
  public void reset() {
    currentRow = -1;
    currentGroup = -1;
    currentResult = null;
  }

  @Override
  public void close() {
    reset();
  }

  private boolean advanceRow() {
    if (currentGroup == -1) {
      return false;
    } else if (currentRow < rowsCount - 1) {
      currentRow++;
      resolveRow();
      return true;
    } else {
      return false;
    }
  }

  private boolean advanceGroup() {
    if (currentGroup < groups.length - 1) {
      currentGroup++;
      currentRow = -1;
      resolveGroup();
      return true;
    } else {
      currentResult = null;
      return false;
    }
  }

  private void resolveGroup() {
    try {
      rows = resolveGroup(groups[currentGroup]);
      rowsCount = rowsCount(rows);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void resolveRow() {
    try {
      currentResult = resolveRow(groups[currentGroup], rows, currentRow);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract int rowsCount(RowsType rows);

  protected abstract RowsType resolveGroup(GroupType group) throws Exception;

  protected abstract ResultType resolveRow(GroupType group, RowsType rows, int currentRow) throws Exception;
}
