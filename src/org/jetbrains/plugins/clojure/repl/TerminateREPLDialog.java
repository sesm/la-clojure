package org.jetbrains.plugins.clojure.repl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Colin Fleming
 */
public class TerminateREPLDialog extends DialogWrapper
{
  private static final int ICON_TEXT_GAP = 7;
  private final String message;

  public TerminateREPLDialog(Project project, String title, String message, String okText)
  {
    super(project, true);
    this.message = message;
    setTitle(title);
    setOKButtonText(okText);
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
    JLabel label = new JLabel(message);
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
