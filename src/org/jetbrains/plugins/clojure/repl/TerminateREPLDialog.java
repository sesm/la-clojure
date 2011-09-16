package org.jetbrains.plugins.clojure.repl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.plugins.clojure.ClojureBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author Colin Fleming
 */
public class TerminateREPLDialog extends DialogWrapper
{
  private static final int ICON_TEXT_GAP = 7;
  private final String replName;

  public TerminateREPLDialog(Project project, String replName)
  {
    super(project, true);
    this.replName = replName;
    setTitle(ClojureBundle.message("repl.is.running", replName));
    setOKButtonText(ExecutionBundle.message("button.terminate"));
    setButtonsAlignment(SwingConstants.CENTER);
    init();
  }

  @Override
  protected Action[] createActions()
  {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  protected JComponent createNorthPanel()
  {
    JLabel label = new JLabel(ClojureBundle.message("do.you.want.to.terminate.the.repl", replName));
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(label, BorderLayout.CENTER);
    Icon icon = UIUtil.getOptionPanelWarningIcon();
    if (icon != null)
    {
      label.setIcon(icon);
      label.setIconTextGap(ICON_TEXT_GAP);
    }
    return panel;
  }

  @Override
  protected JComponent createCenterPanel()
  {
    return new JPanel(new BorderLayout());
  }
}
