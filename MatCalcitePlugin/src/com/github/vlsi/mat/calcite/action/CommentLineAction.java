package com.github.vlsi.mat.calcite.action;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.custom.StyledText;

public class CommentLineAction extends Action {
  private final StyledText text;

  public CommentLineAction(StyledText text) {
    this.text = text;
  }

  @Override
  public void run() {
    int caretOffset = text.getCaretOffset();
    int lineIndex = text.getLineAtOffset(caretOffset);
    int lineStart = getLineStart(lineIndex);
    String lineText = text.getLine(lineIndex);

    // Move caret to next line
    if (lineIndex + 1 < text.getLineCount()) {
      text.invokeAction(ST.LINE_DOWN);
    }

    if (lineText.startsWith("--")) {
      // Uncomment
      text.replaceTextRange(lineStart, 2, "");
    } else {
      // Comment
      text.replaceTextRange(lineStart, 0, "--");
    }
  }

  private int getLineStart(int lineIndex) {
    String textContent = text.getText();
    String delimiter = text.getLineDelimiter();
    int lineStart = 0;

    while (lineIndex > 0) {
      lineStart = textContent.indexOf(delimiter, lineStart);
      if (lineStart > 0) {
        lineStart += delimiter.length();
      }
      lineIndex--;
    }
    return lineStart;
  }
}
