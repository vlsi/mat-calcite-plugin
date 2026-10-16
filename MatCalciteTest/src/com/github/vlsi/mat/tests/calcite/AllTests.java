package com.github.vlsi.mat.tests.calcite;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    BasicQueriesTests.class,
    GetByKeyTests.class,
    TableFunctionsTests.class,
    CollectionsFunctionsTests.class,
    RowSetTableTests.class,
    SqlForObjectsTests.class,
    McpServerTests.class,
    DateFunctionsTests.class,
    QueryCancellationTests.class
})
public class AllTests {
}
