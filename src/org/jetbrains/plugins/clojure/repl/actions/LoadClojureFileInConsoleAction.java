package org.jetbrains.plugins.clojure.repl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.clojure.ClojureIcons;
import org.jetbrains.plugins.clojure.psi.api.ClojureFile;

/**
 * @author ilyas
 */
public class LoadClojureFileInConsoleAction extends RunActionBase
{
  public LoadClojureFileInConsoleAction()
  {
    getTemplatePresentation().setIcon(ClojureIcons.REPL_LOAD);
  }

  public void actionPerformed(AnActionEvent e)
  {
    Editor editor = e.getData(DataKeys.EDITOR);

    if (editor == null)
    {
      return;
    }
    Project project = editor.getProject();
    if (project == null)
    {
      return;
    }

    Document document = editor.getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if ((psiFile == null) || (!(psiFile instanceof ClojureFile)))
    {
      return;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null)
    {
      return;
    }
    String filePath = virtualFile.getPath();
    if (filePath == null)
    {
      return;
    }

    String command = "(load-file \"" + filePath + "\")";

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    executeCommand(project, command);
  }
}
