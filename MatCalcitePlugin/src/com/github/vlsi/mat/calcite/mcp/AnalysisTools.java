package com.github.vlsi.mat.calcite.mcp;

import com.github.vlsi.mat.calcite.CalciteDataSource;
import com.github.vlsi.mat.calcite.HeapReference;
import com.github.vlsi.mat.calcite.QueryCancellation;
import com.github.vlsi.mat.calcite.mcp.McpServer.ToolException;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.collect.HashMapLongObject;
import org.eclipse.mat.inspections.collectionextract.AbstractExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.CollectionExtractionUtils;
import org.eclipse.mat.inspections.collectionextract.ExtractedCollection;
import org.eclipse.mat.inspections.collectionextract.ExtractedMap;
import org.eclipse.mat.inspections.collectionextract.ICollectionExtractor;
import org.eclipse.mat.inspections.collectionextract.IMapExtractor;
import org.eclipse.mat.query.Bytes;
import org.eclipse.mat.query.ContextProvider;
import org.eclipse.mat.query.IContextObject;
import org.eclipse.mat.query.IContextObjectSet;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.IResultTable;
import org.eclipse.mat.snapshot.ClassHistogramRecord;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.SnapshotInfo;
import org.eclipse.mat.snapshot.model.GCRootInfo;
import org.eclipse.mat.snapshot.model.IClass;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IStackFrame;
import org.eclipse.mat.snapshot.model.IThreadStack;
import org.eclipse.mat.snapshot.model.PrettyPrinter;
import org.eclipse.mat.snapshot.query.SnapshotQuery;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;

/**
 * Higher-level analyses for {@link McpServer}: overview, leak suspects, dump comparison, collection contents, CSV
 * export, duplicate strings and wasteful collections. They answer the common heap questions in one call each, and
 * their results carry addresses for the object tools.
 */
final class AnalysisTools {
  /** Longest string value read for duplicate detection; longer strings are compared on this prefix. */
  private static final int MAX_STRING_LENGTH = 100_000;
  private static final int MAX_VALUE_SHOWN = 100;

  private AnalysisTools() {
  }

  // ---------------------------------------------------------------- heap_overview

