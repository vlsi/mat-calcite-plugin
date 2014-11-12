package com.github.vlsi.mat.optiq.action;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.custom.StyledText;

public class CommentLineAction extends Action
{
    private StyledText text;

    public CommentLineAction(StyledText text)
    {
        this.text = text;
    }

    @Override
    public void run()
    {
        int caretOffset = text.getCaretOffset();
        int lineIndex = text.getLineAtOffset(caretOffset);
        int lineStart = getLineStart(lineIndex);
        String lineText = text.getLine(lineIndex);

        if (lineText.startsWith("--"))
        {
            // Uncomment
            text.replaceTextRange(lineStart, 2, "");
        }
        else
        {
            // Comment
            text.replaceTextRange(lineStart, 0, "--");
        }

        // Move to the next line
        if (lineIndex+1 < text.getLineCount())
        {
            int lineOffset = caretOffset - lineStart;
            int nextLineLength = text.getLine(lineIndex+1).length();

            if (lineOffset > nextLineLength)
            {
                lineOffset = nextLineLength;
            }

            text.setCaretOffset(getLineStart(lineIndex+1) + lineOffset);
        }
    }

    private int getLineStart(int lineIndex)
    {
        String textContent = text.getText();
        String delimiter = text.getLineDelimiter();
        int lineStart = 0;

        while(lineIndex>0)
        {
            lineStart = textContent.indexOf(delimiter, lineStart);
            if (lineStart > 0)
            {
                lineStart += delimiter.length();
            }
            lineIndex--;
        }
        return lineStart;
    }
}
