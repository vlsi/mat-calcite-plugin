package com.github.vlsi.mat.calcite.functions;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;

@SuppressWarnings("unused")
public abstract class IClassMethods {
  public static IClass getSuper(IObject clazz) {
    if (clazz instanceof IClass) {
      return ((IClass) clazz).getSuperClass();
    }
    return null;
  }

  public static IObject getClassLoader(IObject clazz) {
    if (!(clazz instanceof IClass)) {
      return null;
    }
    int classLoaderId = ((IClass) clazz).getClassLoaderId();
    try {
      return clazz.getSnapshot().getObject(classLoaderId);
    } catch (SnapshotException e) {
      throw new IllegalArgumentException(
          "Unable to retrieve classloader of class " + clazz + " in heap " + clazz.getSnapshot(), e);
    }
  }

  public static String getClassName(IObject clazz) {
    if (clazz instanceof IClass) {
      return ((IClass) clazz).getName();
    }
    return null;
  }
}
