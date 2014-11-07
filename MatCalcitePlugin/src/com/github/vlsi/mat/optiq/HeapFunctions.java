package com.github.vlsi.mat.optiq;

public class HeapFunctions {
    public static int get_id(Object r) {
        if (r == null || !(r instanceof HeapReference)) return -1;
        return ((HeapReference) r).getIObject().getObjectId();
    }

    public static String toString(Object r) {
        if (r == null) return null;
        return r.toString();
    }
}
