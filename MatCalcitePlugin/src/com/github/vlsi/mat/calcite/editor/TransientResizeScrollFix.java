package com.github.vlsi.mat.calcite.editor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

/**
 * Keeps the query editor's scroll position across a transient resize.
 *
 * <p>When a query result adds buttons to MAT's toolbar, JFace's {@code ToolBarManager.relayout} packs the toolbar's
 * parent, which briefly lays the whole editor area out at its preferred size (as wide as the longest SQL line) before
 * {@code requestLayout} restores it. While the editor is taller, {@code StyledText} scrolls up to fill the space below
 * the last line ("claim bottom free space"), and it does not scroll back when the editor shrinks again. With the
 * editor scrolled to the end, running a query therefore made the text jump.
 *
 * <p>The fix records the scroll position just before a resize (a display filter runs before StyledText's own resize
 * handling). If the editor grows, scrolls up, and returns to its original height shortly after, that position is
 * restored. Lasting resizes, such as dragging the sash, are left alone.
 */
final class TransientResizeScrollFix {
  /** How long a resize may last to count as transient. */
  private static final int TRANSIENT_MILLIS = 500;

  private final StyledText text;
  private int lastHeight;
  private int topBeforeResize;
  /** Height and scroll position to return to when the editor shrinks back, or -1 when nothing is pending. */
  private int restoreHeight = -1;
  private int restoreTop;
  private long restoreUntil;

  private TransientResizeScrollFix(StyledText text) {
    this.text = text;
    this.lastHeight = text.getClientArea().height;
  }

  static void install(StyledText text) {
    TransientResizeScrollFix fix = new TransientResizeScrollFix(text);
    Display display = text.getDisplay();
    Listener before = fix::beforeResize;
    display.addFilter(SWT.Resize, before);
    text.addListener(SWT.Resize, fix::afterResize);
    text.addDisposeListener(e -> display.removeFilter(SWT.Resize, before));
  }

  private void beforeResize(Event e) {
    if (e.widget == text) {
      topBeforeResize = text.getTopPixel();
    }
  }

  private void afterResize(Event e) {
    int height = text.getClientArea().height;
    int top = text.getTopPixel();
    long now = System.currentTimeMillis();
    if (height > lastHeight && top < topBeforeResize) {
      // Grew and scrolled up to fill the space below the last line
      if (restoreHeight < 0 || now > restoreUntil) {
        restoreHeight = lastHeight;
        restoreTop = topBeforeResize;
      }
      restoreUntil = now + TRANSIENT_MILLIS;
    } else if (restoreHeight >= 0) {
      if (height == restoreHeight && now <= restoreUntil) {
        text.setTopPixel(restoreTop);
      }
      if (height <= restoreHeight || now > restoreUntil) {
        restoreHeight = -1;
      }
    }
    lastHeight = height;
  }
}
