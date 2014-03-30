package com.github.vlsi.mat.optiq.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.hyperlink.IHyperlinkPresenter;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

public class OptiqSourceViewerConfiguration extends SourceViewerConfiguration {
	@Override
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] { IDocument.DEFAULT_CONTENT_TYPE,
				OptiqPartitionScanner.SQL_COMMENT,
				OptiqPartitionScanner.SQL_QUOTED_IDENTIFIER,
				OptiqPartitionScanner.SQL_STRING };
	}

	@Override
	public IHyperlinkPresenter getHyperlinkPresenter(ISourceViewer sourceViewer) {
		/* When using default implementation, the following exception appears:
	  java.lang.IllegalStateException
	at org.eclipse.jface.text.TextViewer.setHyperlinkPresenter(TextViewer.java:5712)
	at org.eclipse.jface.text.source.SourceViewer.configure(SourceViewer.java:491)
	at com.github.vlsi.mat.optiq.editor.OptiqPane.createPartControl(OptiqPane.java:84)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addPage(MultiPaneEditor.java:585)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addPage(MultiPaneEditor.java:574)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.doAddNewPage(MultiPaneEditor.java:552)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addNewPage(MultiPaneEditor.java:535)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addNewPage(MultiPaneEditor.java:489)
	at com.github.vlsi.mat.optiq.editor.OpenOptiqAction.run(OpenOptiqAction.java:22)
		 */
		return null;
	}

	@Override
	public IPresentationReconciler getPresentationReconciler(
			ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		DefaultDamagerRepairer dr;

		dr = new DefaultDamagerRepairer(new OptiqScanner());
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

		dr = new DefaultDamagerRepairer(new StringScanner());
		reconciler.setDamager(dr, OptiqPartitionScanner.SQL_QUOTED_IDENTIFIER);
		reconciler.setRepairer(dr, OptiqPartitionScanner.SQL_QUOTED_IDENTIFIER);
		reconciler.setDamager(dr, OptiqPartitionScanner.SQL_STRING);
		reconciler.setRepairer(dr, OptiqPartitionScanner.SQL_STRING);

		dr = new DefaultDamagerRepairer(new CommentScanner());
		reconciler.setDamager(dr, OptiqPartitionScanner.SQL_COMMENT);
		reconciler.setRepairer(dr, OptiqPartitionScanner.SQL_COMMENT);
		return reconciler;
	}

}
