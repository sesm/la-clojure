package org.jetbrains.plugins.clojure.repl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.plugins.clojure.ClojureBundle;
import org.jetbrains.plugins.clojure.ClojureIcons;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiElementFactoryImpl;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiFactory;

/**
 * @author ilyas
 */
public class RunSelectedTextAction extends RunActionBase
{
  public RunSelectedTextAction()
  {
    getTemplatePresentation().setIcon(ClojureIcons.REPL_EVAL);
  }

  @Override
  public void actionPerformed(AnActionEvent e)
  {
    Editor editor = e.getData(DataKeys.EDITOR);
    if (editor == null)
    {
      return;
    }
    SelectionModel selectionModel = editor.getSelectionModel();
    String selectedText = selectionModel.getSelectedText();
    if ((selectedText == null) || (selectedText.trim().length() == 0))
    {
      return;
    }
    String text = selectedText.trim();
    Project project = editor.getProject();

    final String msg = ClojurePsiFactory.getInstance(project).getErrorMessage(text);
    if (msg != null) {
      Messages.showErrorDialog(project,
                               ClojureBundle.message("evaluate.incorrect.form"),
                               ClojureBundle.message("evaluate.incorrect.cannot.evaluate", new Object[0]));

      return;
    }

    executeTextRange(editor, new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));
  }
}
