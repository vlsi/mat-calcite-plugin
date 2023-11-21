package com.github.vlsi.mat.calcite.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlinkPresenter;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

public class CalciteSourceViewerConfiguration extends SourceViewerConfiguration {
  @Override
  public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
    return new String[]{IDocument.DEFAULT_CONTENT_TYPE,
        CalcitePartitionScanner.SQL_COMMENT,
        CalcitePartitionScanner.SQL_QUOTED_IDENTIFIER,
        CalcitePartitionScanner.SQL_STRING};
  }

  @Override
  public IHyperlinkPresenter getHyperlinkPresenter(ISourceViewer sourceViewer) {
		/* When using default implementation, the following exception appears:
	  java.lang.IllegalStateException
	at org.eclipse.jface.text.TextViewer.setHyperlinkPresenter(TextViewer.java:5712)
	at org.eclipse.jface.text.source.SourceViewer.configure(SourceViewer.java:491)
	at CalcitePane.createPartControl(CalcitePane.java:84)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addPage(MultiPaneEditor.java:585)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addPage(MultiPaneEditor.java:574)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.doAddNewPage(MultiPaneEditor.java:552)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addNewPage(MultiPaneEditor.java:535)
	at org.eclipse.mat.ui.editor.MultiPaneEditor.addNewPage(MultiPaneEditor.java:489)
	at OpenCalciteAction.run(OpenCalciteAction.java:22)
		 */
    return null;
  }

  @Override
  public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
    IContentAssistProcessor proc = new CalciteContentAssistantProcessor();
    ContentAssistant assistant = new ContentAssistant();
    assistant.enableAutoActivation(false);
    assistant.setAutoActivationDelay(500);
    assistant.setContentAssistProcessor(proc, IDocument.DEFAULT_CONTENT_TYPE);
    assistant.setContentAssistProcessor(proc, CalcitePartitionScanner.SQL_QUOTED_IDENTIFIER);
    return assistant;
  }

  @Override
  public IPresentationReconciler getPresentationReconciler(
      ISourceViewer sourceViewer) {
    PresentationReconciler reconciler = new PresentationReconciler();
    DefaultDamagerRepairer dr;

    dr = new DefaultDamagerRepairer(new CalciteScanner());
    reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
    reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

    dr = new DefaultDamagerRepairer(new StringScanner());
    reconciler.setDamager(dr, CalcitePartitionScanner.SQL_QUOTED_IDENTIFIER);
    reconciler.setRepairer(dr, CalcitePartitionScanner.SQL_QUOTED_IDENTIFIER);
    reconciler.setDamager(dr, CalcitePartitionScanner.SQL_STRING);
    reconciler.setRepairer(dr, CalcitePartitionScanner.SQL_STRING);

    dr = new DefaultDamagerRepairer(new CommentScanner());
    reconciler.setDamager(dr, CalcitePartitionScanner.SQL_COMMENT);
    reconciler.setRepairer(dr, CalcitePartitionScanner.SQL_COMMENT);
    return reconciler;
  }

}
