package com.github.vlsi.mat.optiq.editor;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.runtime.CoreException;
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
import org.eclipse.mat.ui.util.ErrorHelper;
import org.eclipse.mat.ui.util.PaneState;
import org.eclipse.mat.ui.util.PaneState.PaneType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.operations.RedoActionHandler;
import org.eclipse.ui.operations.UndoActionHandler;

import com.github.vlsi.mat.optiq.action.ExecuteQueryAction;

public class OptiqPane extends CompositeHeapEditorPane {
	private SourceViewer queryViewer;
	private StyledText queryString;

	private Action executeAction;

	public OptiqPane() {
	}

	@Override
	public void createPartControl(Composite parent) {
		SashForm sash = new SashForm(parent, SWT.VERTICAL | SWT.SMOOTH);
		queryViewer = new SourceViewer(sash, null, SWT.MULTI | SWT.WRAP);
		queryViewer.configure(new OptiqSourceViewerConfiguration());
		queryString = queryViewer.getTextWidget();
		queryString.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
		queryString.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == '\r' && (e.stateMask & SWT.MOD1) != 0) {
					e.doit = false;
					executeAction.run();
				} else if (e.keyCode == ' ' && (e.stateMask & SWT.CTRL) != 0) {
					// ctrl space combination for content assist
					// contentAssistAction.run();
				} else if (e.keyCode == SWT.F5) {
					executeAction.run();
					e.doit = false;
				}
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
	}

	public StyledText getQueryString() {
		return queryString;
	}

	public void createExceptionPane(String message, Throwable e)
			throws PartInitException {
		StringWriter sw = new StringWriter();
		if (message != null)
			sw.append(message);
		e.printStackTrace(new PrintWriter(sw));

		AbstractEditorPane pane;
		try {
			pane = EditorPaneRegistry.instance().createNewPane("TextViewPane");//$NON-NLS-1$
			if (pane == null)
				throw new PartInitException("TextViewPane not found");
		} catch (CoreException e1) {
			throw new PartInitException(ErrorHelper.createErrorStatus(e1));
		}
		createResultPane(pane, sw.toString());
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
