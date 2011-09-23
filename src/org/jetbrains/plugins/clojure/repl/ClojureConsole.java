package org.jetbrains.plugins.clojure.repl;

import clojure.lang.IMapEntry;
import clojure.lang.IPersistentList;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentSet;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
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
import com.intellij.ui.content.Content;
import org.jetbrains.plugins.clojure.file.ClojureFileType;
import org.jetbrains.plugins.clojure.highlighter.ClojureSyntaxHighlighter;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.settings.ClojureProjectSettings;
import org.jetbrains.plugins.clojure.utils.Editors;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public class ClojureConsole extends LanguageConsoleImpl
{
  public static final TextAttributes NORMAL_TEXT = ConsoleViewContentType.NORMAL_OUTPUT.getAttributes();
  private final ConsoleHistoryModel historyModel;
  private String currentREPLItem = null;
  private int currentREPLOffset = 0;

  public ClojureConsole(Project project, String title, ConsoleHistoryModel historyModel)
  {
    super(project, title, ClojureFileType.CLOJURE_LANGUAGE);
    this.historyModel = historyModel;
  }

  public void printResponse(Response response, boolean displayResult)
  {
    setTitle(response.namespace());

    if (response.errorOutput() != null)
    {
      printToHistory(response.errorOutput(), ConsoleViewContentType.ERROR_OUTPUT.getAttributes());
    }
    if (response.stdOutput() != null)
    {
      printToHistory(response.stdOutput(), NORMAL_TEXT);
    }

    if (displayResult)
    {
      for (Object value : response.values())
      {
        print("=> ", ConsoleViewContentType.USER_INPUT.getAttributes());
        printValue(value);
        print("\n", NORMAL_TEXT);
      }
    }

    Editors.scrollDown(getHistoryViewer());
  }

  // Based on RT.print
  @SuppressWarnings("HardCodedStringLiteral")
  private void printValue(Object x)
  {
    // TODO add repl options for printing (*print-meta*, *print-dup*, *print-readably*)
    // TODO then add back meta code

    if (x == null)
    {
      print("nil", NORMAL_TEXT);
    }
    else if (x instanceof ISeq || x instanceof IPersistentList)
    {
      print("(", ClojureSyntaxHighlighter.PARENTS);
      printInnerSeq(RT.seq(x));
      print(")", ClojureSyntaxHighlighter.PARENTS);
    }
    else if (x instanceof String)
    {
      CharSequence charSequence = (CharSequence) x;
      StringBuilder buffer = new StringBuilder(charSequence.length() + 8);
      buffer.append('\"');
      for (int i = 0; i < charSequence.length(); i++)
      {
        char c = charSequence.charAt(i);
        switch (c)
        {
          case '\n':
            buffer.append("\\n");
            break;
          case '\t':
            buffer.append("\\t");
            break;
          case '\r':
            buffer.append("\\r");
            break;
          case '"':
            buffer.append("\\\"");
            break;
          case '\\':
            buffer.append("\\\\");
            break;
          case '\f':
            buffer.append("\\f");
            break;
          case '\b':
            buffer.append("\\b");
            break;
          default:
            buffer.append(c);
        }
      }
      buffer.append('\"');
      print(buffer.toString(), ClojureSyntaxHighlighter.STRING);
    }
    else if (x instanceof IPersistentMap)
    {
      print("{", ClojureSyntaxHighlighter.BRACES);
      for (ISeq seq = RT.seq(x); seq != null; seq = seq.next())
      {
        IMapEntry e = (IMapEntry) seq.first();
        printValue(e.key());
        print(" ", NORMAL_TEXT);
        printValue(e.val());
        if (seq.next() != null)
        {
          print(", ", NORMAL_TEXT);
        }
      }
      print("}", ClojureSyntaxHighlighter.BRACES);
    }
    else if (x instanceof IPersistentVector)
    {
      IPersistentVector vector = (IPersistentVector) x;
      print("[", ClojureSyntaxHighlighter.BRACES);
      for (int i = 0; i < vector.count(); i++)
      {
        printValue(vector.nth(i));
        if (i < vector.count() - 1)
        {
          print(" ", NORMAL_TEXT);
        }
      }
      print("]", ClojureSyntaxHighlighter.BRACES);
    }
    else if (x instanceof IPersistentSet)
    {
      print("#{", ClojureSyntaxHighlighter.BRACES);
      for (ISeq seq = RT.seq(x); seq != null; seq = seq.next())
      {
        printValue(seq.first());
        if (seq.next() != null)
        {
          print(" ", NORMAL_TEXT);
        }
      }
      print("}", ClojureSyntaxHighlighter.BRACES);
    }
    else if (x instanceof Character)
    {
      char c = ((Character) x).charValue();
      print("\\", ClojureSyntaxHighlighter.CHAR);
      switch (c)
      {
        case '\n':
          print("newline", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\t':
          print("tab", ClojureSyntaxHighlighter.CHAR);
          break;
        case ' ':
          print("space", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\b':
          print("backspace", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\f':
          print("formfeed", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\r':
          print("return", ClojureSyntaxHighlighter.CHAR);
          break;
        default:
          print(Character.toString(c), ClojureSyntaxHighlighter.CHAR);
      }
    }
    else if (x instanceof Class)
    {
      print("#=", NORMAL_TEXT);
      print(((Class<?>) x).getName(), NORMAL_TEXT);
    }
    else if (x instanceof BigDecimal)
    {
      print(x.toString(), ClojureSyntaxHighlighter.NUMBER);
      print("M", ClojureSyntaxHighlighter.NUMBER);
    }
    else if (x instanceof Number)
    {
      print(x.toString(), ClojureSyntaxHighlighter.NUMBER);
    }
    else if (x instanceof Keyword)
    {
      print(x.toString(), ClojureSyntaxHighlighter.KEY);
    }
    else if (x instanceof Symbol)
    {
      print(x.toString(), ClojureSyntaxHighlighter.ATOM);
    }
    else if (x instanceof Var)
    {
      Var v = (Var) x;
      print("#=(var " + v.ns.name + '/' + v.sym + ')', NORMAL_TEXT);
    }
    else if (x instanceof Pattern)
    {
      Pattern p = (Pattern) x;
      print("#\"" + p.pattern() + '\"', NORMAL_TEXT);
    }
    else
    {
      print(x.toString(), NORMAL_TEXT);
    }
  }

  private void printInnerSeq(ISeq x)
  {
    for (ISeq seq = x; seq != null; seq = seq.next())
    {
      printValue(seq.first());
      if (seq.next() != null)
      {
        print(" ", NORMAL_TEXT);
      }
    }
  }

  private void print(String text, TextAttributes attributes)
  {
    printToHistory(text, attributes);
  }

  private void print(String text, TextAttributesKey key)
  {
    EditorColorsScheme clojureScheme = EditorColorsManager.getInstance().getGlobalScheme();
    printToHistory(text, clojureScheme.getAttributes(key));
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

        Response response = repl.execute(candidate);
        printResponse(response, true);
      }
      setInputText("");

      Editors.scrollDown(editor);
      return true;
    }

    return false;
  }

  public void setTitle(final String title)
  {
    final Content content = getConsoleEditor().getUserData(REPL.CONTENT_KEY);
    final REPL repl = getConsoleEditor().getUserData(REPL.REPL_KEY);
    if ((content == null) || (repl == null))
    {
      return;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable()
    {
      public void run()
      {
        content.setDisplayName(repl.getType() + ": " + title);
      }
    });
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
