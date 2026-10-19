package com.github.vlsi.mat.calcite.editor;

import com.github.vlsi.mat.calcite.CalciteDataSource;
import com.github.vlsi.mat.calcite.SchemaCatalog;

import static com.github.vlsi.mat.calcite.SchemaCatalog.IDS_PREFIX;
import static com.github.vlsi.mat.calcite.SchemaCatalog.INSTANCEOF;
import static com.github.vlsi.mat.calcite.SchemaCatalog.NATIVE;
import static com.github.vlsi.mat.calcite.SqlIdentifiers.quote;
import static com.github.vlsi.mat.calcite.SqlIdentifiers.quotePath;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.query.Icons;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Tree of the tables, columns and functions that SQL queries can use: classes by package, classes with their
 * subclasses ({@code instanceof}), thread stacks and functions. Double-click inserts the name, quoted where SQL needs
 * it. The filter box switches to a flat list of the classes whose name contains the text.
 */
class SchemaBrowser {
  private static final int MAX_FILTER_RESULTS = 1000;
  private static final int FILTER_DELAY_MILLIS = 200;
  /** Shared, so ClassRowTypeCache (keyed by type factory) caches the row types across browsers. */
  private static final RelDataTypeFactory TYPE_FACTORY = new JavaTypeFactoryImpl();

  private final Composite control;
  private final Text filter;
  private final TreeViewer viewer;
  private final Consumer<String> insert;
  private Schema schema;
  private List<Node> roots = Collections.emptyList();
  /** One instance, so a newer keystroke can cancel the refresh an older one scheduled. */
  private final Runnable refreshLater = this::refreshIfOpen;

  SchemaBrowser(Composite parent, ISnapshot snapshot, Consumer<String> insert) {
    this.insert = insert;
    control = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 2;
    control.setLayout(layout);

    filter = new Text(control, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
    filter.setMessage("Filter tables");
    filter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    // Search once typing pauses, not on every keystroke
    filter.addModifyListener(e -> {
      filter.getDisplay().timerExec(-1, refreshLater);
      filter.getDisplay().timerExec(FILTER_DELAY_MILLIS, refreshLater);
    });

    viewer = new TreeViewer(control, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
    viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    viewer.setContentProvider(new ContentProvider());
    viewer.setLabelProvider(new NodeLabelProvider());
    viewer.addDoubleClickListener(e -> {
      Object element = ((IStructuredSelection) e.getSelection()).getFirstElement();
      if (!(element instanceof Node)) {
        return;
      }
      Node node = (Node) element;
      if (node.insertText != null) {
        this.insert.accept(node.insertText);
      } else {
        viewer.setExpandedState(node, !viewer.getExpandedState(node));
      }
    });
    viewer.setInput(Collections.singletonList(new Node(Kind.MESSAGE, "Loading schema...", null)));

    loadSchema(snapshot);
  }

  Composite getControl() {
    return control;
  }

  private void loadSchema(ISnapshot snapshot) {
    if (snapshot == null) {
      viewer.setInput(Collections.singletonList(new Node(Kind.MESSAGE, "No heap dump", null)));
      return;
    }
    Job job = new Job("Loading SQL schema") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        Schema loaded;
        try {
          loaded = CalciteDataSource.getHeapSchema(snapshot);
        } catch (Exception e) {
          showMessage("Unable to load the schema: " + e.getMessage());
          return Status.OK_STATUS;
        }
        if (!control.isDisposed()) {
          control.getDisplay().asyncExec(() -> {
            if (control.isDisposed()) {
              return;
            }
            schema = loaded;
            roots = createRoots(loaded);
            refreshInput();
          });
        }
        return Status.OK_STATUS;
      }
    };
    job.setSystem(true);
    job.schedule();
  }

  private void showMessage(String message) {
    if (control.isDisposed()) {
      return;
    }
    control.getDisplay().asyncExec(() -> {
      if (!control.isDisposed()) {
        viewer.setInput(Collections.singletonList(new Node(Kind.MESSAGE, message, null)));
      }
    });
  }

  private void refreshIfOpen() {
    if (!filter.isDisposed()) {
      refreshInput();
    }
  }

  private void refreshInput() {
    if (schema == null) {
      return;
    }
    String text = filter.getText().trim();
    viewer.getTree().setRedraw(false);
    try {
      viewer.setInput(text.isEmpty() ? roots : filterClasses(text));
    } finally {
      viewer.getTree().setRedraw(true);
    }
  }

  private static List<Node> createRoots(Schema schema) {
    List<Node> roots = new ArrayList<>();
    Node classes = new Node(Kind.GROUP, "Classes", null);
    classes.schema = schema;
    roots.add(classes);
    Schema instanceOf = schema.getSubSchema(INSTANCEOF);
    if (instanceOf != null) {
      Node node = new Node(Kind.GROUP, "Classes and subclasses (instanceof)", null);
      node.schema = instanceOf;
      node.path = INSTANCEOF;
      roots.add(node);
    }
    Schema nativeSchema = schema.getSubSchema(NATIVE);
    if (nativeSchema != null) {
      Node node = new Node(Kind.GROUP, "Thread stacks (native)", null);
      node.schema = nativeSchema;
      node.path = NATIVE;
      roots.add(node);
    }
    Node functions = new Node(Kind.FUNCTIONS, "Functions", null);
    functions.schema = schema;
    roots.add(functions);
    return roots;
  }

