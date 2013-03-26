package org.jetbrains.plugins.clojure.repl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.content.Content;
import org.jetbrains.plugins.clojure.file.ClojureFileType;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.utils.Editors;

public class ClojureConsole extends LanguageConsoleImpl {
  private final ConsoleHistoryModel historyModel;
  private String currentREPLItem = null;
  private int currentREPLOffset = 0;

  public ClojureConsole(Project project, String title, ConsoleHistoryModel historyModel) {
    super(project, title, ClojureFileType.CLOJURE_LANGUAGE);
    this.historyModel = historyModel;
  }

  public void printResponse(Editor editor, Response response, boolean displayResult) {
    setTitle(response.namespace());

    Printing.printResponse(editor, response, displayResult);
  }

  public boolean executeCurrent(boolean immediately) {
    REPL repl = getConsoleEditor().getUserData(REPL.REPL_KEY);
    if (repl == null) {
      return false;
    }

    Project project = getProject();

    Editor editor = getCurrentEditor();
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    int offset = caretModel.getOffset();
    String text = document.getText();

    if (!immediately && !"".equals(text.substring(offset).trim())) {
      return false;
    }

    String candidate = text.trim();

    if (ClojurePsiUtil.isValidClojureExpression(candidate, project) || "".equals(candidate)) {
      ConsoleHistoryModel consoleHistoryModel = historyModel;

      TextRange range = new TextRange(0, document.getTextLength());
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      if (!StringUtil.isEmptyOrSpaces(candidate)) {
        addTextRangeToHistory(getConsoleEditor(), ClojureConsole.this.getHistoryViewer(), range);
        consoleHistoryModel.addToHistory(candidate);
        Editors.scrollDown(getHistoryViewer());

        Response response = repl.execute(candidate);
        printResponse(getHistoryViewer(), response, true);
      }
      setInputText("");

      Editors.scrollDown(editor);
      return true;
    }

    return false;
  }

  public void setTitle(final String title) {
    final Content content = getConsoleEditor().getUserData(REPL.CONTENT_KEY);
    final REPL repl = getConsoleEditor().getUserData(REPL.REPL_KEY);
    if ((content == null) || (repl == null)) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        content.setDisplayName(repl.getType() + ": " + title);
      }
    });
  }

  // Copied from LanguageConsoleImpl
  public static String addTextRangeToHistory(final EditorEx editor, final Editor historyEditor, final TextRange textRange) {
    final Ref<String> ref = Ref.create("");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ref.set(Printing.addTextRangeToHistoryImpl(editor, historyEditor, textRange));
      }
    });
    return ref.get();
  }

  public ConsoleHistoryModel getHistoryModel() {
    return historyModel;
  }

  public void saveCurrentREPLItem() {
    Editor editor = getCurrentEditor();
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    currentREPLOffset = caretModel.getOffset();
    currentREPLItem = document.getText();
  }

  // Assumed to be run in a write action
  public void restoreCurrentREPLItem() {
    Editor editor = getCurrentEditor();
    Document document = editor.getDocument();
    document.setText(currentREPLItem == null ? "" : currentREPLItem);
    editor.getCaretModel().moveToOffset(currentREPLItem == null ? 0 : currentREPLOffset);
  }
}
