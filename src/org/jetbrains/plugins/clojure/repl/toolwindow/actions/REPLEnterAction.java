package org.jetbrains.plugins.clojure.repl.toolwindow.actions;

import clojure.lang.Atom;
import clojure.lang.RT;
import clojure.lang.Var;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.plugins.clojure.repl.ClojureConsole;

/**
 * @author Colin Fleming
 */
public class REPLEnterAction extends EditorActionHandler implements DumbAware
{
  private final EditorActionHandler originalHandler;

  public REPLEnterAction(EditorActionHandler originalHandler)
  {
    this.originalHandler = originalHandler;
  }

  @Override
  public void execute(Editor editor, DataContext dataContext)
  {
    Atom state = editor.getUserData(ClojureConsole.STATE_KEY);
    if (state == null) {
      originalHandler.execute(editor, dataContext);
    }
    else {
      Var doExecute = RT.var("plugin.repl.toolwindow", "do-execute");
      Boolean result = (Boolean) doExecute.invoke(state, false);
      if (!result.booleanValue()) {
        originalHandler.execute(editor, dataContext);
      }
    }
  }

  @Override
  public boolean isEnabled(Editor editor, DataContext dataContext)
  {
    return originalHandler.isEnabled(editor, dataContext);
  }
}
