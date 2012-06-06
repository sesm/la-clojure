package org.jetbrains.plugins.clojure.findUsages;

import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;

/**
 * @author Colin Fleming
 */
public class ClojureWordsScanner implements WordsScanner {
  private final Lexer myLexer;
  private final TokenSet myIdentifierTokenSet;
  private final TokenSet myCommentTokenSet;
  private final TokenSet myLiteralTokenSet;
  public static final String MACROS = "\";'@^`~()[]{}\\%#";

  /**
   * Creates a new instance of the words scanner.
   *
   * @param lexer              the lexer used for breaking the text into tokens.
   * @param identifierTokenSet the set of token types which represent identifiers.
   * @param commentTokenSet    the set of token types which represent comments.
   * @param literalTokenSet    the set of token types which represent literals.
   */
  public ClojureWordsScanner(final Lexer lexer, final TokenSet identifierTokenSet, final TokenSet commentTokenSet,
                             final TokenSet literalTokenSet) {
    myLexer = lexer;
    myIdentifierTokenSet = identifierTokenSet;
    myCommentTokenSet = commentTokenSet;
    myLiteralTokenSet = literalTokenSet;
  }

  public void processWords(CharSequence fileText, Processor<WordOccurrence> processor) {
    myLexer.start(fileText);
    WordOccurrence occurrence = null; // shared occurrence

    IElementType type;
    while ((type = myLexer.getTokenType()) != null) {
      if (myIdentifierTokenSet.contains(type)) {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.CODE, occurrence))
          return;
      } else if (myCommentTokenSet.contains(type)) {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.COMMENTS, occurrence))
          return;
      } else if (myLiteralTokenSet.contains(type)) {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.LITERALS, occurrence))
          return;
      }
      myLexer.advance();
    }
  }

  protected static boolean stripWords(final Processor<WordOccurrence> processor,
                                      final CharSequence tokenText,
                                      int from,
                                      int to,
                                      final WordOccurrence.Kind kind,
                                      WordOccurrence occurrence) {
    // This code seems strange but it is more effective as Character.isJavaIdentifier_xxx_ is quite costly operation due to unicode
    int index = from;

    while (index < to) {
      char c = tokenText.charAt(index);
      if (isIdentifierStart(c)) {
        int identifierStart = index;
        int wordStart = -1;
        int wordEnd = -1;
        if (isWordStart(c)) {
          wordStart = index;
          index++;
          while (index < to && isWordPart(tokenText.charAt(index))) {
            index++;
          }
          wordEnd = index;
          if (!addOccurrence(tokenText, wordStart, wordEnd, kind, occurrence, processor)) return false;
        } else {
          index++;
        }
        while (index < to && isIdentifierPart(tokenText.charAt(index))) {
          if (isWordStart(tokenText.charAt(index))) {
            wordStart = index;
            index++;
            while (index < to && isWordPart(tokenText.charAt(index))) {
              index++;
            }
            wordEnd = index;
            if (!addOccurrence(tokenText, wordStart, wordEnd, kind, occurrence, processor)) return false;
          } else {
            index++;
          }
        }
        int identifierEnd = index;
        if ((identifierStart != wordStart) || (identifierEnd != wordEnd)) {
          if (!addOccurrence(tokenText, identifierStart, identifierEnd, kind, occurrence, processor)) return false;
        }
      }
      index++;
    }
    return true;
  }

  // Based on LispReader.java
  private static boolean isIdentifierStart(char c) {
    if (c >= '0' && c <= '9') return false;
    else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
    else if (Character.isWhitespace(c)) return false;
    else if (Character.isDigit(c)) return false;
    else if (MACROS.indexOf(c) >= 0) return false;
    else return true;
  }

  private static boolean isIdentifierPart(char c) {
    if (Character.isWhitespace(c)) return false;
    else if (c == '#') return true;
    else if (MACROS.indexOf(c) >= 0) return false;
    else return true;
  }

  private static boolean isWordStart(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
        (c != '$' && Character.isJavaIdentifierStart(c));
  }

  private static boolean isWordPart(char c) {
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) return true;
    return (c != '$' && Character.isJavaIdentifierPart(c));
  }

  private static boolean addOccurrence(CharSequence tokenText, int wordStart, int wordEnd, WordOccurrence.Kind kind, WordOccurrence occurrence, Processor<WordOccurrence> processor) {
    if (occurrence == null) {
      occurrence = new WordOccurrence(tokenText, wordStart, wordEnd, kind);
    } else {
      occurrence.init(tokenText, wordStart, wordEnd, kind);
    }
    return processor.process(occurrence);
  }
}
