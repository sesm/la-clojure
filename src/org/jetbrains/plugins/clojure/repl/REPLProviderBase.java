package org.jetbrains.plugins.clojure.repl;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.ExecutionHelper;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import org.jetbrains.plugins.clojure.repl.toolwindow.REPLToolWindowFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Colin Fleming
 */
public abstract class REPLProviderBase implements REPLProvider
{
  public static final String CONSOLE_TITLE = "Console title";

  public abstract boolean isSupported();

  public abstract REPL newREPL(Project project, Module module, ClojureConsoleView consoleView, String workingDir);

  public void createREPL(final Project project, Module module)
  {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(REPLToolWindowFactory.TOOL_WINDOW_ID);
    assert (toolWindow != null) : "ToolWindow is null";

    // Create the console
    ConsoleHistoryModel history = new ConsoleHistoryModel();
    ClojureConsoleView consoleView = new ClojureConsoleView(project, CONSOLE_TITLE, history);
    final ClojureConsole clojureConsole = consoleView.getConsole();

    // Create toolbar
    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("unknown", toolbarActions, false);

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(Color.WHITE);
    panel.add(actionToolbar.getComponent(), "West");
    panel.add(consoleView.getComponent(), "Center");

    // TODO
    String workingDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();

    final REPL repl = newREPL(project, module, consoleView, workingDir);

    AnAction[] actions;
    try
    {
      repl.start();
      actions = getToolbarActions(repl);
    }
    catch (REPLException e)
    {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), CONSOLE_TITLE, null);
      return;
    }

    toolbarActions.addAll(actions);

    registerActionShortcuts(actions, clojureConsole.getConsoleEditor().getComponent());
    registerActionShortcuts(actions, panel);
    panel.updateUI();

    ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final Content content = contentFactory.createContent(panel, "REPL", false);
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(content);
    content.putUserData(REPL.REPL_KEY, repl);
    clojureConsole.getConsoleEditor().putUserData(REPL.REPL_KEY, repl);
    clojureConsole.getConsoleEditor().putUserData(REPL.CONTENT_KEY, content);
    clojureConsole.getFile().putCopyableUserData(REPL.REPL_KEY, repl);

    if (toolWindow.isActive())
    {
      initContent(contentManager, content, project, repl);
    }
    else
    {
      toolWindow.activate(new Runnable()
      {
        public void run()
        {
          initContent(contentManager, content, project, repl);
        }
      });
    }

    toolWindow.show(new Runnable()
    {
      public void run()
      {
        IdeFocusManager focusManager = IdeFocusManager.getInstance(project);
        focusManager.requestFocus(clojureConsole.getCurrentEditor().getContentComponent(), true);
      }
    });
  }

  private void initContent(final ContentManager contentManager, Content content, final Project project, REPL repl)
  {
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);

    final REPLListener listener = new REPLListener(project, repl, content);
    final ProjectManager projectManager = ProjectManager.getInstance();
    projectManager.addProjectManagerListener(project, listener);
    contentManager.addContentManagerListener(listener);

    repl.onShutdown(new Runnable()
    {
      public void run()
      {
        projectManager.removeProjectManagerListener(project, listener);
        contentManager.removeContentManagerListener(listener);
      }
    });
  }

  private AnAction[] getToolbarActions(REPL repl) throws REPLException
  {
    List<AnAction> actions = new ArrayList<AnAction>();
    actions.addAll(Arrays.asList(repl.getToolbarActions()));

    ClojureConsole console = repl.getConsoleView().getConsole();
    actions.add(createConsoleHistoryAction(console, true, KeyEvent.VK_UP));
    actions.add(createConsoleHistoryAction(console, false, KeyEvent.VK_DOWN));

    return actions.toArray(new AnAction[actions.size()]);
  }

  public static void registerActionShortcuts(AnAction[] actions, JComponent component)
  {
    for (AnAction action : actions)
    {
      if (action.getShortcutSet() != null)
      {
        action.registerCustomShortcutSet(action.getShortcutSet(), component);
      }
    }
  }

  public static AnAction createConsoleHistoryAction(final ClojureConsole console, final boolean previous, int keyEvent)
  {
    AnAction action = new AnAction()
    {
      @Override
      public void actionPerformed(AnActionEvent e)
      {
        ConsoleHistoryModel historyModel = console.getHistoryModel();
        if (previous && historyModel.isEditingCurrentItem())
        {
          console.saveCurrentREPLItem();
        }

        final String text = previous ? historyModel.getHistoryPrev() : historyModel.getHistoryNext();
        new WriteCommandAction(console.getProject(), console.getFile())
        {
          @Override
          protected void run(Result result) throws Throwable
          {
            if (!previous && (text == null))
            {
              console.restoreCurrentREPLItem();
            }
            else
            {
              console.getEditorDocument().setText(text == null ? "" : text);
              console.getCurrentEditor().getCaretModel().moveToOffset(text == null ? 0 : text.length());
            }
          }
        }.execute();
      }

      @Override
      public void update(AnActionEvent e)
      {
        // Check if we have anything in history
        ConsoleHistoryModel historyModel = console.getHistoryModel();
        boolean enabled = previous ? historyModel.hasPreviousHistory() : historyModel.hasNextHistory();

        if (!enabled)
        {
          e.getPresentation().setEnabled(false);
          return;
        }

        e.getPresentation().setEnabled(canMoveInEditor(previous));
      }

      private boolean canMoveInEditor(boolean previous)
      {
        Editor consoleEditor = console.getCurrentEditor();
        Document document = consoleEditor.getDocument();
        CaretModel caretModel = consoleEditor.getCaretModel();

        if (LookupManager.getActiveLookup(consoleEditor) != null)
        {
          return false;
        }

        if (previous)
        {
          return document.getLineNumber(caretModel.getOffset()) == 0;
        }
        else
        {
          int lineCount = document.getLineCount();
          return (lineCount == 0 || document.getLineNumber(caretModel.getOffset()) == lineCount - 1) &&
                 StringUtil.isEmptyOrSpaces(document.getText().substring(caretModel.getOffset()));
        }
      }

    };
    action.registerCustomShortcutSet(keyEvent, 0, null);
    action.getTemplatePresentation().setVisible(false);
    return action;
  }

  private static class REPLListener extends ContentManagerAdapter implements ProjectManagerListener
  {
    private final Project project;
    private final REPL repl;
    private final Content content;

    private REPLListener(Project project, REPL repl, Content content)
    {
      this.project = project;
      this.repl = repl;
      this.content = content;
    }

    @Override
    public void contentRemoveQuery(ContentManagerEvent event)
    {
      if (content.equals(event.getContent()))
      {
        boolean canClose = repl.stopQuery();
        if (!canClose)
        {
          event.consume();
        }
      }
    }

    public void projectOpened(Project project)
    {
    }

    public boolean canCloseProject(Project project)
    {
      if (!this.project.equals(project))
      {
        return true;
      }

      boolean canClose = repl.stopQuery();
      if (canClose)
      {
        content.getManager().removeContent(content, true);
      }
      return canClose;
    }

    public void projectClosed(Project project)
    {
    }

    public void projectClosing(Project project)
    {
    }
  }
}
