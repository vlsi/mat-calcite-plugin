package com.github.vlsi.mat.calcite.editor;

import com.github.vlsi.mat.calcite.action.CommentLineAction;
import com.github.vlsi.mat.calcite.action.ExecuteQueryAction;

import org.apache.calcite.runtime.CalciteContextException;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.parser.SqlParserUtil;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.JFacePreferences;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.EditorPaneRegistry;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CalcitePane extends CompositeHeapEditorPane {
  /** Key binding context active while the SQL editor has focus, see plugin.xml. */
  public static final String CONTEXT_ID = "com.github.vlsi.mat.calcite.sqlEditor";
  private static final String COMMAND_PREFIX = "com.github.vlsi.mat.calcite.";
  private static final String TITLE_IMAGE_KEY = "com.github.vlsi.mat.calcite.paneImage";

  private static final String HINT = "-- F5 or Ctrl+Enter: run, F10: explain plan, Ctrl+Space: complete,"
      + " Ctrl+/: comment line\n"
      + "-- Tables, columns and functions: see the SQL Reference button in the toolbar\n";
  private static final String DEFAULT_QUERY = HINT
      + "select u.this, retainedSize(s.this) retained_size\n"
      + "  from \"java.lang.String\" s\n"
      + "     , \"java.net.URL\" u\n"
      + " where s.this = u.path\n";

  /** Open SQL panes, least recently used first, see {@link #lastUsed(MultiPaneEditor)}. UI thread only. */
  private static final List<CalcitePane> OPEN_PANES = new ArrayList<>();

  private SourceViewer queryViewer;
  private StyledText queryString;

  private ExecuteQueryAction executeQueryAction;
  private ExecuteQueryAction explainQueryAction;
  private Action commentLineAction;
  private Action contentAssistAction;
  private Action showReferenceAction;
  private Action toggleSchemaAction;
  private SashForm outerSash;
  private SchemaBrowser schemaBrowser;
  /** Text editing actions for the context menu and the global Cut/Copy/Paste handlers, keyed by action factory id. */
  private final Map<String, TextOperationAction> textActions = new LinkedHashMap<>();
  private TextOperationAction undoAction;
  private TextOperationAction redoAction;
  private IContextActivation contextActivation;

  public CalcitePane() {
  }

  @Override
  public void createPartControl(Composite parent) {
    OPEN_PANES.add(this);
    // Schema browser on the left, full height; query editor above the results on the right
    outerSash = new SashForm(parent, SWT.HORIZONTAL | SWT.SMOOTH);
    schemaBrowser = new SchemaBrowser(outerSash, getSnapshot(), this::insertText);
    SashForm sash = new SashForm(outerSash, SWT.VERTICAL | SWT.SMOOTH);

    int VERTICAL_RULER_WIDTH = 12;
    CompositeRuler ruler = new CompositeRuler(VERTICAL_RULER_WIDTH);
    ruler.addDecorator(0, new ThemedLineNumberRulerColumn());
    queryViewer = new SourceViewer(sash, ruler, SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
    queryString = queryViewer.getTextWidget();
    // The following setBackground(getBackground) results in proper white background in MACOS.
    // No sure why the background is gray otherwise.
    queryString.setBackground(queryString.getBackground());
    queryString.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
    TransientResizeScrollFix.install(queryString);
    // Fallback for hosts where the key bindings from plugin.xml are not active (they are declared in MAT's key
    // binding scheme). When a binding handles a key, the workbench consumes the event and these listeners never see it.
    queryString.addTraverseListener(e -> {
      if (e.detail == SWT.TRAVERSE_RETURN && (e.stateMask & SWT.MOD1) != 0) {
        executeQueryAction.run();
        e.detail = SWT.TRAVERSE_NONE;
      }
    });
    queryString.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.keyCode == ' ' && (e.stateMask & SWT.CTRL) != 0) {
          // ctrl space combination for content assist
          contentAssistAction.run();
        } else if (e.character == '/' && (e.stateMask & SWT.MOD1) != 0) {
          commentLineAction.run();
          e.doit = false;
        } else if (e.keyCode == SWT.F5) {
          executeQueryAction.run();
          e.doit = false;
        } else if (e.keyCode == SWT.F10) {
          explainQueryAction.run();
          e.doit = false;
        }
      }

    });
    ActiveShellFix.install(queryString.getShell());
    queryString.addListener(SWT.MouseDown, e -> reactivateShell());
    queryString.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        OPEN_PANES.remove(CalcitePane.this);
        OPEN_PANES.add(CalcitePane.this);
        reactivateShell();
        IContextService contextService = getEditor().getSite().getService(IContextService.class);
        if (contextService != null && contextActivation == null) {
          contextActivation = contextService.activateContext(CONTEXT_ID);
        }
        IActionBars actionBars = getEditor().getEditorSite().getActionBars();
        for (Map.Entry<String, TextOperationAction> entry : textActions.entrySet()) {
          actionBars.setGlobalActionHandler(entry.getKey(), entry.getValue());
        }
        actionBars.updateActionBars();
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (contextActivation != null) {
          contextActivation.getContextService().deactivateContext(contextActivation);
          contextActivation = null;
        }
        IActionBars actionBars = getEditor().getEditorSite().getActionBars();
        for (String id : textActions.keySet()) {
          actionBars.setGlobalActionHandler(id, null);
        }
        actionBars.updateActionBars();
      }
    });

    IDocument doc = new Document(DEFAULT_QUERY);
    SourceViewerConfiguration svc = new CalciteSourceViewerConfiguration();
    IDocumentPartitioner partitioner = new FastPartitioner(
        new CalcitePartitionScanner(),
        svc.getConfiguredContentTypes(queryViewer));
    partitioner.connect(doc);
    doc.setDocumentPartitioner(partitioner);
    queryViewer.setDocument(doc);
    queryViewer.configure(svc);

    queryString.selectAll();

    createContainer(sash);
    sash.setWeights(1, 4);
    outerSash.setWeights(1, 4);

    makeActions();
    hookContextMenu();
    queryViewer.addSelectionChangedListener(e -> updateTextActions());
    queryViewer.addTextListener(e -> updateTextActions());
    updateTextActions();
  }

  /**
   * Fallback for {@link ActiveShellFix}: when the user clicks or focuses the editor, the window is certainly active,
   * so restore SWT's active shell if it was lost. A no-op while SWT's state is correct.
   */
  private void reactivateShell() {
    if (queryString.getDisplay().getActiveShell() == null) {
      queryString.getShell().forceActive();
    }
  }

  @Override
  public void setFocus() {
    queryString.setFocus();
  }

  @Override
  public String getTitle() {
    return "Calcite";
  }

  /**
   * The image is shared and never disposed: the navigation history keeps showing it for queries of SQL tabs that
   * have already been closed.
   */
  @Override
  public Image getTitleImage() {
    ImageRegistry registry = JFaceResources.getImageRegistry();
    Image image = registry.get(TITLE_IMAGE_KEY);
    if (image == null) {
      registry.put(TITLE_IMAGE_KEY,
          AbstractUIPlugin.imageDescriptorFromPlugin("MatCalcitePlugin", "resources/icons/plugin.png"));
      image = registry.get(TITLE_IMAGE_KEY);
    }
    return image;
  }

  /**
   * Runs an SQL editor command declared in plugin.xml, see {@link SqlEditorCommandHandler}.
   */
  void runCommand(String commandId) {
    switch (commandId.substring(COMMAND_PREFIX.length())) {
      case "execute":
        executeQueryAction.run();
        break;
      case "explain":
        explainQueryAction.run();
        break;
      case "contentAssist":
        contentAssistAction.run();
        break;
      case "toggleComment":
        commentLineAction.run();
        break;
      default:
        break;
    }
  }

  private void makeActions() {
    executeQueryAction = new ExecuteQueryAction(this, null, false);
    explainQueryAction = new ExecuteQueryAction(this, null, true);
    commentLineAction = new CommentLineAction(queryString);
    commentLineAction.setText("Toggle Comment");
    contentAssistAction = new Action() {
      @Override
      public void run() {
        queryViewer.doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
      }
    };
    toggleSchemaAction = new Action("Schema Browser", IAction.AS_CHECK_BOX) {
      @Override
      public void run() {
        outerSash.setMaximizedControl(isChecked() ? null : outerSash.getChildren()[1]);
      }
    };
    toggleSchemaAction.setToolTipText("Show or hide the tables, columns and functions");
    toggleSchemaAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
        .getImageDescriptor(ISharedImages.IMG_OBJ_FOLDER));
    toggleSchemaAction.setChecked(true);
    showReferenceAction = new Action("SQL Reference") {
      @Override
      public void run() {
        // A separate tab, so the current result stays where it is
        getEditor().addNewPage(SqlReferencePane.ID, null, true);
      }
    };
    showReferenceAction.setToolTipText("SQL Reference: tables, virtual columns and functions");
    showReferenceAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
        .getImageDescriptor(ISharedImages.IMG_LCL_LINKTO_HELP));

    undoAction = addTextAction(ActionFactory.UNDO, ITextOperationTarget.UNDO, true);
    redoAction = addTextAction(ActionFactory.REDO, ITextOperationTarget.REDO, true);
    addTextAction(ActionFactory.CUT, ITextOperationTarget.CUT, true);
    addTextAction(ActionFactory.COPY, ITextOperationTarget.COPY, true);
    addTextAction(ActionFactory.PASTE, ITextOperationTarget.PASTE, true);
    addTextAction(ActionFactory.DELETE, ITextOperationTarget.DELETE, true);
    addTextAction(ActionFactory.SELECT_ALL, ITextOperationTarget.SELECT_ALL, true);
  }

  /**
   * Creates a context menu action that runs a text operation on the query viewer, copying text, icon and
   * accelerator from the matching workbench action.
   *
   * @param global whether the action also handles the workbench action (and its key binding) while the editor
   *               has focus
   */
  private TextOperationAction addTextAction(ActionFactory factory, int operation, boolean global) {
    IWorkbenchWindow window = getEditorSite().getWorkbenchWindow();
    ActionFactory.IWorkbenchAction globalAction = factory.create(window);
    TextOperationAction action = new TextOperationAction(operation);
    action.setText(globalAction.getText());
    action.setToolTipText(globalAction.getToolTipText());
    action.setImageDescriptor(globalAction.getImageDescriptor());
    action.setDisabledImageDescriptor(globalAction.getDisabledImageDescriptor());
    action.setActionDefinitionId(globalAction.getActionDefinitionId());
    globalAction.dispose();
    if (global) {
      textActions.put(factory.getId(), action);
    }
    return action;
  }

  private void hookContextMenu() {
    MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
    menuMgr.setRemoveAllWhenShown(true);
    menuMgr.addMenuListener(this::fillContextMenu);
    queryString.setMenu(menuMgr.createContextMenu(queryString));
  }

  private void fillContextMenu(IMenuManager manager) {
    updateTextActions();
    manager.add(executeQueryAction);
    manager.add(explainQueryAction);
    manager.add(new Separator());
    manager.add(undoAction);
    manager.add(redoAction);
    manager.add(new Separator());
    manager.add(textActions.get(ActionFactory.CUT.getId()));
    manager.add(textActions.get(ActionFactory.COPY.getId()));
    manager.add(textActions.get(ActionFactory.PASTE.getId()));
    manager.add(new Separator());
    manager.add(textActions.get(ActionFactory.DELETE.getId()));
    manager.add(textActions.get(ActionFactory.SELECT_ALL.getId()));
    manager.add(new Separator());
    manager.add(commentLineAction);
    manager.add(showReferenceAction);
  }

  /**
   * Line numbers in the editor's own colors: by default the column uses the system list colors, which do not follow
   * the workbench theme (a black column with dark numbers in the dark theme). The colors are read on every paint, so
   * they also pick up theme styling applied after the editor was created.
   */
  private class ThemedLineNumberRulerColumn extends LineNumberRulerColumn {
    private RGB lastForeground;

    @Override
    protected Color getBackground(Display display) {
      if (queryString == null || queryString.isDisposed()) {
        return super.getBackground(display);
      }
      Color background = queryString.getBackground();
      // Numbers half way between text and background, like Eclipse's own line numbers
      RGB fg = queryString.getForeground().getRGB();
      RGB bg = background.getRGB();
      RGB numbers = new RGB((fg.red + bg.red) / 2, (fg.green + bg.green) / 2, (fg.blue + bg.blue) / 2);
      if (!numbers.equals(lastForeground)) {
        lastForeground = numbers;
        setForeground(new Color(display, numbers));
      }
      return background;
    }
  }

  private class TextOperationAction extends Action {
    private final int operation;

    TextOperationAction(int operation) {
      this.operation = operation;
    }

    void update() {
      setEnabled(queryViewer.canDoOperation(operation));
    }

    @Override
    public void run() {
      queryViewer.doOperation(operation);
      // The undo manager records that redo is possible only after the text change, so refresh again afterwards
      updateTextActions();
    }
  }

  /**
   * Refreshes the enabled state of the text actions. It is stored rather than computed in isEnabled(), so the context
   * menu and the key bindings (through the global action handlers) see the same up-to-date value.
   */
  private void updateTextActions() {
    undoAction.update();
    redoAction.update();
    for (TextOperationAction action : textActions.values()) {
      action.update();
    }
  }

  @Override
  public void dispose() {
    OPEN_PANES.remove(this);
    // A query still running has no tab left to show its result in
    Job.getJobManager().cancel(this);
    // Closed while the editor had focus: focusLost may never come, which would leave the SQL key bindings active
    // and the editor-wide Cut/Copy/Paste handlers pointing at this closed editor. Only handlers still ours are
    // cleared, since another pane may have set its own since.
    if (contextActivation != null) {
      contextActivation.getContextService().deactivateContext(contextActivation);
      contextActivation = null;
    }
    MultiPaneEditor editor = getEditor();
    if (editor != null && editor.getEditorSite() != null) {
      IActionBars actionBars = editor.getEditorSite().getActionBars();
      boolean changed = false;
      for (Map.Entry<String, TextOperationAction> entry : textActions.entrySet()) {
        if (actionBars.getGlobalActionHandler(entry.getKey()) == entry.getValue()) {
          actionBars.setGlobalActionHandler(entry.getKey(), null);
          changed = true;
        }
      }
      if (changed) {
        actionBars.updateActionBars();
      }
    }
    super.dispose();
  }

  /**
   * The SQL pane of the editor that was opened or focused last, or null if the editor has none.
   */
  static CalcitePane lastUsed(MultiPaneEditor editor) {
    for (int i = OPEN_PANES.size() - 1; i >= 0; i--) {
      CalcitePane pane = OPEN_PANES.get(i);
      if (pane.getEditor() == editor && pane.queryString != null && !pane.queryString.isDisposed()) {
        return pane;
      }
    }
    return null;
  }

  /**
   * Adds a query at the end of the editor, or in place of the sample query of a new tab, and selects it so F5 runs
   * just that query. Nothing is executed.
   */
  void appendQuery(String sql) {
    IDocument doc = queryViewer.getDocument();
    String text = doc.get();
    int start;
    String insert;
    if (text.equals(DEFAULT_QUERY)) {
      start = HINT.length();
      insert = sql;
    } else {
      start = text.length();
      insert = (text.isEmpty() || text.endsWith("\n\n") ? "" : text.endsWith("\n") ? "\n" : "\n\n") + sql;
    }
    try {
      doc.replace(start, text.length() - start, insert);
    } catch (BadLocationException e) {
      return;
    }
    int queryStart = start + insert.length() - sql.length();
    queryViewer.setSelectedRange(queryStart, sql.length());
    queryViewer.revealRange(queryStart, sql.length());
    queryString.setFocus();
  }

  private ISnapshot getSnapshot() {
    return (ISnapshot) getEditor().getQueryContext().get(ISnapshot.class, null);
  }

  /**
   * Inserts text at the cursor, replacing the selection, as one undoable edit, and focuses the editor.
   */
  private void insertText(String text) {
    Point selection = queryViewer.getSelectedRange();
    try {
      queryViewer.getDocument().replace(selection.x, selection.y, text);
    } catch (BadLocationException e) {
      return;
    }
    queryViewer.setSelectedRange(selection.x + text.length(), 0);
    queryString.setFocus();
  }

  public StyledText getQueryString() {
    return queryString;
  }

  /**
   * Removes the error underline of an earlier run: it is drawn over the syntax colors, which repaint only text that
   * is edited, so it would otherwise stay after the query is fixed or another one runs.
   */
  public void clearErrorHighlight() {
    if (!queryString.isDisposed()) {
      queryViewer.invalidateTextPresentation();
    }
  }

  /**
   * Underlines the position of an SQL error and returns the error's message.
   *
   * @param sql          the SQL that ran, which positions in the error refer to
   * @param editorOffset where sql starts in the editor (a selection was run)
   * @param prefixLength the length of text at the start of sql that is not in the editor (explain plan for)
   */
  public String highlightError(Throwable e, String sql, int editorOffset, int prefixLength) {
    Throwable t;
    SqlParserPos errPos = null;
    for (t = e; t != null; t = t.getCause()) {
      if (t instanceof CalciteContextException) {
        CalciteContextException cce = (CalciteContextException) t;
        errPos = new SqlParserPos(cce.getPosLine(), cce.getPosColumn(),
            cce.getEndPosLine(), cce.getEndPosColumn());
        break;
      }
      if (t instanceof SqlParseException) {
        SqlParseException spe = (SqlParseException) t;
        errPos = spe.getPos();
        break;
      }
    }
    if (errPos == null) {
      return null;
    }

    clearErrorHighlight();
    StyleRange style = new StyleRange();
    int start;
    int end;
    try {
      start = SqlParserUtil.lineColToIndex(sql, errPos.getLineNum(), errPos.getColumnNum());
      end = SqlParserUtil.lineColToIndex(sql, errPos.getEndLineNum(), errPos.getEndColumnNum()) + 1;
    } catch (RuntimeException outside) {
      return t.getMessage(); // a position outside the SQL: nothing to underline
    }
    start = Math.max(start, prefixLength) - prefixLength + editorOffset;
    end = Math.max(end, prefixLength) - prefixLength + editorOffset;
    if (start < 0 || end <= start || end > queryString.getCharCount()) {
      return t.getMessage(); // e.g. the text was edited while the query ran
    }
    style.start = start;
    style.length = end - start;
    style.foreground = JFaceResources.getColorRegistry().get(JFacePreferences.ERROR_COLOR);
    style.underline = true;
    style.underlineStyle = SWT.UNDERLINE_SQUIGGLE;
    queryString.replaceStyleRanges(start, end - start, new StyleRange[]{style});
    return t.getMessage();
  }

  /**
   * Re-opens a query from the navigation history, or runs a query passed by another pane.
   */
  @Override
  public void initWithArgument(Object argument) {
    if (argument instanceof String) {
      queryViewer.getDocument().set((String) argument);
      executeQueryAction.run();
    } else if (argument instanceof QueryResult) {
      initQueryResult((QueryResult) argument, null, null);
    } else if (argument instanceof PaneState) {
      PaneState state = (PaneState) argument;
      queryViewer.getDocument().set(state.getIdentifier());
      new ExecuteQueryAction(this, state, false).run();
    }
  }

  /**
   * Shows a query result and records it in the navigation history.
   *
   * @param sql the SQL that produced the result, used to re-run it from the history; the editor text when null
   */
  public void initQueryResult(QueryResult queryResult, PaneState state, String sql) {
    IResult subject = queryResult.getSubject();
    String identifier = sql == null ? queryString.getText() : sql.trim();

    AbstractEditorPane pane = EditorPaneRegistry.instance().createNewPane(
        subject, this.getClass());

    if (state == null) {
      for (PaneState child : getPaneState().getChildren()) {
        if (identifier.equals(child.getIdentifier())) {
          state = child;
          break;
        }
      }

      if (state == null) {
        state = new PaneState(PaneType.COMPOSITE_CHILD, getPaneState(), identifier, true);
        state.setImage(getTitleImage());
      }
    }

    pane.setPaneState(state);

    createResultPane(pane, queryResult);
  }

  /**
   * Shows plain text (an error) in the result area, without adding it to the navigation history.
   */
  public void showText(String text) {
    AbstractEditorPane pane;
    try {
      pane = EditorPaneRegistry.instance().createNewPane("TextViewPane"); //$NON-NLS-1$
    } catch (Exception e) {
      ErrorHelper.logThrowable(e);
      return;
    }
    if (pane == null) {
      return;
    }
    createResultPane(pane, text);
  }

  @Override
  public void contributeToToolBar(IToolBarManager manager) {
    manager.add(executeQueryAction);
    manager.add(explainQueryAction);
    manager.add(showReferenceAction);
    manager.add(toggleSchemaAction);
    super.contributeToToolBar(manager);
  }
}
