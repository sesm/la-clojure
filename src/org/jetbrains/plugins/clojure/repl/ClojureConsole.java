package org.jetbrains.plugins.clojure.repl;

import clojure.lang.Atom;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.content.Content;
import org.jetbrains.plugins.clojure.file.ClojureFileType;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.repl.toolwindow.REPLToolWindowFactory;
import org.jetbrains.plugins.clojure.utils.Editors;

public class ClojureConsole extends LanguageConsoleImpl {
  public static final Key<Content> CONTENT_KEY = Key.create("REPL.Content");
  public static final Key<Atom> STATE_KEY = Key.create(":plugin.repl.toolwindow/state");
  private String currentREPLItem = null;
  private int currentREPLOffset = 0;

  public ClojureConsole(Project project, String title) {
    super(project, title, ClojureFileType.CLOJURE_LANGUAGE);
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
