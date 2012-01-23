package org.jetbrains.plugins.clojure;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.io.IOException;

/**
 * @author Colin Fleming
 */
public abstract class EditorModificationTestCase extends LightCodeInsightTestCase {

  public abstract void doModification(Project project, Editor editor, PsiFile file, DataContext dataContext);

  public void doModificationTest(String before, String after) throws IOException {
    configureFromFileText("editor-modification-test.clj", before);
    doModification(getProject(), getEditor(), getFile(), DataManager.getInstance().getDataContext());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals("Reparse error!", myEditor.getDocument().getText(), myFile.getText());
    checkResultByText(null, after, false);
  }
}
