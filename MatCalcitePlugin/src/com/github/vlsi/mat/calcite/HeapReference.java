package com.github.vlsi.mat.calcite;

import com.github.vlsi.mat.calcite.functions.HeapFunctions;

import org.eclipse.mat.snapshot.model.IArray;
import org.eclipse.mat.snapshot.model.IObject;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class HeapReference implements Comparable<HeapReference>, Map {
  private final IObject o;

  public HeapReference(IObject o) {
    this.o = o;
  }

  public static HeapReference valueOf(IObject o) {
    return o == null ? null : new HeapReference(o);
  }

  public IObject getIObject() {
    return o;
  }

  @Override
  public boolean equals(Object o1) {
    if (this == o1) {
      return true;
    }
    if (o1 == null || getClass() != o1.getClass()) {
      return false;
    }

    HeapReference that = (HeapReference) o1;

    return o.equals(that.o);
  }

  @Override
  public int hashCode() {
    return o.hashCode();
  }

  @Override
  public String toString() {
    String classSpecific = o.getClassSpecificName();
    if (classSpecific != null) {
      return classSpecific;
    }
    return o.getDisplayName();
  }

  /**
   * Orders heap objects by address. Calcite must see one order everywhere: its sorts compare heap objects through
   * {@link #entrySet} (they are Maps), while its merge join compares the same keys with this method, and two
   * different orders made the merge join skip matches. Address order is also cheap: Calcite builds a new
   * HeapReference each time a comparison reads a row, so ordering by label rebuilt the label (a String's text) on
   * every comparison, and ORDER BY this on 4 million objects took minutes. ORDER BY toString(this) gives label
   * order, computing one label per row.
   */
  @Override
  public int compareTo(HeapReference other) {
    return Long.compare(o.getObjectAddress(), other.o.getObjectAddress());
  }

  @Override
  public Object get(Object key) {
    // This Map.get is called by Calcite when this['fieldA'] SQL syntax is used
    if (key == null) {
      return null;
    }
    if (key instanceof Number && getIObject() instanceof IArray) {
      return HeapFunctions.getField(this, "[" + key + "]");
    }
    String fieldName = String.valueOf(key);
    if (fieldName.charAt(0) == '@') {
      if ("@shallow".equalsIgnoreCase(fieldName)) {
        return HeapFunctions.shallowSize(this);
      }
      if ("@retained".equalsIgnoreCase(fieldName)) {
        return HeapFunctions.retainedSize(this);
      }
    }
    return HeapFunctions.getField(this, fieldName);
  }

  @Override
  public int size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmpty() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsKey(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsValue(Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object put(Object key, Object value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set keySet() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection values() {
    throw new UnsupportedOperationException();
  }

  /**
   * One entry whose value orders like {@link #compareTo}, for Calcite. HeapReference is not a real Map: it is a
   * view of one heap object, a Map only so that this['field'] maps onto {@link #get}. But Calcite sorts Map values
   * (ORDER BY this, MIN(this), the inputs of a merge join) with Functions.compareMaps, which compares the entries in
   * turn. An empty set made every two objects equal: ORDER BY this did not sort, and merge joins dropped matches.
   * The value must not be a Map itself, or Calcite would compare it as one again; a Comparable of a single class
   * makes it call {@link SortKey#compareTo}.
   */
  @Override
  public Set<Entry> entrySet() {
    return Collections.singleton(new AbstractMap.SimpleImmutableEntry<>("@this", new SortKey(this)));
  }

  /** The order of {@link HeapReference#compareTo}, as a value Calcite compares with compareTo. */
  private static final class SortKey implements Comparable<SortKey> {
    private final HeapReference ref;

    SortKey(HeapReference ref) {
      this.ref = ref;
    }

    @Override
    public int compareTo(SortKey other) {
      return ref.compareTo(other.ref);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof SortKey && ref.equals(((SortKey) other).ref);
    }

    @Override
    public int hashCode() {
      return ref.hashCode();
    }
  }
}
