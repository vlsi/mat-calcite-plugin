package com.github.vlsi.mat.calcite;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.IClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds SQL that selects the objects chosen in another MAT view: a whole class for a class row (e.g. in the
 * histogram), otherwise the objects themselves, by address. Objects of several classes give one statement per
 * class, since their columns differ.
 */
public final class SqlForObjects {
  /** Keeps the statement small enough to edit and fast enough to plan. */
  public static final int MAX_OBJECTS_PER_CLASS = 1000;

  private SqlForObjects() {
  }

  public static String build(ISnapshot snapshot, List<? extends IContextObject> elements) throws SnapshotException {
    Set<String> wholeClasses = new LinkedHashSet<>();
    Map<String, List<Long>> addressesByClass = new LinkedHashMap<>();
    Map<String, Integer> countByClass = new LinkedHashMap<>();

    for (IContextObject element : elements) {
      int id = element.getObjectId();
      int[] ids = element instanceof IContextObjectSet ? ((IContextObjectSet) element).getObjectIds() : new int[]{id};
      if (element instanceof IContextObjectSet && id >= 0 && snapshot.isClass(id)) {
        IClass cls = (IClass) snapshot.getObject(id);
        // A class row of the full histogram stands for all its instances. A row of a partial histogram (retained
        // set, grouped dominator tree...) stands for some of them only, so those are selected by address.
        if (ids.length == cls.getNumberOfObjects()) {
          wholeClasses.add(cls.getName());
          continue;
        }
      }
      for (int objectId : ids) {
        if (objectId < 0) {
          continue;
        }
        String className = snapshot.getClassOf(objectId).getName();
        int count = countByClass.merge(className, 1, Integer::sum);
        if (count <= MAX_OBJECTS_PER_CLASS) {
          addressesByClass.computeIfAbsent(className, k -> new ArrayList<>())
              .add(snapshot.mapIdToAddress(objectId));
        }
      }
    }

    List<String> statements = new ArrayList<>();
    for (String className : wholeClasses) {
      statements.add("select *\n  from " + SqlIdentifiers.quote(className) + "\n");
    }
    for (Map.Entry<String, List<Long>> entry : addressesByClass.entrySet()) {
      String className = entry.getKey();
      StringBuilder sb = new StringBuilder();
      int total = countByClass.get(className);
      if (total > MAX_OBJECTS_PER_CLASS) {
        sb.append("-- First ").append(MAX_OBJECTS_PER_CLASS).append(" of ").append(total).append(" objects\n");
      }
      sb.append("select *\n  from ").append(SqlIdentifiers.quote(className))
          .append("\n where getAddress(this) in (");
      List<Long> addresses = entry.getValue();
      for (int i = 0; i < addresses.size(); i++) {
        if (i > 0) {
          sb.append(i % 8 == 0 ? ",\n    " : ", ");
        }
        sb.append(addresses.get(i));
      }
      sb.append(")\n");
      statements.add(sb.toString());
    }

    if (statements.isEmpty()) {
      throw new SnapshotException("No heap objects selected");
    }
    if (statements.size() == 1) {
      return statements.get(0);
    }
    return "-- Objects of several classes: select one statement and press F5 to run it\n\n"
        + String.join("\n", statements);
  }
}
