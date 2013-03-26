package org.jetbrains.plugins.clojure.repl;

import clojure.lang.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.highlighter.ClojureSyntaxHighlighter;
import org.jetbrains.plugins.clojure.settings.ClojureProjectSettings;
import org.jetbrains.plugins.clojure.utils.Editors;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * @author Colin Fleming
 */
public class Printing {
  public static final TextAttributes NORMAL_TEXT = ConsoleViewContentType.NORMAL_OUTPUT.getAttributes();
  public static final TextAttributes ERROR_TEXT = ConsoleViewContentType.ERROR_OUTPUT.getAttributes();
  public static final TextAttributes USER_INPUT_TEXT = ConsoleViewContentType.USER_INPUT.getAttributes();

  static void printResponse(Editor editor, Response response, boolean displayResult) {
    if (response.errorOutput() != null) {
      printToHistory(editor, response.errorOutput(), ERROR_TEXT);
    }
    if (response.stdOutput() != null) {
      printToHistory(editor, response.stdOutput(), NORMAL_TEXT);
    }

    if (displayResult) {
      for (Object value : response.values()) {
        print(editor, "=> ", USER_INPUT_TEXT);
        if (value instanceof Response.UnreadableForm) {
          print(editor, ((Response.UnreadableForm) value).form.trim(), NORMAL_TEXT);
        }
        else {
          printValue(editor, value);
        }
        print(editor, "\n", NORMAL_TEXT);
      }
    }

    Editors.scrollDown(editor);
  }

