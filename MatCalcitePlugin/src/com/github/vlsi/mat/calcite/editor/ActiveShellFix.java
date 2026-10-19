package com.github.vlsi.mat.calcite.editor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * Works around SWT on GTK losing track of the active window, which makes Backspace, Delete and the arrow keys stop
 * working in the query editor while letters still type.
 *
 * <p>When MAT shows a table result it runs a "Retrieving view elements..." user job. Eclipse opens a progress dialog
 * for it, and when the job finishes quickly the dialog is opened and closed within the same call, without ever
 * getting keyboard focus. Opening it still makes SWT record it as the active shell, and closing it sets
 * {@code Display.getActiveShell()} to null. Only a new focus-in event on the workbench window restores it. The window
 * never lost focus, so on Wayland that event does not come. While the active shell is null,
 * {@code Display.getFocusControl()} is null and SWT drops every key press that is not committed text.
 *
 * <p>The fix tracks whether the workbench window itself was deactivated. If a child shell is disposed and leaves no
 * active shell while the window was never deactivated, the window still has focus, so it is re-activated. If the
 * user switched to another application in between, the window got a Deactivate event and nothing is done.
 */
final class ActiveShellFix {
  private static final String KEY = ActiveShellFix.class.getName();

  private final Shell shell;
  private boolean shellActive;

  private ActiveShellFix(Shell shell) {
    this.shell = shell;
    this.shellActive = shell.getDisplay().getActiveShell() == shell;
  }

  /**
   * Installs the fix for the given workbench window, once per window.
   */
  static void install(Shell shell) {
    if (shell.getData(KEY) != null) {
      return;
    }
    ActiveShellFix fix = new ActiveShellFix(shell);
    shell.setData(KEY, fix);
    Display display = shell.getDisplay();
    Listener listener = fix::handleEvent;
    display.addFilter(SWT.Activate, listener);
    display.addFilter(SWT.Deactivate, listener);
    display.addFilter(SWT.Dispose, listener);
    shell.addDisposeListener(e -> {
      display.removeFilter(SWT.Activate, listener);
      display.removeFilter(SWT.Deactivate, listener);
      display.removeFilter(SWT.Dispose, listener);
    });
  }

  private void handleEvent(org.eclipse.swt.widgets.Event e) {
    if (e.widget == shell) {
      if (e.type == SWT.Activate) {
        shellActive = true;
      } else if (e.type == SWT.Deactivate) {
        shellActive = false;
      }
      return;
    }
    if (e.type != SWT.Dispose || !(e.widget instanceof Shell) || ((Shell) e.widget).getParent() != shell) {
      return;
    }
    // The active shell is cleared while the child shell is released, after this event, so check afterwards
    Display display = shell.getDisplay();
    display.asyncExec(() -> {
      if (!shell.isDisposed() && shellActive && display.getActiveShell() == null) {
        shell.forceActive();
      }
    });
  }
}
