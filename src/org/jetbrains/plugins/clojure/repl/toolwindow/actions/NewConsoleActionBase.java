package org.jetbrains.plugins.clojure.repl.toolwindow.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.clojure.ClojureIcons;
import org.jetbrains.plugins.clojure.repl.REPLProvider;
import org.jetbrains.plugins.clojure.utils.Actions;

/**
 * @author Colin Fleming
 */
public abstract class NewConsoleActionBase extends AnAction implements DumbAware
{
  protected abstract REPLProvider getProvider();

  protected NewConsoleActionBase()
  {
    getTemplatePresentation().setIcon(ClojureIcons.REPL_GO);
  }

  public void update(AnActionEvent e)
  {
    Module module = Actions.getModule(e);
    Presentation presentation = e.getPresentation();
    if (module == null)
    {
      presentation.setEnabled(false);
      return;
    }

    REPLProvider provider = getProvider();
    presentation.setEnabled(provider.isSupported());
    super.update(e);
  }

  public void actionPerformed(AnActionEvent event)
  {
    Module module = Actions.getModule(event);
    assert (module != null) : "Module is null";

    // Find the tool window
    Project project = module.getProject();
    REPLProvider provider = getProvider();
    if (!provider.isSupported())
    {
      return;
    }

    provider.createREPL(project, module);
  }
}
