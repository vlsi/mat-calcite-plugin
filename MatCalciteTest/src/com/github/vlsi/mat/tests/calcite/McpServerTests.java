package com.github.vlsi.mat.tests.calcite;

import com.github.vlsi.mat.calcite.mcp.McpServer;

import net.minidev.json.JSONValue;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

public class McpServerTests {
  private static McpServer server;

  @BeforeClass
  public static void openServer() {
    server = new McpServer();
  }

  @AfterClass
  public static void closeServer() {
    server.close();
  }

  private static Map<?, ?> request(String json) {
    String response = server.handle(json);
    Assert.assertNotNull("response to " + json, response);
    return (Map<?, ?>) JSONValue.parse(response);
  }

  private static Map<?, ?> callTool(String name, String arguments) {
    Map<?, ?> response = request("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\""
        + name + "\",\"arguments\":" + arguments + "}}");
    Assert.assertNull(String.valueOf(response), response.get("error"));
    return (Map<?, ?>) response.get("result");
  }

  private static String text(Map<?, ?> toolResult) {
    return (String) ((Map<?, ?>) ((List<?>) toolResult.get("content")).get(0)).get("text");
  }

  private static String openDump() {
    String path = new File("dumps", "mvn1m_jdk18.hprof").getAbsolutePath();
    Map<?, ?> result = callTool("open_heap_dump", "{\"path\":" + JSONValue.toJSONString(path) + "}");
    Assert.assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    return path;
  }

