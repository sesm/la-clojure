package org.jetbrains.plugins.clojure.repl.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.content.Content;
import org.jetbrains.plugins.clojure.repl.ClojureConsole;
import org.jetbrains.plugins.clojure.repl.ClojureConsoleView;
import org.jetbrains.plugins.clojure.repl.REPL;
import org.jetbrains.plugins.clojure.repl.REPLException;
import org.jetbrains.plugins.clojure.repl.TerminateREPLDialog;
import org.jetbrains.plugins.clojure.repl.toolwindow.actions.ExecuteImmediatelyAction;
import org.jetbrains.plugins.clojure.repl.toolwindow.actions.HistoryNextAction;
import org.jetbrains.plugins.clojure.repl.toolwindow.actions.HistoryPreviousAction;
import org.jetbrains.plugins.clojure.utils.Editors;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * @author Colin Fleming
 */
public abstract class REPLBase implements REPL
{
  private static final Icon STOP_ICON = IconLoader.getIcon("/actions/suspend.png");
  private static final Icon CANCEL_ICON = IconLoader.getIcon("/actions/cancel.png");

  protected final Project project;
  protected final ClojureConsoleView consoleView;

  private String displayName = getType();

  private Runnable shutdownHook = null;

  public REPLBase(ClojureConsoleView consoleView, Project project)
  {
    this.consoleView = consoleView;
    this.project = project;
  }

  public abstract void start() throws REPLException;

  public abstract void doStop();

  public final void stop()
  {
    try
    {
      doStop();
    }
    finally
    {
      if (shutdownHook != null)
      {
        shutdownHook.run();
      }
    }
  }

  public boolean stopQuery()
  {
    if (!isActive())
    {
      return true;
    }

    TerminateREPLDialog dialog = new TerminateREPLDialog(project, displayName);
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE)
    {
      return false;
    }

    stop();
    return true;
  }

  public void onShutdown(Runnable runnable)
  {
    shutdownHook = runnable;
  }

  public abstract void execute(String command);

  public abstract boolean isActive();

  protected abstract String getType();

  public ClojureConsoleView getConsoleView()
  {
    return consoleView;
  }

  public AnAction[] getToolbarActions()
  {
    ClojureConsole console = consoleView.getConsole();
    return new AnAction[]{new ExecuteImmediatelyAction(console),
                          new StopAction(),
                          new CloseAction(),
                          new HistoryPreviousAction(console),
                          new HistoryNextAction(console)};
  }

  public abstract Collection<PsiNamedElement> getSymbolVariants(PsiManager manager, PsiElement symbol);

  protected void hideEditor()
  {
    ApplicationManager.getApplication().invokeLater(new Runnable()
    {
      public void run()
      {
        ClojureConsole console = consoleView.getConsole();
        JComponent component = consoleView.getComponent();
        Container parent = component.getParent();
        if (parent instanceof JPanel)
        {
          EditorEx historyViewer = console.getHistoryViewer();
          parent.add(historyViewer.getComponent());
          parent.remove(component);
          Editors.scrollDown(historyViewer);
          ((JComponent) parent).updateUI();
        }
      }
    });
  }

  protected void setEditorEnabled(boolean enabled)
  {
    consoleView.getConsole().getConsoleEditor().setRendererMode(!enabled);
    ApplicationManager.getApplication().invokeLater(new Runnable()
    {
      public void run()
      {
        consoleView.getConsole().getConsoleEditor().getComponent().updateUI();
      }
    });
  }

  public void setTabName(final String tabName)
  {
    ApplicationManager.getApplication().invokeLater(new Runnable()
    {
      public void run()
      {
        Content content = getContent();
        displayName = getType() + ": " + tabName;
        content.setDisplayName(displayName);
      }
    });
  }

  private Content getContent()
  {
    return consoleView.getConsole().getConsoleEditor().getUserData(CONTENT_KEY);
  }

  private class StopAction extends DumbAwareAction
  {
    private StopAction()
    {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM));
      Presentation templatePresentation = getTemplatePresentation();
      templatePresentation.setIcon(STOP_ICON);
      templatePresentation.setText("Stop REPL");
      templatePresentation.setDescription(null);
    }

    @Override
    public void update(AnActionEvent e)
    {
      e.getPresentation().setEnabled(isActive());
    }

    @Override
    public void actionPerformed(AnActionEvent e)
    {
      if (isActive())
      {
        stop();
      }
    }
  }

  private class CloseAction extends DumbAwareAction
  {
    private CloseAction()
    {
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE));
      Presentation templatePresentation = getTemplatePresentation();
      templatePresentation.setIcon(CANCEL_ICON);
      templatePresentation.setText("Close REPL tab");
      templatePresentation.setDescription(null);
    }

    @Override
    public void actionPerformed(AnActionEvent e)
    {
      if (isActive())
      {
        stop();
      }

      Content content = getContent();
      if (content != null)
      {
        content.getManager().removeContent(content, true);
      }
    }
  }
}