  static String heapOverview(ISnapshot snapshot, int top, Cancellation cancellation) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("## Heap\n\n")
        .append(run(snapshot, "heap_dump_overview", new MatResultRenderer(50, 0, 1), cancellation)).append("\n\n");
    sb.append("## Biggest objects (top of the dominator tree, by retained heap)\n\n")
        .append(run(snapshot, "dominator_tree", new MatResultRenderer(top, 0, top), cancellation)).append("\n\n");
    sb.append("## Classes with the most shallow heap\n\n")
        .append(run(snapshot, "histogram", new MatResultRenderer(top, 0, 1).sortedBy("Shallow Heap"), cancellation))
        .append("\n\n");
    sb.append("Next: leak_suspects, duplicate_strings, wasteful_collections, or path_to_gc_roots on an address above.");
    return sb.toString();
  }

  // ---------------------------------------------------------------- leak_suspects

  /**
   * Uses only public API: MAT's find_leaks result is an {@code IResultTable} whose columns are suspect, number of
   * objects, retained heap, share, accumulation point and its size and share, and whose context providers give the
   * suspect (a set for a group of objects) and the accumulation point. ({@code FindLeaksQuery} itself lives in a
   * friend-restricted package.)
   */
  static String leakSuspects(ISnapshot snapshot, int thresholdPercent, int maxSuspects, Cancellation cancellation)
      throws Exception {
    IResult result = SnapshotQuery.lookup("find_leaks", snapshot)
        .setArgument("threshold_percent", thresholdPercent)
        .execute(cancellation.listener());
    long total = snapshot.getSnapshotInfo().getUsedHeapSize();
    if (!(result instanceof IResultTable) || ((IResultTable) result).getRowCount() == 0) {
      return "No leak suspect retains more than " + thresholdPercent + "% of the heap (" + total + " bytes). "
          + "Lower threshold_percent, or look at heap_overview for the biggest objects.";
    }
    IResultTable table = (IResultTable) result;
    List<ContextProvider> contexts = table.getResultMetaData() == null ? null
        : table.getResultMetaData().getContextProviders();
    StringBuilder sb = new StringBuilder();
    sb.append(table.getRowCount()).append(table.getRowCount() == 1 ? " suspect" : " suspects")
        .append(" retaining more than ").append(thresholdPercent).append("% of the heap (").append(total)
        .append(" bytes):\n");
    for (int i = 0; i < Math.min(maxSuspects, table.getRowCount()); i++) {
      Object row = table.getRow(i);
      long objects = number(table.getColumnValue(row, 1));
      long retained = number(table.getColumnValue(row, 2));
      IContextObject suspect = contexts != null && contexts.size() > 0 ? contexts.get(0).getContext(row)
          : table.getContext(row);
      IContextObject point = contexts != null && contexts.size() > 1 ? contexts.get(1).getContext(row) : null;
      sb.append("\n## Suspect ").append(i + 1).append(": ");
      boolean group = suspect instanceof IContextObjectSet && objects > 1;
      IObject first = snapshot.getObject(suspect.getObjectId());
      if (group) {
        sb.append(objects).append(" instances of ").append(className(first));
      } else {
        sb.append(first.getDisplayName());
      }
      sb.append("\nretains ").append(retained).append(" bytes (").append(percent(retained, total))
          .append(" of the heap)\n");
      sb.append("class loader: ").append(classLoader(snapshot, first)).append('\n');
      IObject accumulation = point != null && point.getObjectId() >= 0 ? snapshot.getObject(point.getObjectId())
          : null;
      if (accumulation != null) {
        sb.append("accumulation point (where the memory piles up): ").append(accumulation.getDisplayName())
            .append(", retains ").append(snapshot.getRetainedHeapSize(point.getObjectId())).append(" bytes\n");
      } else if (group) {
        sb.append("accumulation point: none, no single object holds the group; each instance is kept alive on "
            + "its own\n");
      }
      if (group) {
        appendBiggest(sb, snapshot, ((IContextObjectSet) suspect).getObjectIds());
      } else {
        sb.append("path from a GC root:\n").append(pathToRoot(snapshot, suspect.getObjectId(), cancellation));
      }
      if (accumulation != null && point.getObjectId() != suspect.getObjectId()) {
        sb.append("path from a GC root to the accumulation point (who holds the memory):\n")
            .append(pathToRoot(snapshot, point.getObjectId(), cancellation));
      }
      // A thread's memory is held by its stack: show which frames and local variables hold it
      IObject thread = isThread(first) ? first : accumulation != null && isThread(accumulation) ? accumulation : null;
      if (thread != null) {
        appendThreadStack(sb, snapshot, thread);
      }
    }
    sb.append("\nNext: retained_set or collection_contents on an accumulation point, path_to_gc_roots on a suspect.");
    return sb.toString();
  }

  private static final int BIGGEST_INSTANCES = 5;
  private static final int MAX_FRAMES = 40;
  private static final int LOCALS_PER_FRAME = 3;
  private static final int MAX_LABEL = 120;

  private static String classLoader(ISnapshot snapshot, IObject object) throws SnapshotException {
    IClass clazz = object instanceof IClass ? (IClass) object : object.getClazz();
    int loader = clazz.getClassLoaderId();
    return loader < 0 ? "(unknown)" : snapshot.getObject(loader).getDisplayName();
  }

  /**
   * The members of a group suspect that retain the most, as starting points for path_to_gc_roots. A group can
   * have millions of members, so only the biggest few are kept while scanning.
   */
  private static void appendBiggest(StringBuilder sb, ISnapshot snapshot, int[] ids) throws SnapshotException {
    PriorityQueue<long[]> biggest = new PriorityQueue<>(Comparator.comparingLong((long[] s) -> s[0]));
    for (int id : ids) {
      checkCancelled();
      long retained = snapshot.getRetainedHeapSize(id);
      if (biggest.size() < BIGGEST_INSTANCES || retained > biggest.peek()[0]) {
        biggest.add(new long[] {retained, id});
        if (biggest.size() > BIGGEST_INSTANCES) {
          biggest.poll();
        }
      }
    }
    List<long[]> shown = new ArrayList<>(biggest);
    shown.sort(Comparator.comparingLong((long[] s) -> s[0]).reversed());
    sb.append("biggest instances:\n");
    for (long[] s : shown) {
      sb.append("  ").append(label(snapshot.getObject((int) s[1]))).append(", retains ").append(s[0])
          .append(" bytes\n");
    }
    if (ids.length > shown.size()) {
      sb.append("  ... ").append(ids.length - shown.size()).append(" more\n");
    }
  }

  /** The shortest strong path from a GC root, or a note when weak or soft references are the only holders. */
  private static String pathToRoot(ISnapshot snapshot, int id, Cancellation cancellation) throws Exception {
    String path = run(snapshot, "path2gc -numberOfPaths 1 " + hex(snapshot, id), new MatResultRenderer(30, 30, 1),
        cancellation);
    GCRootInfo[] roots = snapshot.getGCRootInfo(id);
    if (path.trim().endsWith("\n1 row") && (roots == null || roots.length == 0)) {
      path += "\n(no strong path: only weak, soft or finalizer references keep it; the JVM can free it under memory "
          + "pressure; list_references incoming shows what references it)";
    }
    return path + "\n";
  }

  /** The object's label, cut short: the label of a byte[] or char[] holds its contents. */
  private static String label(IObject object) {
    String name = object.getDisplayName();
    return name.length() > MAX_LABEL ? name.substring(0, MAX_LABEL) + "..." : name;
  }

  private static boolean isThread(IObject object) {
    if (object instanceof IClass) {
      return false;
    }
    for (IClass c = object.getClazz(); c != null; c = c.getSuperClass()) {
      if ("java.lang.Thread".equals(c.getName())) {
        return true;
      }
    }
    return false;
  }

  /** The thread's stack, each frame with the local variables that retain the most. */
  private static void appendThreadStack(StringBuilder sb, ISnapshot snapshot, IObject thread)
      throws SnapshotException {
    IThreadStack stack = snapshot.getThreadStack(thread.getObjectId());
    if (stack == null || stack.getStackFrames() == null || stack.getStackFrames().length == 0) {
      sb.append("thread stack: not in the dump\n");
      return;
    }
    IStackFrame[] frames = stack.getStackFrames();
    long threadRetained = snapshot.getRetainedHeapSize(thread.getObjectId());
    sb.append("thread stack of ").append(label(thread)).append(", with the local variables that retain at least 1% "
        + "of the thread's ").append(threadRetained).append(" bytes:\n");
    int shown = Math.min(MAX_FRAMES, frames.length);
    for (int f = 0; f < shown; f++) {
      sb.append("  ").append(frames[f].getText()).append('\n');
      int[] locals = frames[f].getLocalObjectsIds();
      if (locals == null || locals.length == 0) {
        continue;
      }
      // Each object once, and only those that hold a noticeable part of what the thread retains
      List<long[]> sizes = new ArrayList<>();
      for (int local : java.util.Arrays.stream(locals).distinct().toArray()) {
        long size = snapshot.getRetainedHeapSize(local);
        if (size * 100 >= threadRetained) {
          sizes.add(new long[] {size, local});
        }
      }
      sizes.sort((a, b) -> Long.compare(b[0], a[0]));
      for (int i = 0; i < Math.min(LOCALS_PER_FRAME, sizes.size()); i++) {
        sb.append("      local ").append(label(snapshot.getObject((int) sizes.get(i)[1]))).append(", retains ")
            .append(sizes.get(i)[0]).append(" bytes\n");
      }
      if (sizes.size() > LOCALS_PER_FRAME) {
        sb.append("      ... ").append(sizes.size() - LOCALS_PER_FRAME).append(" more locals\n");
      }
    }
    if (frames.length > shown) {
      sb.append("  ... ").append(frames.length - shown).append(" more frames\n");
    }
  }

  /** A numeric cell of a MAT result, which holds sizes as {@link Bytes}. */
  private static long number(Object value) {
    if (value instanceof Bytes) {
      return ((Bytes) value).getValue();
    }
    return value instanceof Number ? ((Number) value).longValue() : 0;
  }

  private static String className(IObject object) {
    return object instanceof IClass ? ((IClass) object).getName() : object.getClazz().getName();
  }

  // ---------------------------------------------------------------- histogram

  /** Classes for which an exact retained size is computed; above this, MAT's quick lower bound is used. */
  private static final int PRECISE_RETAINED_CLASSES = 20;

  /**
   * The class histogram: objects, shallow heap and, when asked, retained heap per class.
   *
   * @param filter   regular expression found in the class name (or, if it is not one, plain text); empty for all
   * @param sortBy   objects, shallow or retained
   * @param retained compute retained heap even when not sorting by it
   */
  static String histogram(ISnapshot snapshot, String filter, String sortBy, boolean retained, int maxRows,
                          Cancellation cancellation) throws Exception {
    String sort = sortBy == null || sortBy.trim().isEmpty() ? "shallow" : sortBy.trim().toLowerCase(Locale.ROOT);
    if (!Arrays.asList("objects", "shallow", "retained").contains(sort)) {
      throw new ToolException("sort_by must be objects, shallow or retained, got " + sortBy);
    }
    java.util.function.Predicate<String> matches = classNameFilter(filter);
    List<ClassHistogramRecord> records = new ArrayList<>();
    for (ClassHistogramRecord record : snapshot.getHistogram(cancellation.listener()).getClassHistogramRecords()) {
      if (record.getNumberOfObjects() > 0 && matches.test(record.getLabel())) {
        records.add(record);
      }
    }
    boolean withRetained = retained || "retained".equals(sort);
    boolean precise = records.size() <= PRECISE_RETAINED_CLASSES;
    Map<ClassHistogramRecord, Long> retainedSizes = new HashMap<>();
    if (withRetained) {
      for (ClassHistogramRecord record : records) {
        if (cancellation.isCancelled()) {
          throw new CancellationException(cancellation.reason());
        }
        // MAT marks a lower bound with a negative value
        retainedSizes.put(record, Math.abs(record.calculateRetainedSize(snapshot, true, !precise,
            cancellation.listener())));
      }
    }
    Comparator<ClassHistogramRecord> order = "objects".equals(sort)
        ? Comparator.comparingLong(ClassHistogramRecord::getNumberOfObjects)
        : "retained".equals(sort) ? Comparator.comparingLong(retainedSizes::get)
        : Comparator.comparingLong(ClassHistogramRecord::getUsedHeapSize);
    records.sort(order.reversed().thenComparing(ClassHistogramRecord::getLabel));

    long objects = 0;
    long shallow = 0;
    for (ClassHistogramRecord record : records) {
      objects += record.getNumberOfObjects();
      shallow += record.getUsedHeapSize();
    }
    StringBuilder sb = new StringBuilder();
    sb.append(records.size()).append(records.size() == 1 ? " class" : " classes").append(" with objects")
        .append(filter == null || filter.trim().isEmpty() ? "" : " matching " + filter.trim()).append(": ")
        .append(objects).append(" objects, ").append(shallow).append(" bytes shallow (")
        .append(percent(shallow, snapshot.getSnapshotInfo().getUsedHeapSize())).append(" of the heap), sorted by ")
        .append(sort).append("\n\n");
    sb.append("| class | objects | shallow heap |").append(withRetained ? " retained heap |" : "").append('\n')
        .append("|---|---|---|").append(withRetained ? "---|" : "").append('\n');
    int shown = Math.min(maxRows, records.size());
    for (int i = 0; i < shown; i++) {
      ClassHistogramRecord record = records.get(i);
      sb.append("| ").append(record.getLabel().replace("|", "\\|")).append(" | ").append(record.getNumberOfObjects())
          .append(" | ").append(record.getUsedHeapSize()).append(" |");
      if (withRetained) {
        sb.append(' ').append(precise ? "" : ">= ").append(retainedSizes.get(record)).append(" |");
      }
      sb.append('\n');
    }
    sb.append('\n').append(shown).append(" of ").append(records.size()).append(" classes shown")
        .append(records.size() > shown ? " (raise max_rows)" : "");
    if (withRetained && !precise) {
      sb.append("\n\">=\" retained sizes are MAT's quick lower bound, used above ").append(PRECISE_RETAINED_CLASSES)
          .append(" classes; narrow the filter for exact ones.");
    }
    sb.append("\n\nNext: sql_query on a class (its table name is the class name in double quotes), or "
        + "retained_set on an object's address.");
    return sb.toString();
  }

  private static java.util.function.Predicate<String> classNameFilter(String filter) {
    if (filter == null || filter.trim().isEmpty()) {
      return name -> true;
    }
    try {
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(filter.trim());
      return name -> pattern.matcher(new CancellableText(name)).find();
    } catch (java.util.regex.PatternSyntaxException e) {
      String text = filter.trim();
      return name -> name.contains(text);
    }
  }

  /**
   * Text that checks for cancellation as a regular expression reads it. A pattern such as (a+)+b can take
   * exponential time on one long class name, and class names come from the dump; this lets the timeout stop it.
   */
  private static final class CancellableText implements CharSequence {
    private final String text;

    CancellableText(String text) {
      this.text = text;
    }

    @Override
    public int length() {
      return text.length();
    }

    @Override
    public char charAt(int index) {
      checkCancelled();
      return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return new CancellableText(text.substring(start, end));
    }

    @Override
    public String toString() {
      return text;
    }
  }

  // ---------------------------------------------------------------- compare_dumps

  static String compareDumps(ISnapshot current, ISnapshot baseline, String baselinePath, int maxRows,
                             Cancellation cancellation) throws Exception {
    if (baselinePath.contains(" ")) {
      throw new ToolException("compare_dumps cannot pass a baseline path with spaces to MAT yet: " + baselinePath);
    }
    SnapshotInfo now = current.getSnapshotInfo();
    SnapshotInfo before = baseline.getSnapshotInfo();
    StringBuilder sb = new StringBuilder();
    sb.append("| | baseline | current | change |\n|---|---|---|---|\n")
        .append(row("objects", before.getNumberOfObjects(), now.getNumberOfObjects()))
        .append(row("classes", before.getNumberOfClasses(), now.getNumberOfClasses()))
        .append(row("used heap (bytes)", before.getUsedHeapSize(), now.getUsedHeapSize()))
        .append("\n## Classes that grew the most (shallow heap change, current minus baseline)\n\n");
    // The query opens the baseline from its path and disposes that reference again, so the open one stays open
    sb.append(run(current, "delta_histogram -baseline " + baselinePath,
        new MatResultRenderer(maxRows, 0, 1).sortedBy("Shallow Heap"), cancellation));
    sb.append("\n\nNext: sql_query or list_tables on a growing class, then path_to_gc_roots on one of its objects.");
    return sb.toString();
  }

  private static String row(String what, long before, long now) {
    long change = now - before;
    return "| " + what + " | " + before + " | " + now + " | " + (change > 0 ? "+" : "") + change + " |\n";
  }

  // ---------------------------------------------------------------- collection_contents

  static String collectionContents(ISnapshot snapshot, String address, int offset, int limit) throws Exception {
    int[] ids = ObjectTools.objectIds(snapshot, address);
    if (ids.length != 1) {
      throw new ToolException("collection_contents takes one address, got " + ids.length);
    }
    IObject object = snapshot.getObject(ids[0]);
    AbstractExtractedCollection<?, ?> collection = CollectionExtractionUtils.extractCollection(object);
    if (collection == null) {
      throw new ToolException(object.getDisplayName() + " is not a collection MAT can read. For arrays and plain "
          + "objects use object_info.");
    }
    StringBuilder sb = new StringBuilder(object.getDisplayName()).append('\n');
    if (collection.hasSize()) {
      sb.append("size: ").append(collection.size()).append('\n');
    }
    if (collection.hasCapacity()) {
      sb.append("capacity: ").append(collection.getCapacity()).append('\n');
    }
    if (collection.hasFillRatio() && collection.getFillRatio() != null) {
      sb.append("fill ratio: ").append(String.format(Locale.ROOT, "%.2f", collection.getFillRatio())).append('\n');
    }
    if (collection instanceof ExtractedMap && ((ExtractedMap) collection).hasCollisionRatio()) {
      Double collisions = ((ExtractedMap) collection).getCollisionRatio();
      if (collisions != null) {
        sb.append("collision ratio: ").append(String.format(Locale.ROOT, "%.2f", collisions)).append('\n');
      }
    }
    int index = 0;
    int shown = 0;
    boolean more = false;
    if (collection instanceof ExtractedMap) {
      sb.append("entries (key => value):\n");
      for (Map.Entry<IObject, IObject> entry : (ExtractedMap) collection) {
        checkCancelled();
        if (index++ < offset) {
          continue;
        }
        if (shown == limit) {
          more = true;
          break;
        }
        sb.append("  [").append(index - 1).append("] ").append(describe(entry.getKey())).append(" => ")
            .append(describe(entry.getValue())).append('\n');
        shown++;
      }
    } else if (collection instanceof ExtractedCollection && collection.hasExtractableContents()) {
      sb.append("elements:\n");
      for (IObject element : (ExtractedCollection) collection) {
        checkCancelled();
        if (index++ < offset) {
          continue;
        }
        if (shown == limit) {
          more = true;
          break;
        }
        sb.append("  [").append(index - 1).append("] ").append(describe(element)).append('\n');
        shown++;
      }
    } else {
      sb.append("MAT cannot list the contents of this collection type.\n");
    }
    if (more) {
      sb.append("  ... more: call again with offset ").append(offset + shown).append('\n');
    }
    return sb.toString();
  }

  private static String describe(IObject object) {
    if (object == null) {
      return "null";
    }
    String text = object.getDisplayName();
    return text.length() > 300 ? text.substring(0, 300) + "..." : text;
  }

  // ---------------------------------------------------------------- export_query

  /**
   * Writes a query result to a CSV file inside {@code exportDir}. The assistant chooses the path but also reads heap
   * contents, which may hold text written to steer it, so the file must be a .csv inside that one directory. The
   * result goes to a temporary file first and replaces the target only when complete, so a failed or cancelled
   * export leaves an existing file as it was.
   */
  static String exportQuery(ISnapshot snapshot, File exportDir, String sql, String path, boolean overwrite,
                            Cancellation cancellation) throws Exception {
    File dir = exportDir.getCanonicalFile();
    if (!dir.isDirectory()) {
      throw new ToolException("Export directory does not exist: " + dir);
    }
    File requested = new File(path);
    File file = (requested.isAbsolute() ? requested : new File(dir, path)).getCanonicalFile();
    if (!file.toPath().startsWith(dir.toPath()) || file.equals(dir)) {
      throw new ToolException("export_query writes only inside " + dir + " (set MAT_MCP_EXPORT_DIR to change it); "
          + "pass a file name such as strings.csv");
    }
    if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new ToolException("The file name must end with .csv: " + file.getName());
    }
    if (!file.getParentFile().isDirectory()) {
      throw new ToolException("Directory does not exist: " + file.getParent());
    }
    if (file.exists() && !overwrite) {
      throw new ToolException("File exists: " + file + " (pass overwrite: true to replace it)");
    }
    Path temp = Files.createTempFile(file.getParentFile().toPath(), "." + file.getName() + ".", ".tmp");
    long rows = 0;
    int columns;
    try {
      try (Connection con = CalciteDataSource.getConnection(snapshot);
           Statement st = con.createStatement()) {
        cancellation.setStatement(st);
        try (ResultSet rs = st.executeQuery(sql);
             BufferedWriter out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
          ResultSetMetaData md = rs.getMetaData();
          columns = md.getColumnCount();
          for (int i = 1; i <= columns; i++) {
            out.write((i > 1 ? "," : "") + csv(inert(md.getColumnLabel(i))));
          }
          out.write('\n');
          while (rs.next()) {
            for (int i = 1; i <= columns; i++) {
              Object value = rs.getObject(i);
              String text = value == null ? "" : value instanceof Number ? value.toString()
                  : inert(value instanceof HeapReference ? ((HeapReference) value).getIObject().getDisplayName()
                  : String.valueOf(value));
              out.write((i > 1 ? "," : "") + csv(text));
            }
            out.write('\n');
            rows++;
          }
        }
      }
      if (cancellation.isCancelled()) {
        throw new CancellationException(cancellation.reason());
      }
      try {
        Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
    return "Wrote " + rows + (rows == 1 ? " row" : " rows") + " of " + columns + " columns to " + file + " ("
        + file.length() + " bytes).";
  }

  /**
   * Text that a spreadsheet would run as a formula (=, +, -, @, or a leading tab or carriage return) gets a leading
   * apostrophe, so opening the CSV cannot run anything taken from the heap. Numbers are written as they are.
   */
  private static String inert(String text) {
    if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
      return "'" + text;
    }
    return text;
  }

  private static String csv(String text) {
    if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
      return text;
    }
    return '"' + text.replace("\"", "\"\"") + '"';
  }

  // ---------------------------------------------------------------- duplicate_strings

  /**
   * Counts string values by a 64-bit hash, not by the text: keeping every distinct text would copy all the dump's
   * strings into this JVM, which runs out of memory on a large dump. A hash costs the same for any length, and the
   * text of the values shown is read again from an example object. Two values share a hash with odds of about
   * n^2 / 2^65 for n distinct values, negligible here.
   */
  static String duplicateStrings(ISnapshot snapshot, int minCopies, int maxRows) throws Exception {
    // value hash -> {copies, retained sum, min retained, sample id}
    HashMapLongObject<long[]> values = new HashMapLongObject<>();
    long strings = 0;
    long retainedTotal = 0;
    for (IClass stringClass : snapshot.getClassesByName("java.lang.String", false)) {
      for (int id : stringClass.getObjectIds()) {
        checkCancelled();
        String value = PrettyPrinter.objectAsString(snapshot.getObject(id), MAX_STRING_LENGTH);
        if (value == null) {
          continue;
        }
        long retained = snapshot.getRetainedHeapSize(id);
        strings++;
        retainedTotal += retained;
        long hash = hash64(value);
        long[] stats = values.get(hash);
        if (stats == null) {
          values.put(hash, new long[]{1, retained, retained, id});
        } else {
          stats[0]++;
          stats[1] += retained;
          stats[2] = Math.min(stats[2], retained);
        }
      }
    }
    List<long[]> duplicates = new ArrayList<>();
    long duplicatedStrings = 0;
    long wastedTotal = 0;
    for (Iterator<long[]> it = values.values(); it.hasNext(); ) {
      long[] stats = it.next();
      if (stats[0] >= minCopies) {
        duplicates.add(stats);
        duplicatedStrings += stats[0];
        wastedTotal += stats[1] - stats[2];
      }
    }
    duplicates.sort(Comparator.comparingLong((long[] stats) -> stats[1] - stats[2]).reversed());
    StringBuilder sb = new StringBuilder();
    sb.append("strings: ").append(strings).append(" (retaining ").append(retainedTotal).append(" bytes)\n")
        .append("distinct values: ").append(values.size()).append('\n')
        .append("values with at least ").append(minCopies).append(" copies: ").append(duplicates.size())
        .append(", covering ").append(duplicatedStrings).append(" strings\n")
        .append("wasted by the extra copies: ").append(wastedTotal).append(" bytes (")
        .append(percent(wastedTotal, retainedTotal)).append(" of what strings retain, ")
        .append(percent(wastedTotal, snapshot.getSnapshotInfo().getUsedHeapSize())).append(" of the heap)\n\n");
    sb.append("| value | copies | wasted bytes | example |\n|---|---|---|---|\n");
    for (int i = 0; i < Math.min(maxRows, duplicates.size()); i++) {
      long[] stats = duplicates.get(i);
      String value = PrettyPrinter.objectAsString(snapshot.getObject((int) stats[3]), MAX_STRING_LENGTH);
      sb.append("| ").append(cell(value == null ? "" : value)).append(" | ").append(stats[0]).append(" | ")
          .append(stats[1] - stats[2]).append(" | ").append(hex(snapshot, (int) stats[3])).append(" |\n");
    }
    if (duplicates.size() > maxRows) {
      sb.append("\n").append(maxRows).append(" of ").append(duplicates.size()).append(" values shown (raise max_rows)");
    }
    sb.append("\n\n\"wasted\" is what all copies but the smallest retain. Next: list_references (incoming) on an "
        + "example address to see who creates the copies.");
    return sb.toString();
  }

  /**
   * Bytes per object reference: 4 with compressed pointers (the default for heaps up to 32 GB), else the dump's
   * identifier size. MAT records compressed pointers as the snapshot property $useCompressedOops.
   */
  private static int referenceSize(ISnapshot snapshot) {
    Object compressed = snapshot.getSnapshotInfo().getProperty("$useCompressedOops");
    return Boolean.TRUE.equals(compressed) || "true".equals(String.valueOf(compressed)) ? 4
        : snapshot.getSnapshotInfo().getIdentifierSize();
  }

  /** String.hashCode in the high half and FNV-1a in the low half: two independent 32-bit hashes. */
  private static long hash64(String value) {
    int fnv = 0x811c9dc5;
    for (int i = 0; i < value.length(); i++) {
      fnv = (fnv ^ value.charAt(i)) * 0x01000193;
    }
    return ((long) value.hashCode() << 32) | (fnv & 0xffffffffL);
  }

  /** A string value for a markdown cell: shortened, with control characters escaped. */
  private static String cell(String value) {
    StringBuilder sb = new StringBuilder("\"");
    int length = Math.min(value.length(), MAX_VALUE_SHOWN);
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      if (c == '|') {
        sb.append("\\|");
      } else if (c == '\n') {
        sb.append("\\n");
      } else if (c == '\r') {
        sb.append("\\r");
      } else if (c == '\t') {
        sb.append("\\t");
      } else if (c < ' ') {
        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
      } else {
        sb.append(c);
      }
    }
    sb.append(value.length() > MAX_VALUE_SHOWN ? "...\" (" + value.length() + " chars)" : "\"");
    return sb.toString();
  }

  // ---------------------------------------------------------------- wasteful_collections

  /** Per class statistics of collections. */
  private static final class CollectionStats {
    final String className;
    long count;
    long empty;
    long emptyRetained;
    double fillSum;
    long fillCount;
    long lowFill;
    long unusedSlots;
    double collisionSum;
    long collisionCount;

    CollectionStats(String className) {
      this.className = className;
    }

    long waste(int referenceSize) {
      return emptyRetained + unusedSlots * referenceSize;
    }
  }

  /** One collection with its estimated waste, for the list of worst ones. */
  private static final class Wasteful {
    final int id;
    final long waste;
    final String why;

    Wasteful(int id, long waste, String why) {
      this.id = id;
      this.waste = waste;
      this.why = why;
    }
  }

  /** The single collections listed: only this many are kept while scanning, however many there are. */
  private static final int WORST_SHOWN = 10;

  static String wastefulCollections(ISnapshot snapshot, int maxRows) throws Exception {
    int referenceSize = referenceSize(snapshot);
    List<CollectionStats> classes = new ArrayList<>();
    // The least wasteful of those kept is at the head, ready to be replaced
    PriorityQueue<Wasteful> worst = new PriorityQueue<>(Comparator.comparingLong((Wasteful w) -> w.waste));
    for (IClass clazz : snapshot.getClasses()) {
      if (clazz.isArrayType() || clazz.getNumberOfObjects() == 0) {
        continue;
      }
      int[] ids = clazz.getObjectIds();
      ICollectionExtractor extractor = CollectionExtractionUtils.findCollectionExtractor(snapshot.getObject(ids[0]));
      if (extractor == null || !extractor.hasSize()) {
        continue;
      }
      CollectionStats stats = new CollectionStats(clazz.getName());
      boolean maps = extractor instanceof IMapExtractor && ((IMapExtractor) extractor).hasCollisionRatio();
      for (int id : ids) {
        checkCancelled();
        IObject collection = snapshot.getObject(id);
        Integer size;
        Integer capacity;
        try {
          size = extractor.getSize(collection);
          capacity = extractor.hasCapacity() ? extractor.getCapacity(collection) : null;
          if (size == null) {
            continue;
          }
          stats.count++;
          long waste = 0;
          String why = null;
          if (size == 0) {
            long retained = snapshot.getRetainedHeapSize(id);
            stats.empty++;
            stats.emptyRetained += retained;
            waste += retained;
            why = "empty";
          }
          if (capacity != null && capacity > 0) {
            double fill = size / (double) capacity;
            stats.fillSum += fill;
            stats.fillCount++;
            if (fill < 0.5) {
              stats.lowFill++;
            }
            if (capacity > size) {
              stats.unusedSlots += capacity - size;
              waste += (long) (capacity - size) * referenceSize;
              if (why == null) {
                why = size + " of " + capacity + " slots used";
              }
            }
          }
          if (maps && size > 0) {
            Double collisions = ((IMapExtractor) extractor).getCollisionRatio(collection);
            if (collisions != null) {
              stats.collisionSum += collisions;
              stats.collisionCount++;
            }
          }
          if (waste > 0 && (worst.size() < WORST_SHOWN || waste > worst.peek().waste)) {
            worst.add(new Wasteful(id, waste, why));
            if (worst.size() > WORST_SHOWN) {
              worst.poll();
            }
          }
        } catch (SnapshotException | RuntimeException e) {
          // a collection MAT cannot read in this dump: skip it
        }
      }
      if (stats.count > 0) {
        classes.add(stats);
      }
    }
    if (classes.isEmpty()) {
      return "No collections found that MAT can read.";
    }
    classes.sort(Comparator.comparingLong((CollectionStats s) -> s.waste(referenceSize)).reversed());
    List<Wasteful> worstFirst = new ArrayList<>(worst);
    worstFirst.sort(Comparator.comparingLong((Wasteful w) -> w.waste).reversed());
    long totalWaste = 0;
    for (CollectionStats stats : classes) {
      totalWaste += stats.waste(referenceSize);
    }
    StringBuilder sb = new StringBuilder();
    sb.append("estimated waste: ").append(totalWaste).append(" bytes (")
        .append(percent(totalWaste, snapshot.getSnapshotInfo().getUsedHeapSize())).append(" of the heap) = what "
        + "empty collections retain + unused slots x ").append(referenceSize).append(" bytes per reference\n\n");
    sb.append("| class | collections | empty | empty retain (bytes) | avg fill | under 50% full | unused slots "
        + "| avg collision ratio | est. waste (bytes) |\n|---|---|---|---|---|---|---|---|---|\n");
    for (int i = 0; i < Math.min(maxRows, classes.size()); i++) {
      CollectionStats s = classes.get(i);
      sb.append("| ").append(s.className).append(" | ").append(s.count).append(" | ").append(s.empty).append(" | ")
          .append(s.emptyRetained).append(" | ")
          .append(s.fillCount == 0 ? "" : String.format(Locale.ROOT, "%.2f", s.fillSum / s.fillCount)).append(" | ")
          .append(s.fillCount == 0 ? "" : String.valueOf(s.lowFill)).append(" | ")
          .append(s.fillCount == 0 ? "" : String.valueOf(s.unusedSlots)).append(" | ")
          .append(s.collisionCount == 0 ? "" : String.format(Locale.ROOT, "%.2f", s.collisionSum / s.collisionCount))
          .append(" | ").append(s.waste(referenceSize)).append(" |\n");
    }
    if (classes.size() > maxRows) {
      sb.append("\n").append(maxRows).append(" of ").append(classes.size()).append(" collection classes shown\n");
    }
    sb.append("\n## Most wasteful single collections\n\n| collection | est. waste (bytes) | why |\n|---|---|---|\n");
    for (Wasteful w : worstFirst) {
      sb.append("| ").append(describe(snapshot.getObject(w.id))).append(" | ").append(w.waste).append(" | ")
          .append(w.why).append(" |\n");
    }
    sb.append("\nNext: collection_contents or path_to_gc_roots on one of these addresses.");
    return sb.toString();
  }

  // ---------------------------------------------------------------- helpers

  private static String run(ISnapshot snapshot, String commandLine, MatResultRenderer renderer,
                            Cancellation cancellation) throws Exception {
    return renderer.render(snapshot, SnapshotQuery.parse(commandLine, snapshot).execute(cancellation.listener()));
  }

  private static String hex(ISnapshot snapshot, int id) throws SnapshotException {
    return "0x" + Long.toHexString(snapshot.mapIdToAddress(id));
  }

  private static String percent(long part, long whole) {
    return whole == 0 ? "0%" : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
  }

  /** The long analyses check for cancellation the way the heap object scan does. */
  private static void checkCancelled() {
    QueryCancellation.check();
  }
}