  /** Tables whose name contains the text, as a flat list. */
  private List<Node> filterClasses(String text) {
    List<Node> result = new ArrayList<>();
    for (SchemaCatalog.TableRef table : SchemaCatalog.findTables(schema, text, MAX_FILTER_RESULTS + 1)) {
      if (result.size() == MAX_FILTER_RESULTS) {
        result.add(new Node(Kind.MESSAGE, "More than " + MAX_FILTER_RESULTS + " tables, refine the filter", null));
        break;
      }
      result.add(classNode(table.schema, table.tableName, table.fullName, table.sqlName));
    }
    if (result.isEmpty()) {
      result.add(new Node(Kind.MESSAGE, "No table matches", null));
    }
    return result;
  }

  private static Node classNode(Schema owner, String tableName, String label, String insertText) {
    Node node = new Node(Kind.CLASS, label, insertText);
    node.schema = owner;
    node.tableName = tableName;
    return node;
  }

  /** Children of a package-like schema: sub-packages, then classes. */
  private static List<Node> schemaChildren(Node parent) {
    List<Node> children = new ArrayList<>();
    boolean root = parent.path == null;
    for (String name : new TreeSet<>(parent.schema.getSubSchemaNames())) {
      if (root && (INSTANCEOF.equals(name) || NATIVE.equals(name))) {
        continue;
      }
      Node node = new Node(Kind.PACKAGE, name, null);
      node.schema = parent.schema.getSubSchema(name);
      node.path = root ? name : parent.path + "." + name;
      children.add(node);
    }
    for (String name : new TreeSet<>(parent.schema.getTableNames())) {
      if (name.startsWith(IDS_PREFIX)) {
        continue;
      }
      if (name.contains(".")) {
        // The root and instanceof schemas also list every class by its full name; the package tree shows those
        continue;
      }
      // Schema path form, e.g. java.util."HashMap$Node", instanceof.java.util.AbstractMap or native.ThreadStackFrames
      String insertText = root ? quote(name) : quotePath(parent.path) + "." + quote(name);
      children.add(classNode(parent.schema, name, name, insertText));
    }
    return children;
  }

  private static List<Node> columns(Node classNode) {
    Table table = classNode.schema.getTable(classNode.tableName);
    if (table == null) {
      return Collections.emptyList();
    }
    List<Node> children = new ArrayList<>();
    try {
      for (RelDataTypeField field : table.getRowType(TYPE_FACTORY).getFieldList()) {
        Node node = new Node(Kind.COLUMN, field.getName() + " : " + field.getType().getSqlTypeName(),
            quote(field.getName()));
        children.add(node);
      }
      // Heap object tables (first column "this") also have sizes, computed through the object
      if (!children.isEmpty() && "this".equals(table.getRowType(TYPE_FACTORY).getFieldList().get(0).getName())) {
        children.add(new Node(Kind.COLUMN, "this['@shallow'] : shallow heap size", "this['@shallow']"));
        children.add(new Node(Kind.COLUMN, "this['@retained'] : retained heap size", "this['@retained']"));
      }
    } catch (RuntimeException e) {
      children.add(new Node(Kind.MESSAGE, "Unable to read columns: " + e.getMessage(), null));
    }
    return children;
  }

  private static List<Node> functions(Node functionsNode) {
    List<Node> children = new ArrayList<>();
    for (String name : SchemaCatalog.functionNames(functionsNode.schema)) {
      children.add(new Node(Kind.FUNCTION, name, name + "("));
    }
    return children;
  }

  private enum Kind {
    GROUP, PACKAGE, CLASS, COLUMN, FUNCTIONS, FUNCTION, MESSAGE
  }

  private static final class Node {
    final Kind kind;
    final String label;
    /** Text inserted on double-click; null for nodes that only expand. */
    final String insertText;
    Schema schema;
    /** Dotted package path of package-like nodes, null for the root schema. */
    String path;
    String tableName;
    List<Node> children;

    Node(Kind kind, String label, String insertText) {
      this.kind = kind;
      this.label = label;
      this.insertText = insertText;
    }

    List<Node> children() {
      if (children == null) {
        switch (kind) {
          case GROUP:
          case PACKAGE:
            children = schemaChildren(this);
            break;
          case CLASS:
            children = columns(this);
            break;
          case FUNCTIONS:
            children = functions(this);
            break;
          default:
            children = Collections.emptyList();
            break;
        }
      }
      return children;
    }

    boolean hasChildren() {
      return kind != Kind.COLUMN && kind != Kind.FUNCTION && kind != Kind.MESSAGE;
    }
  }

  private static final class ContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object input) {
      return ((List<?>) input).toArray();
    }

    @Override
    public Object[] getChildren(Object parent) {
      return ((Node) parent).children().toArray();
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return ((Node) element).hasChildren();
    }
  }

  private static final class NodeLabelProvider extends LabelProvider {
    @Override
    public String getText(Object element) {
      return ((Node) element).label;
    }

    @Override
    public Image getImage(Object element) {
      ISharedImages shared = PlatformUI.getWorkbench().getSharedImages();
      switch (((Node) element).kind) {
        case GROUP:
        case PACKAGE:
        case FUNCTIONS:
          return shared.getImage(ISharedImages.IMG_OBJ_FOLDER);
        case CLASS:
          return image(Icons.CLASS);
        case COLUMN:
        case FUNCTION:
          return shared.getImage(ISharedImages.IMG_OBJ_ELEMENT);
        default:
          return null;
      }
    }

    /** Images from the shared JFace registry, which owns and disposes them. */
    private static Image image(URL url) {
      ImageRegistry registry = JFaceResources.getImageRegistry();
      String key = url.toExternalForm();
      Image image = registry.get(key);
      if (image == null) {
        registry.put(key, ImageDescriptor.createFromURL(url));
        image = registry.get(key);
      }
      return image;
    }
  }
}