  // Based on RT.print
  @SuppressWarnings("HardCodedStringLiteral")
  public static void printValue(Editor editor, Object x) {
    // TODO add repl options for printing (*print-meta*, *print-dup*, *print-readably*)
    // TODO then add back meta code

    if (x == null) {
      print(editor, "nil", NORMAL_TEXT);
    } else if (x instanceof ISeq || x instanceof IPersistentList) {
      print(editor, "(", ClojureSyntaxHighlighter.PARENTS);
      printInnerSeq(editor, RT.seq(x));
      print(editor, ")", ClojureSyntaxHighlighter.PARENTS);
    } else if (x instanceof String) {
      CharSequence charSequence = (CharSequence) x;
      StringBuilder buffer = new StringBuilder(charSequence.length() + 8);
      buffer.append('\"');
      for (int i = 0; i < charSequence.length(); i++) {
        char c = charSequence.charAt(i);
        switch (c) {
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
      print(editor, buffer.toString(), ClojureSyntaxHighlighter.STRING);
    } else if (x instanceof IPersistentMap) {
      print(editor, "{", ClojureSyntaxHighlighter.BRACES);
      for (ISeq seq = RT.seq(x); seq != null; seq = seq.next()) {
        IMapEntry e = (IMapEntry) seq.first();
        printValue(editor, e.key());
        print(editor, " ", NORMAL_TEXT);
        printValue(editor, e.val());
        if (seq.next() != null) {
          print(editor, ", ", NORMAL_TEXT);
        }
      }
      print(editor, "}", ClojureSyntaxHighlighter.BRACES);
    } else if (x instanceof IPersistentVector) {
      IPersistentVector vector = (IPersistentVector) x;
      print(editor, "[", ClojureSyntaxHighlighter.BRACES);
      for (int i = 0; i < vector.count(); i++) {
        printValue(editor, vector.nth(i));
        if (i < vector.count() - 1) {
          print(editor, " ", NORMAL_TEXT);
        }
      }
      print(editor, "]", ClojureSyntaxHighlighter.BRACES);
    } else if (x instanceof IPersistentSet) {
      print(editor, "#{", ClojureSyntaxHighlighter.BRACES);
      for (ISeq seq = RT.seq(x); seq != null; seq = seq.next()) {
        printValue(editor, seq.first());
        if (seq.next() != null) {
          print(editor, " ", NORMAL_TEXT);
        }
      }
      print(editor, "}", ClojureSyntaxHighlighter.BRACES);
    } else if (x instanceof Character) {
      char c = ((Character) x).charValue();
      print(editor, "\\", ClojureSyntaxHighlighter.CHAR);
      switch (c) {
        case '\n':
          print(editor, "newline", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\t':
          print(editor, "tab", ClojureSyntaxHighlighter.CHAR);
          break;
        case ' ':
          print(editor, "space", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\b':
          print(editor, "backspace", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\f':
          print(editor, "formfeed", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\r':
          print(editor, "return", ClojureSyntaxHighlighter.CHAR);
          break;
        default:
          print(editor, Character.toString(c), ClojureSyntaxHighlighter.CHAR);
      }
    } else if (x instanceof Class) {
      print(editor, "#=", NORMAL_TEXT);
      print(editor, ((Class<?>) x).getName(), NORMAL_TEXT);
    } else if (x instanceof BigDecimal) {
      print(editor, x.toString(), ClojureSyntaxHighlighter.NUMBER);
      print(editor, "M", ClojureSyntaxHighlighter.NUMBER);
    } else if (x instanceof Number) {
      print(editor, x.toString(), ClojureSyntaxHighlighter.NUMBER);
    } else if (x instanceof Keyword) {
      print(editor, x.toString(), ClojureSyntaxHighlighter.KEY);
    } else if (x instanceof Symbol) {
      print(editor, x.toString(), ClojureSyntaxHighlighter.ATOM);
    } else if (x instanceof Var) {
      Var v = (Var) x;
      if(v.ns != null)
        print(editor, "#=(var " + v.ns.name + '/' + v.sym + ')', NORMAL_TEXT);
      else
        print(editor, "#=(var " + (v.sym != null ? v.sym.toString() : "--unnamed--") + ')', NORMAL_TEXT);
    } else if (x instanceof Pattern) {
      Pattern p = (Pattern) x;
      print(editor, "#\"" + p.pattern() + '\"', NORMAL_TEXT);
    } else {
      print(editor, x.toString(), NORMAL_TEXT);
    }
  }

  private static void printInnerSeq(Editor editor, ISeq x) {
    for (ISeq seq = x; seq != null; seq = seq.next()) {
      printValue(editor, seq.first());
      if (seq.next() != null) {
        print(editor, " ", NORMAL_TEXT);
      }
    }
  }

  private static void print(Editor editor, String text, TextAttributes attributes) {
    printToHistory(editor, text, attributes);
  }

  private static void print(Editor editor, String text, TextAttributesKey key) {
    EditorColorsScheme clojureScheme = EditorColorsManager.getInstance().getGlobalScheme();
    printToHistory(editor, text, clojureScheme.getAttributes(key));
  }

  public static void printToHistory(final Editor editor, final String text, final TextAttributes attributes)
  {
    ApplicationManager.getApplication().invokeLater(new Runnable()
    {
      public void run()
      {
        Document history = editor.getDocument();
        MarkupModel markupModel = DocumentMarkupModel.forDocument(history, editor.getProject(), true);
        int offset = history.getTextLength();
        appendToHistoryDocument(history, StringUtil.convertLineSeparators(text));
        markupModel.addRangeHighlighter(offset,
            history.getTextLength(),
            HighlighterLayer.SYNTAX,
            attributes,
            HighlighterTargetArea.EXACT_RANGE);
      }
    });
  }

  protected static void appendToHistoryDocument(@NotNull Document history, @NotNull String text)
  {
    history.insertString(history.getTextLength(), text);
  }

  // Copied from LanguageConsoleImpl
  static String addTextRangeToHistoryImpl(EditorEx fromEditor, Editor historyEditor, TextRange textRange) {
    Document history = historyEditor.getDocument();
    Project project = historyEditor.getProject();
    MarkupModel markupModel = DocumentMarkupModel.forDocument(history, project, true);

    int startLine = fromEditor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    int endLine = fromEditor.offsetToLogicalPosition(textRange.getEndOffset()).line;

    Document fromDocument = fromEditor.getDocument();
    String fullText = fromDocument.getText(textRange);

    for (int line = startLine; line <= endLine; line++) {
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
      while (!iterator.atEnd()) {
        int itStart = iterator.getStart();
        if (itStart > localEndOffset) {
          break;
        }
        int itEnd = iterator.getEnd();
        if (itEnd >= localStartOffset) {
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
      duplicateHighlighters(markupModel, DocumentMarkupModel.forDocument(fromDocument, project, true), offset, lineRange);
      duplicateHighlighters(markupModel, fromEditor.getMarkupModel(), offset, lineRange);
      appendToHistoryDocument(history, "\n");

      // Add REPL separators
      if (ClojureProjectSettings.getInstance(project).separateREPLItems && (line == startLine)) {
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
  private static void duplicateHighlighters(MarkupModel to, MarkupModel from, int offset, TextRange textRange) {
    for (RangeHighlighter rangeHighlighter : from.getAllHighlighters()) {
      int localOffset = textRange.getStartOffset();
      int start = Math.max(rangeHighlighter.getStartOffset(), localOffset) - localOffset;
      int end = Math.min(rangeHighlighter.getEndOffset(), textRange.getEndOffset()) - localOffset;
      if (start > end) {
        continue;
      }

      TextAttributes attributes = rangeHighlighter.getTextAttributes();
      if (!ClojureSyntaxHighlighter.ALL_KEYS.contains(attributes)) {
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
}
