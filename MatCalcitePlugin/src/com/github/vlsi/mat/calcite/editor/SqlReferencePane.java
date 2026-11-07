package com.github.vlsi.mat.calcite.editor;

import com.github.vlsi.mat.calcite.SqlReference;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.mat.ui.editor.AbstractEditorPane;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * Read-only SQL quick reference, opened as its own editor tab so it does not replace the query results.
 */
public class SqlReferencePane extends AbstractEditorPane {
  /** Pane id, see plugin.xml. */
  public static final String ID = "CALCITE_REFERENCE";

  private StyledText text;

  @Override
  public void createPartControl(Composite parent) {
    text = new StyledText(parent, SWT.MULTI | SWT.READ_ONLY | SWT.H_SCROLL | SWT.V_SCROLL);
    text.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
    text.setMargins(8, 8, 8, 8);
    text.setText(SqlReference.TEXT);
  }

  @Override
  public void setFocus() {
    text.setFocus();
  }

  @Override
  public String getTitle() {
    return "SQL Reference";
  }

  @Override
  public Image getTitleImage() {
    // Shared workbench image, must not be disposed
    return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_LCL_LINKTO_HELP);
  }
}
