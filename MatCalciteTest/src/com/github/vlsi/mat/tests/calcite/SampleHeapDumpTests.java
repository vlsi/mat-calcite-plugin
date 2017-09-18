package com.github.vlsi.mat.tests.calcite;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.snapshot.ISnapshot;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.File;

public abstract class SampleHeapDumpTests extends AbstractQueriesTests {
    protected static ISnapshot snapshot;

    @BeforeClass
    public static void openSnapshot() throws SnapshotException {
        snapshot = openSnapshot(new File("dumps", "mvn1m_jdk18.hprof"));
    }

    @AfterClass
    public static void closeSnapshot() {
        closeSnapshot(snapshot);
        snapshot = null;
    }

    protected ISnapshot getSnapshot() {
        return snapshot;
    }
}