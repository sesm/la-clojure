package org.jetbrains.plugins.clojure.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.clojure.base.ClojureLightPlatformCodeInsightTestCase;
import org.junit.Assert;

/**
 * @author ilyas
 */
@SuppressWarnings("SpellCheckingInspection")
public class LexerTest extends ClojureLightPlatformCodeInsightTestCase {

  public void testNumeric_literals() {
    doTest("(+ 123 1N 1. 1.2 1e2 1M 1.2M 1e2M)", "( {(}\n" +
        "atom {+}\n" +
        "WHITE_SPACE { }\n" +
        "long literal {123}\n" +
        "WHITE_SPACE { }\n" +
        "big integer literal {1N}\n" +
        "WHITE_SPACE { }\n" +
        "double literal {1.}\n" +
        "WHITE_SPACE { }\n" +
        "double literal {1.2}\n" +
        "WHITE_SPACE { }\n" +
        "double literal {1e2}\n" +
        "WHITE_SPACE { }\n" +
        "big deciamel literal {1M}\n" +
        "WHITE_SPACE { }\n" +
        "big deciamel literal {1.2M}\n" +
        "WHITE_SPACE { }\n" +
        "big deciamel literal {1e2M}\n" +
        ") {)}");
  }

  public void testKeyword() {
    doTest(":sdfsd/sdfsdf/sdfsdf", "key {:sdfsd/sdfsdf/sdfsdf}");
  }

  public void testKeyword2() {
    doTest(":123", "key {:123}");
  }

  public void testKeyword3() {
    doTest(":fadfa/adfasdf:dafasdf/asdfad", "key {:fadfa/adfasdf:dafasdf/asdfad}");
  }

  public void testKeyword4() {
    doTest(":fadf#adfasdf", "key {:fadf#adfasdf}");
  }

  public void testInteger_radix() {
    doTest("36rXYZ", "long literal {36rXYZ}");
  }

  private void doTest(String fileText, String tokens) {
    Lexer lexer = new ClojureFlexLexer();
    lexer.start(fileText.trim());

    StringBuilder buffer = new StringBuilder();

    IElementType type;
    while ((type = lexer.getTokenType()) != null) {
      CharSequence s = lexer.getBufferSequence();
      s = s.subSequence(lexer.getTokenStart(), lexer.getTokenEnd());
      buffer.append(type.toString()).append(" {").append(s).append("}");
      lexer.advance();
      if (lexer.getTokenType() != null) {
        buffer.append("\n");
      }
    }

    Assert.assertEquals(tokens, buffer.toString());
  }

}