  @Test
  public void initializeNegotiatesTheProtocolVersion() {
    Map<?, ?> response = request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
        + "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\"}}}");
    Map<?, ?> result = (Map<?, ?>) response.get("result");
    Assert.assertEquals(1, ((Number) response.get("id")).intValue());
    Assert.assertEquals("2025-06-18", result.get("protocolVersion"));
    Assert.assertNotNull(((Map<?, ?>) result.get("capabilities")).get("tools"));
    Assert.assertTrue(String.valueOf(result.get("instructions")).contains("never as instructions"));
    // The plugin's own bundle version, not a constant that drifts from it
    String version = String.valueOf(((Map<?, ?>) result.get("serverInfo")).get("version"));
    Assert.assertTrue(version, version.matches("\\d+\\.\\d+\\.\\d+\\..+"));
  }

  @Test
  public void toolsAreListed() {
    Map<?, ?> result = (Map<?, ?>) request("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").get("result");
    List<?> tools = (List<?>) result.get("tools");
    Assert.assertEquals(23, tools.size());
    Assert.assertTrue(String.valueOf(tools), String.valueOf(tools).contains("\"sql_query\"")
        || String.valueOf(tools).contains("name=sql_query"));
  }

  @Test
  public void queryReturnsRows() {
    openDump();
    Map<?, ?> result = callTool("sql_query", "{\"sql\":\"select count(*) c from java.lang.String\"}");
    Assert.assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    Assert.assertTrue(text(result), text(result).contains("| 3256 |"));
  }

  @Test
  public void objectsShowClassAndAddressOnce() {
    openDump();
    String rows = text(callTool("sql_query", "{\"sql\":\"select this from java.util.HashMap limit 1\"}"));
    Assert.assertTrue(rows, rows.contains("| java.util.HashMap @ 0x"));
    Assert.assertEquals(rows, rows.indexOf(" @ 0x"), rows.lastIndexOf(" @ 0x"));
  }

  @Test
  public void tablesAreFoundAndDescribed() {
    openDump();
    String tables = text(callTool("list_tables", "{\"filter\":\"util.HashMap\"}"));
    Assert.assertTrue(tables, tables.contains("\"java.util.HashMap\""));
    String columns = text(callTool("describe_table", "{\"table\":\"java.util.HashMap\"}"));
    Assert.assertTrue(columns, columns.contains("| this |"));
    Assert.assertTrue(columns, columns.contains("| size |"));
  }

  @Test
  public void badSqlIsAToolErrorWithPosition() {
    openDump();
    Map<?, ?> result = callTool("sql_query", "{\"sql\":\"select nope from java.lang.String\"}");
    Assert.assertEquals(Boolean.TRUE, result.get("isError"));
    Assert.assertTrue(text(result), text(result).contains("line 1, column 8"));
  }

  /** Decimal address of the HashMap with the largest retained size. */
  private static String biggestHashMap() {
    openDump();
    String rows = text(callTool("sql_query", "{\"sql\":\"select getAddress(this) a from java.util.HashMap "
        + "order by retainedSize(this) desc limit 1\"}"));
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\| (\\d+) \\|").matcher(rows);
    Assert.assertTrue(rows, m.find());
    return m.group(1);
  }

  private static String okText(Map<?, ?> result) {
    Assert.assertEquals(text(result), Boolean.FALSE, result.get("isError"));
    return text(result);
  }

  @Test
  public void objectInfo() {
    String info = okText(callTool("object_info", "{\"address\":\"" + biggestHashMap() + "\"}"));
    Assert.assertTrue(info, info.contains("class: java.util.HashMap"));
    Assert.assertTrue(info, info.contains("retained size: "));
    Assert.assertTrue(info, info.contains("table = "));
  }

  @Test
  public void incomingReferences() {
    String refs = okText(callTool("list_references", "{\"address\":\"" + biggestHashMap()
        + "\",\"direction\":\"incoming\"}"));
    // the object itself, then at least one referrer indented below it
    Assert.assertTrue(refs, refs.contains("java.util.HashMap @ 0x"));
    Assert.assertTrue(refs, refs.contains("\n  "));
  }

  @Test
  public void retainedSet() {
    String set = okText(callTool("retained_set", "{\"address\":\"" + biggestHashMap() + "\"}"));
    Assert.assertTrue(set, set.contains("java.util.HashMap$Node"));
  }

  @Test
  public void pathToGcRoots() {
    String paths = okText(callTool("path_to_gc_roots", "{\"address\":\"" + biggestHashMap() + "\"}"));
    Assert.assertTrue(paths, paths.contains("java.util.HashMap @ 0x"));
    Assert.assertTrue(paths, paths.split("\n").length > 3);
  }

  @Test
  public void matQuery() {
    openDump();
    String histogram = okText(callTool("mat_query", "{\"command\":\"histogram\",\"max_rows\":2000}"));
    Assert.assertTrue(histogram, histogram.contains("java.lang.String"));
  }

  @Test
  public void dominatorOfRootDominatedObjectIsNull() {
    openDump();
    // Strings shared by several objects are dominated by the virtual root: getDominator gives null, not an error
    String rows = okText(callTool("sql_query", "{\"sql\":\"select count(*) c from java.lang.String "
        + "where getDominator(this) is null\"}"));
    Assert.assertFalse(rows, rows.contains("| 0 |"));
  }

  @Test
  public void hexAddresses() {
    openDump();
    String rows = okText(callTool("sql_query", "{\"sql\":\"select getHexAddress(this) h from java.util.HashMap "
        + "limit 1\"}"));
    Assert.assertTrue(rows, rows.matches("(?s).*\\| 0x[0-9a-f]+ \\|.*"));
  }

  @Test
  public void immediateDominatorsSkipNothingByDefault() {
    openDump();
    // The "file" protocol string of a classpath URL is dominated by its java.net.URL
    String rows = okText(callTool("sql_query", "{\"sql\":\"select getHexAddress(this) h from java.lang.String "
        + "where toString(this) = 'file' and getType(getDominator(this)) = 'java.net.URL' limit 1\"}"));
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(0x[0-9a-f]+)").matcher(rows);
    Assert.assertTrue(rows, m.find());
    String address = m.group(1);
    String direct = okText(callTool("immediate_dominators", "{\"address\":\"" + address + "\"}"));
    Assert.assertTrue(direct, direct.contains("| java.net.URL |"));
    String skipped = okText(callTool("immediate_dominators", "{\"address\":\"" + address
        + "\",\"skip\":\"java\\\\..*\"}"));
    Assert.assertFalse(skipped, skipped.contains("| java.net.URL |"));
  }

  @Test
  public void heapOverview() {
    openDump();
    String overview = okText(callTool("heap_overview", "{\"top\":5}"));
    Assert.assertTrue(overview, overview.contains("Number of objects"));
    Assert.assertTrue(overview, overview.contains("java.lang.Thread @ 0x"));
    Assert.assertTrue(overview, overview.contains("## Classes with the most shallow heap"));
  }

  @Test
  public void histogram() {
    openDump();
    String all = okText(callTool("histogram", "{\"max_rows\":5}"));
    Assert.assertTrue(all, all.contains("| class | objects | shallow heap |"));
    Assert.assertTrue(all, all.contains("sorted by shallow"));
    // Exact retained sizes for a few classes, sorted by them
    String maps = okText(callTool("histogram", "{\"filter\":\"^java\\\\.util\\\\.HashMap\",\"sort_by\":\"retained\"}"));
    Assert.assertTrue(maps, maps.contains("| java.util.HashMap | "));
    Assert.assertTrue(maps, maps.contains("retained heap |"));
    Assert.assertFalse(maps, maps.contains(">= "));
    Assert.assertFalse(maps, maps.contains("java.util.LinkedHashMap"));
    // More than 20 classes: MAT's lower bound, marked as such
    String bound = okText(callTool("histogram", "{\"filter\":\"^java\\\\.\",\"retained\":true}"));
    Assert.assertTrue(bound, bound.contains("| >= "));
    Assert.assertEquals(Boolean.TRUE, callTool("histogram", "{\"sort_by\":\"size\"}").get("isError"));
  }

  @Test
  public void leakSuspects() {
    openDump();
    String suspects = okText(callTool("leak_suspects", "{\"threshold_percent\":10}"));
    Assert.assertTrue(suspects, suspects.contains("java.lang.Thread @ 0x7bfe63df0  main"));
    Assert.assertTrue(suspects, suspects.contains("path from a GC root"));
    Assert.assertTrue(suspects, suspects.contains("class loader: "));
    // 728 classes have no common holder: their biggest members are listed instead
    Assert.assertTrue(suspects, suspects.contains("accumulation point: none"));
    Assert.assertTrue(suspects, suspects.contains("biggest instances:"));
    Assert.assertTrue(suspects, suspects.contains("thread stack of java.lang.Thread @ 0x7bfe63df0"));
    String none = okText(callTool("leak_suspects", "{\"threshold_percent\":90}"));
    Assert.assertTrue(none, none.startsWith("No leak suspect"));
  }

  @Test
  public void compareDumpsNeedsTwoDumps() {
    String path = openDump();
    Map<?, ?> result = callTool("compare_dumps", "{\"baseline\":" + JSONValue.toJSONString(path) + "}");
    Assert.assertEquals(Boolean.TRUE, result.get("isError"));
    Assert.assertTrue(text(result), text(result).contains("same heap dump"));
  }

  @Test
  public void collectionContents() {
    String contents = okText(callTool("collection_contents", "{\"address\":\"" + biggestHashMap()
        + "\",\"limit\":3}"));
    Assert.assertTrue(contents, contents.contains("size: 137"));
    Assert.assertTrue(contents, contents.contains(" => "));
    Assert.assertTrue(contents, contents.contains("call again with offset 3"));
  }

  @Test
  public void duplicateStrings() {
    openDump();
    String duplicates = okText(callTool("duplicate_strings", "{\"max_rows\":50}"));
    Assert.assertTrue(duplicates, duplicates.contains("| \"file\" | 43 |"));
  }

  @Test
  public void wastefulCollections() {
    openDump();
    String waste = okText(callTool("wasteful_collections", "{}"));
    Assert.assertTrue(waste, waste.contains("| java.util.HashMap |"));
    Assert.assertTrue(waste, waste.contains("## Most wasteful single collections"));
    // The sample dump uses compressed pointers: a reference slot is 4 bytes, not the 8-byte identifier size
    Assert.assertTrue(waste, waste.contains("unused slots x 4 bytes per reference"));
    String worst = waste.substring(waste.indexOf("## Most wasteful single collections"));
    long rows = java.util.Arrays.stream(worst.split("\n")).filter(line -> line.startsWith("| ")).count();
    Assert.assertEquals(worst, 1 + 10, rows); // the header and the 10 worst
  }

  private static Map<?, ?> export(String sql, String path, boolean overwrite) {
    return callTool("export_query", "{\"sql\":" + JSONValue.toJSONString(sql) + ",\"path\":"
        + JSONValue.toJSONString(path) + (overwrite ? ",\"overwrite\":true" : "") + "}");
  }

  @Test
  public void exportQuery() throws Exception {
    String dump = openDump();
    // Without MAT_MCP_EXPORT_DIR, exports go to the dump's directory
    File dir = new File(dump).getParentFile();
    File csv = new File(dir, "export-test.csv");
    csv.delete();
    try {
      String done = okText(export("select toString(this) s from java.lang.String", "export-test.csv", false));
      Assert.assertTrue(done, done.startsWith("Wrote 3256 rows"));
      Assert.assertEquals(3257, java.nio.file.Files.readAllLines(csv.toPath()).size());
      Assert.assertEquals("an existing file needs overwrite", Boolean.TRUE,
          export("select 1 x from java.lang.String", "export-test.csv", false).get("isError"));

      // A failing query leaves the existing file as it was, and no temporary file behind
      long before = csv.length();
      Assert.assertEquals(Boolean.TRUE, export("select no_such_column from java.lang.String", csv.getAbsolutePath(),
          true).get("isError"));
      Assert.assertEquals(before, csv.length());
      String[] left = dir.list((d, name) -> name.endsWith(".tmp"));
      Assert.assertEquals(java.util.Arrays.toString(left), 0, left.length);

      // Text a spreadsheet would run as a formula is made inert; numbers stay numbers
      okText(export("select '=HYPERLINK(1)' \"=f\", -5 n from java.lang.String limit 1", "export-test.csv", true));
      Assert.assertEquals("'=f,n", java.nio.file.Files.readAllLines(csv.toPath()).get(0));
      Assert.assertEquals("'=HYPERLINK(1),-5", java.nio.file.Files.readAllLines(csv.toPath()).get(1));
    } finally {
      csv.delete();
    }
  }

  @Test
  public void exportQueryWritesOnlyCsvInTheExportDirectory() throws Exception {
    openDump();
    File outside = File.createTempFile("outside", ".csv");
    try {
      for (String path : new String[]{outside.getAbsolutePath(), "../escape.csv", "sub/../../escape.csv"}) {
        Map<?, ?> refused = export("select 1 x from java.lang.String", path, true);
        Assert.assertEquals(path, Boolean.TRUE, refused.get("isError"));
        Assert.assertTrue(text(refused), text(refused).contains("writes only inside"));
      }
      Assert.assertEquals(0, outside.length());
      Map<?, ?> notCsv = export("select 1 x from java.lang.String", "notes.txt", true);
      Assert.assertTrue(text(notCsv), text(notCsv).contains("must end with .csv"));
    } finally {
      outside.delete();
    }
  }

  @Test
  public void matQueryRefusesCommandsThatWriteFiles() throws Exception {
    openDump();
    File target = new File(System.getProperty("java.io.tmpdir"), "mat-mcp-refused.hprof");
    target.delete();
    for (String command : new String[]{"export_hprof -output " + target.getAbsolutePath(),
        "create_report " + target.getAbsolutePath(), "default_report org.eclipse.mat.api:suspects",
        "delta_histogram -baseline " + target.getAbsolutePath()}) {
      Map<?, ?> refused = callTool("mat_query", "{\"command\":" + JSONValue.toJSONString(command) + "}");
      Assert.assertEquals(command, Boolean.TRUE, refused.get("isError"));
    }
    Assert.assertFalse("export_hprof must not have run", target.exists());
    Assert.assertTrue(okText(callTool("mat_query", "{\"command\":\"histogram\",\"max_rows\":3}"))
        .contains("java.lang"));
    Assert.assertTrue(text(callTool("mat_query", "{\"command\":\"no_such_command\"}"))
        .contains("Unknown MAT command"));
  }

  @Test
  public void aFailedOpenRemovesOnlyTheIndexFilesItCreated() throws Exception {
    java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("mat-mcp-open");
    try {
      java.nio.file.Path dump = dir.resolve("app.hprof");
      // A dump header followed by garbage: MAT starts indexing, then fails
      java.nio.file.Files.write(dump, ("JAVA PROFILE 1.0.2\0" + "\u0000\u0000\u0000\u0008garbage-garbage-garbage")
          .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
      // A file that is not one of MAT's must survive. (MAT itself deletes app.2.index, the index of a sibling dump
      // app.2.hprof, before indexing app.hprof: its pattern is ([A-Za-z0-9]{1,20}\\.)?index after "app.")
      java.nio.file.Files.write(dir.resolve("app.notes.txt"), new byte[]{1});
      Map<?, ?> result = callTool("open_heap_dump", "{\"path\":" + JSONValue.toJSONString(dump.toString()) + "}");
      Assert.assertEquals(text(result), Boolean.TRUE, result.get("isError"));
      String[] left = dir.toFile().list();
      java.util.Arrays.sort(left);
      Assert.assertEquals("[app.hprof, app.notes.txt]", java.util.Arrays.toString(left));
    } finally {
      for (File f : dir.toFile().listFiles()) {
        f.delete();
      }
      dir.toFile().delete();
    }
  }

  @Test
  public void badAddress() {
    openDump();
    Map<?, ?> result = callTool("object_info", "{\"address\":\"0x1\"}");
    Assert.assertEquals(Boolean.TRUE, result.get("isError"));
    Assert.assertTrue(text(result), text(result).contains("No object at address"));
  }

  @Test
  public void immediateMessages() {
    Assert.assertTrue(server.isImmediate("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
        + "\"params\":{\"requestId\":5}}"));
    Assert.assertTrue(server.isImmediate("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"));
    Assert.assertTrue(server.isImmediate("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
        + "\"params\":{\"name\":\"cancel_query\"}}"));
    Assert.assertFalse(server.isImmediate("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
        + "\"params\":{\"name\":\"sql_query\"}}"));
  }

  /** About 34 billion rows: never finishes on its own. */
  private static final String ENDLESS_QUERY = "select count(*) c from java.lang.String a, java.lang.String b, "
      + "java.lang.String c where getAddress(a.this) + getAddress(b.this) > getAddress(c.this)";

  /** Runs a tools/call on another thread, as the stdio worker does, and returns its result. */
  private static Map<?, ?> runInBackground(int id, String arguments, Runnable whileRunning) throws Exception {
    Map<?, ?>[] result = new Map<?, ?>[1];
    boolean[] interrupted = new boolean[1];
    Runnable call = () -> result[0] = (Map<?, ?>) ((Map<?, ?>) JSONValue.parse(server.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{\"name\":\"sql_query\","
            + "\"arguments\":" + arguments + "}}"))).get("result");
    Thread worker = new Thread(call) {
      @Override
      public void interrupt() {
        interrupted[0] = true;
        super.interrupt();
      }
    };
    worker.start();
    whileRunning.run();
    worker.join(60_000);
    Assert.assertFalse("the query did not stop", worker.isAlive());
    // An interrupt would close MAT's index files and could hit the next call: cancelling must not use one
    Assert.assertFalse("the worker thread was interrupted", interrupted[0]);
    return result[0];
  }

  @Test
  public void cancelStopsARunningQuery() throws Exception {
    openDump();
    long start = System.currentTimeMillis();
    Map<?, ?> result = runInBackground(101, "{\"sql\":\"" + ENDLESS_QUERY + "\",\"timeout_seconds\":120}", () -> {
      try {
        Thread.sleep(1500);
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
      Assert.assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
          + "\"params\":{\"requestId\":101,\"reason\":\"test\"}}"));
    });
    Assert.assertEquals(text(result), Boolean.TRUE, result.get("isError"));
    Assert.assertTrue(text(result), text(result).startsWith("Query cancelled"));
    Assert.assertTrue("took " + (System.currentTimeMillis() - start) + " ms",
        System.currentTimeMillis() - start < 30_000);
    // The cancel reached the running call and is done: a new call with the same id runs
    Assert.assertEquals(Boolean.FALSE,
        callToolWithId(101, "sql_query", "{\"sql\":\"select count(*) c from java.lang.String\"}").get("isError"));
  }

  private static Map<?, ?> callToolWithId(int id, String name, String arguments) {
    return (Map<?, ?>) request("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":"
        + "{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}").get("result");
  }

  @Test
  public void aCallCancelledWhileQueuedDoesNotRun() {
    openDump();
    // The client sent calls 201 and 202 in parallel and cancelled both while an earlier call ran
    Assert.assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
        + "\"params\":{\"requestId\":201,\"reason\":\"user interrupted\"}}"));
    Assert.assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
        + "\"params\":{\"requestId\":\"202\"}}"));
    Map<?, ?> skipped = callToolWithId(201, "sql_query", "{\"sql\":\"select count(*) c from java.lang.String\"}");
    Assert.assertEquals(text(skipped), Boolean.TRUE, skipped.get("isError"));
    Assert.assertTrue(text(skipped), text(skipped).contains("user interrupted"));
    // A string id is matched as a string; 202 as a number is another request
    Assert.assertEquals(Boolean.FALSE, callToolWithId(202, "list_heap_dumps", "{}").get("isError"));
    // Each cancel is used once: the same id later runs normally
    Assert.assertEquals(Boolean.FALSE,
        callToolWithId(201, "sql_query", "{\"sql\":\"select count(*) c from java.lang.String\"}").get("isError"));
  }

  @Test
  public void timeoutStopsARunningQuery() throws Exception {
    openDump();
    Map<?, ?> result = runInBackground(102, "{\"sql\":\"" + ENDLESS_QUERY + "\",\"timeout_seconds\":2}", () -> {
    });
    Assert.assertEquals(text(result), Boolean.TRUE, result.get("isError"));
    Assert.assertTrue(text(result), text(result).startsWith("Query timed out after 2 s"));
  }

  /** ^(.+)+\1! takes exponential time on a long class name, even in Java 21; the timeout must still stop it. */
  @Test
  public void timeoutStopsASlowHistogramFilter() {
    openDump();
    long start = System.nanoTime();
    Map<?, ?> result = callTool("histogram", "{\"filter\":\"^(.+)+\\\\1!\",\"timeout_seconds\":2}");
    Assert.assertEquals(text(result), Boolean.TRUE, result.get("isError"));
    Assert.assertTrue(text(result), text(result).contains("timed out"));
    Assert.assertTrue("took " + (System.nanoTime() - start) / 1_000_000 + " ms",
        System.nanoTime() - start < 30_000_000_000L);
  }

  @Test
  public void protocolErrors() {
    Map<?, ?> unknown = request("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"no/such\"}");
    Assert.assertEquals(-32601, ((Number) ((Map<?, ?>) unknown.get("error")).get("code")).intValue());
    Map<?, ?> broken = request("{not json");
    Assert.assertEquals(-32700, ((Number) ((Map<?, ?>) broken.get("error")).get("code")).intValue());
    Assert.assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
  }
}
