package org.jetbrains.plugins.clojure;

import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.io.IOException;

/**
 * @author Colin Fleming
 */
public class EditorTypingTestCase extends LightCodeInsightTestCase {
  public void doTestTyping(char ch, String before, String after) throws IOException {
    configureFromFileText("editor-typing-test.clj", before);
    EditorActionManager.getInstance().getTypedAction().actionPerformed(getEditor(), ch, DataManager.getInstance().getDataContext());
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assertEquals("Reparse error!", myEditor.getDocument().getText(), myFile.getText());
    checkResultByText(null, after, true);
  }
}
