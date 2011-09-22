package org.jetbrains.plugins.clojure.repl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.plugins.clojure.file.ClojureFileType;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.settings.ClojureProjectSettings;
import org.jetbrains.plugins.clojure.utils.Editors;

public class ClojureConsole extends LanguageConsoleImpl
{
  private final ConsoleHistoryModel historyModel;
  private String currentREPLItem = null;
  private int currentREPLOffset = 0;

  public ClojureConsole(Project project, String title, ConsoleHistoryModel historyModel)
  {
    super(project, title, ClojureFileType.CLOJURE_LANGUAGE);
    this.historyModel = historyModel;
  }

  public boolean executeCurrent(boolean immediately)
  {
    REPL repl = getConsoleEditor().getUserData(REPL.REPL_KEY);
    if (repl == null)
    {
      return false;
    }

    Project project = getProject();

    Editor editor = getCurrentEditor();
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    int offset = caretModel.getOffset();
    String text = document.getText();

    if (!immediately && !"".equals(text.substring(offset).trim()))
    {
      return false;
    }

    String candidate = text.trim();

    if (ClojurePsiUtil.isValidClojureExpression(candidate, project) || "".equals(candidate))
    {
      ConsoleHistoryModel consoleHistoryModel = historyModel;

      TextRange range = new TextRange(0, document.getTextLength());
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      if (!StringUtil.isEmptyOrSpaces(candidate))
      {
        addTextRangeToHistory(getConsoleEditor(), range);
        consoleHistoryModel.addToHistory(candidate);
        Editors.scrollDown(getHistoryViewer());

        repl.execute(candidate);
      }
      setInputText("");

      Editors.scrollDown(editor);
      return true;
    }

    return false;
  }

  public void setTitle(String title)
  {
    REPL repl = getConsoleEditor().getUserData(REPL.REPL_KEY);
    if (repl == null)
    {
      return;
    }

    repl.setTabName(title);
  }

  // Copied from LanguageConsoleImpl
  public String addTextRangeToHistory(final EditorEx editor, final TextRange textRange)
  {
    final Ref<String> ref = Ref.create("");
    ApplicationManager.getApplication().runWriteAction(new Runnable()
    {
      public void run()
      {
        ref.set(addTextRangeToHistoryImpl(editor, textRange));
      }
    });
    return ref.get();
  }

  // Copied from LanguageConsoleImpl
  private String addTextRangeToHistoryImpl(EditorEx fromEditor, TextRange textRange)
  {
    Document history = getHistoryViewer().getDocument();
    MarkupModel markupModel = history.getMarkupModel(getProject());

    int startLine = fromEditor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    int endLine = fromEditor.offsetToLogicalPosition(textRange.getEndOffset()).line;

    Document fromDocument = fromEditor.getDocument();
    String fullText = fromDocument.getText(textRange);

    for (int line = startLine; line <= endLine; line++)
    {
      int lineStart = fromDocument.getLineStartOffset(line);
      int lineEnd = fromDocument.getLineEndOffset(line);

      TextRange lineRange = new TextRange(lineStart, lineEnd).intersection(textRange);
      assert lineRange != null;

      String lineText = fromDocument.getText(lineRange);
      //offset can be changed after text trimming after insert due to buffer constraints
      appendToHistoryDocument(history, lineText);
      int offset = history.getTextLength() - lineText.length();
      int localStartOffset = lineRange.getStartOffset();
      HighlighterIterator iterator = fromEditor.getHighlighter().createIterator(localStartOffset);
      int localEndOffset = lineRange.getEndOffset();
      while (!iterator.atEnd())
      {
        int itStart = iterator.getStart();
        if (itStart > localEndOffset)
        {
          break;
        }
        int itEnd = iterator.getEnd();
        if (itEnd >= localStartOffset)
        {
          int start = Math.max(itStart, localStartOffset) - localStartOffset + offset;
          int end = Math.min(itEnd, localEndOffset) - localStartOffset + offset;
          markupModel.addRangeHighlighter(start,
                                          end,
                                          HighlighterLayer.SYNTAX,
                                          iterator.getTextAttributes(),
                                          HighlighterTargetArea.EXACT_RANGE);
        }
        iterator.advance();
      }
      duplicateHighlighters(markupModel, fromDocument.getMarkupModel(getProject()), offset, lineRange);
      duplicateHighlighters(markupModel, fromEditor.getMarkupModel(), offset, lineRange);
      appendToHistoryDocument(history, "\n");

      // Add REPL separators
      if (ClojureProjectSettings.getInstance(getProject()).separateREPLItems && (line == startLine))
      {
        RangeHighlighter marker = markupModel.addRangeHighlighter(offset,
                                                                  history.getTextLength(),
                                                                  HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                  null,
                                                                  HighlighterTargetArea.EXACT_RANGE);
        EditorColorsScheme clojureScheme = EditorColorsManager.getInstance().getGlobalScheme();
        marker.setLineSeparatorColor(clojureScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR));
        marker.setLineSeparatorPlacement(SeparatorPlacement.TOP);
      }
    }

    return fullText;
  }

  // Copied from LanguageConsoleImpl
  private static void duplicateHighlighters(MarkupModel to, MarkupModel from, int offset, TextRange textRange)
  {
    EditorColorsScheme clojure = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes matched = clojure.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES);
    TextAttributes unmatched = clojure.getAttributes(CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES);

    for (RangeHighlighter rangeHighlighter : from.getAllHighlighters())
    {
      int localOffset = textRange.getStartOffset();
      int start = Math.max(rangeHighlighter.getStartOffset(), localOffset) - localOffset;
      int end = Math.min(rangeHighlighter.getEndOffset(), textRange.getEndOffset()) - localOffset;
      if (start > end)
      {
        continue;
      }

      TextAttributes attributes = rangeHighlighter.getTextAttributes();
      if (matched.equals(attributes) || unmatched.equals(attributes))
      {
        continue;
      }

      RangeHighlighter h = to.addRangeHighlighter(start + offset,
                                                  end + offset,
                                                  rangeHighlighter.getLayer(),
                                                  attributes,
                                                  rangeHighlighter.getTargetArea());
      ((RangeHighlighterEx) h).setAfterEndOfLine(((RangeHighlighterEx) rangeHighlighter).isAfterEndOfLine());
    }
  }

  public ConsoleHistoryModel getHistoryModel()
  {
    return historyModel;
  }

  public void saveCurrentREPLItem()
  {
    Editor editor = getCurrentEditor();
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    currentREPLOffset = caretModel.getOffset();
    currentREPLItem = document.getText();
  }

  // Assumed to be run in a write action
  public void restoreCurrentREPLItem()
  {
    Editor editor = getCurrentEditor();
    Document document = editor.getDocument();
    document.setText(currentREPLItem == null ? "" : currentREPLItem);
    //noinspection VariableNotUsedInsideIf
    editor.getCaretModel().moveToOffset(currentREPLItem == null ? 0 : currentREPLOffset);
  }
}
