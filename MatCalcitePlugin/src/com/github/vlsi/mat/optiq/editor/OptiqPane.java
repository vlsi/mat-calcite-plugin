package com.github.vlsi.mat.optiq.editor;

import com.github.vlsi.mat.optiq.action.CommentLineAction;
import com.github.vlsi.mat.optiq.action.ExecuteQueryAction;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.IUndoManagerExtension;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.mat.query.IResult;
import org.eclipse.mat.query.registry.QueryResult;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.mat.ui.editor.CompositeHeapEditorPane;
import org.eclipse.mat.ui.editor.EditorPaneRegistry;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.operations.RedoActionHandler;
import org.eclipse.ui.operations.UndoActionHandler;

public class OptiqPane extends CompositeHeapEditorPane {
	private SourceViewer queryViewer;
	private StyledText queryString;

	private Action executeAction;
	private Action copyQueryStringAction;
	private Action commentLineAction;

	public OptiqPane() {
	}

	@Override
	public void createPartControl(Composite parent) {
		SashForm sash = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
		queryViewer = new SourceViewer(sash, null, SWT.MULTI | SWT.WRAP);
		queryViewer.configure(new OptiqSourceViewerConfiguration());
		queryString = queryViewer.getTextWidget();
		// The following setBackground(getBackround) results in proper white background in MACOS.
		// No sure why the background is gray otherwise.
		queryString.setBackground(queryString.getBackground());
		queryString.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
		queryString.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_RETURN && (e.stateMask & SWT.MOD1) != 0) {
					executeAction.run();
					e.detail = SWT.TRAVERSE_NONE;
				}
			}
		});
		queryString.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == ' ' && (e.stateMask & SWT.CTRL) != 0) {
					// ctrl space combination for content assist
					// contentAssistAction.run();
				} else if (e.character == '/' && (e.stateMask & SWT.MOD1) != 0) {
					commentLineAction.run();
					e.doit = false;
				}
				else if (e.keyCode == SWT.F5) {
					executeAction.run();
					e.doit = false;
				}
			}

		});
		this.queryString.addFocusListener(new FocusListener() {
			public void focusGained(FocusEvent e) {
				IActionBars actionBars = OptiqPane.this.getEditor().getEditorSite().getActionBars();
				actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(), OptiqPane.this.copyQueryStringAction);
				actionBars.updateActionBars();
			}

			public void focusLost(FocusEvent e) {
			}
		});

		IDocument doc = createDocument();
		SourceViewerConfiguration svc = new OptiqSourceViewerConfiguration();
		IDocumentPartitioner partitioner = new FastPartitioner(
				new OptiqPartitionScanner(),
				svc.getConfiguredContentTypes(queryViewer));
		partitioner.connect(doc);
		doc.setDocumentPartitioner(partitioner);
		queryViewer.setDocument(doc);
		queryViewer.configure(svc);

		createContainer(sash);
		makeActions();

		installUndoRedoSupport();
	}

	private void installUndoRedoSupport() {
		IUndoContext undoContext = ((IUndoManagerExtension) queryViewer.getUndoManager()).getUndoContext();

		UndoActionHandler undoAction = new UndoActionHandler(getSite(), undoContext);
		RedoActionHandler redoAction = new RedoActionHandler(getSite(), undoContext);

		undoAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_UNDO);
		redoAction.setActionDefinitionId(IWorkbenchCommandConstants.EDIT_REDO);

		IActionBars actionBars = getEditor().getEditorSite().getActionBars();
		actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(), undoAction);
		actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(), redoAction);

		actionBars.updateActionBars();
	}

	private IDocument createDocument() {
		IDocument doc = new Document();
		doc.set("select * from \"java.net.URL\"");
		return doc;
	}

	@Override
	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		super.init(site, input);
	}

	@Override
	public void setFocus() {
	}

	@Override
	public String getTitle() {
		return "Calcite SQL";
	}

	private void makeActions() {
		executeAction = new ExecuteQueryAction(this, null);
		commentLineAction = new CommentLineAction(queryString);
		IWorkbenchWindow window = this.getEditorSite().getWorkbenchWindow();
		ActionFactory.IWorkbenchAction globalAction = ActionFactory.COPY.create(window);
		this.copyQueryStringAction = new Action() {
			public void run() {
				OptiqPane.this.queryString.copy();
			}
		};
		this.copyQueryStringAction.setAccelerator(globalAction.getAccelerator());
	}

	public StyledText getQueryString() {
		return queryString;
	}

	public void initQueryResult(QueryResult queryResult, PaneState state) {
		IResult subject = queryResult.getSubject();
		// queryViewer.getDocument().set(subject.getOQLQuery());

		AbstractEditorPane pane = EditorPaneRegistry.instance().createNewPane(
				subject, this.getClass());

		if (state == null) {
			for (PaneState child : getPaneState().getChildren()) {
				if (queryString.getText().equals(child.getIdentifier())) {
					state = child;
					break;
				}
			}

			if (state == null) {
				state = new PaneState(PaneType.COMPOSITE_CHILD, getPaneState(),
						queryString.getText(), true);
				state.setImage(getTitleImage());
			}
		}

		pane.setPaneState(state);

		createResultPane(pane, queryResult);
	}
}
