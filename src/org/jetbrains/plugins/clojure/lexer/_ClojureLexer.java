/* The following code was generated by JFlex 1.4.1 on 3/1/13 8:50 PM */

/*
 * Copyright 2000-2009 Red Shark Technology
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.clojure.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.io.CharArrayReader;
import org.jetbrains.annotations.NotNull;


/**
 * This class is a scanner generated by 
 * <a href="http://www.jflex.de/">JFlex</a> 1.4.1
 * on 3/1/13 8:50 PM from the specification file
 * <tt>src/org/jetbrains/plugins/clojure/lexer/clojure.flex</tt>
 */
public class _ClojureLexer implements ClojureTokenTypes, FlexLexer {
  /** initial size of the lookahead buffer */
  private static final int ZZ_BUFFERSIZE = 16384;

  /** lexical states */
  public static final int YYINITIAL = 0;
  public static final int SYMBOL = 1;

  /** 
   * Translates characters to character classes
   */
  private static final String ZZ_CMAP_PACKED = 
    "\11\0\1\3\1\1\1\0\1\3\1\1\22\0\1\2\1\56\1\52"+
    "\1\37\1\51\1\41\1\42\1\35\1\27\1\30\1\56\1\21\1\4"+
    "\1\21\1\25\1\26\1\22\2\6\1\6\1\6\2\6\1\6\2\6"+
    "\1\57\1\54\1\56\1\56\1\56\1\56\1\44\3\5\1\16\1\20"+
    "\1\11\1\7\1\55\1\15\2\55\1\13\13\55\1\23\2\55\1\31"+
    "\1\45\1\32\1\40\1\51\1\36\1\60\1\5\1\5\1\16\1\17"+
    "\1\10\1\7\1\55\1\14\2\55\1\12\1\55\1\47\3\55\1\24"+
    "\1\61\1\50\1\53\2\55\1\23\2\55\1\33\1\0\1\34\1\43"+
    "\43\0\4\46\4\0\1\46\12\0\1\46\4\0\1\46\5\0\27\46"+
    "\1\0\37\46\1\0\u013f\46\31\0\162\46\4\0\14\46\16\0\5\46"+
    "\11\0\1\46\213\0\1\46\13\0\1\46\1\0\3\46\1\0\1\46"+
    "\1\0\24\46\1\0\54\46\1\0\46\46\1\0\5\46\4\0\202\46"+
    "\10\0\105\46\1\0\46\46\2\0\2\46\6\0\20\46\41\0\46\46"+
    "\2\0\1\46\7\0\47\46\110\0\33\46\5\0\3\46\56\0\32\46"+
    "\5\0\13\46\43\0\2\46\1\0\143\46\1\0\1\46\17\0\2\46"+
    "\7\0\2\46\12\0\3\46\2\0\1\46\20\0\1\46\1\0\36\46"+
    "\35\0\3\46\60\0\46\46\13\0\1\46\u0152\0\66\46\3\0\1\46"+
    "\22\0\1\46\7\0\12\46\43\0\10\46\2\0\2\46\2\0\26\46"+
    "\1\0\7\46\1\0\1\46\3\0\4\46\3\0\1\46\36\0\2\46"+
    "\1\0\3\46\16\0\4\46\21\0\6\46\4\0\2\46\2\0\26\46"+
    "\1\0\7\46\1\0\2\46\1\0\2\46\1\0\2\46\37\0\4\46"+
    "\1\0\1\46\23\0\3\46\20\0\11\46\1\0\3\46\1\0\26\46"+
    "\1\0\7\46\1\0\2\46\1\0\5\46\3\0\1\46\22\0\1\46"+
    "\17\0\2\46\17\0\1\46\23\0\10\46\2\0\2\46\2\0\26\46"+
    "\1\0\7\46\1\0\2\46\1\0\5\46\3\0\1\46\36\0\2\46"+
    "\1\0\3\46\17\0\1\46\21\0\1\46\1\0\6\46\3\0\3\46"+
    "\1\0\4\46\3\0\2\46\1\0\1\46\1\0\2\46\3\0\2\46"+
    "\3\0\3\46\3\0\10\46\1\0\3\46\77\0\1\46\13\0\10\46"+
    "\1\0\3\46\1\0\27\46\1\0\12\46\1\0\5\46\46\0\2\46"+
    "\43\0\10\46\1\0\3\46\1\0\27\46\1\0\12\46\1\0\5\46"+
    "\3\0\1\46\40\0\1\46\1\0\2\46\43\0\10\46\1\0\3\46"+
    "\1\0\27\46\1\0\20\46\46\0\2\46\43\0\22\46\3\0\30\46"+
    "\1\0\11\46\1\0\1\46\2\0\7\46\72\0\60\46\1\0\2\46"+
    "\13\0\10\46\72\0\2\46\1\0\1\46\2\0\2\46\1\0\1\46"+
    "\2\0\1\46\6\0\4\46\1\0\7\46\1\0\3\46\1\0\1\46"+
    "\1\0\1\46\2\0\2\46\1\0\4\46\1\0\2\46\11\0\1\46"+
    "\2\0\5\46\1\0\1\46\25\0\2\46\42\0\1\46\77\0\10\46"+
    "\1\0\42\46\35\0\4\46\164\0\42\46\1\0\5\46\1\0\2\46"+
    "\45\0\6\46\112\0\46\46\12\0\51\46\7\0\132\46\5\0\104\46"+
    "\5\0\122\46\6\0\7\46\1\0\77\46\1\0\1\46\1\0\4\46"+
    "\2\0\7\46\1\0\1\46\1\0\4\46\2\0\47\46\1\0\1\46"+
    "\1\0\4\46\2\0\37\46\1\0\1\46\1\0\4\46\2\0\7\46"+
    "\1\0\1\46\1\0\4\46\2\0\7\46\1\0\7\46\1\0\27\46"+
    "\1\0\37\46\1\0\1\46\1\0\4\46\2\0\7\46\1\0\47\46"+
    "\1\0\23\46\105\0\125\46\14\0\u026c\46\2\0\10\46\12\0\32\46"+
    "\5\0\113\46\3\0\3\46\17\0\15\46\1\0\4\46\16\0\22\46"+
    "\16\0\22\46\16\0\15\46\1\0\3\46\17\0\64\46\43\0\1\46"+
    "\3\0\2\46\103\0\130\46\10\0\51\46\127\0\35\46\63\0\36\46"+
    "\2\0\5\46\u038b\0\154\46\224\0\234\46\4\0\132\46\6\0\26\46"+
    "\2\0\6\46\2\0\46\46\2\0\6\46\2\0\10\46\1\0\1\46"+
    "\1\0\1\46\1\0\1\46\1\0\37\46\2\0\65\46\1\0\7\46"+
    "\1\0\1\46\3\0\3\46\1\0\7\46\3\0\4\46\2\0\6\46"+
    "\4\0\15\46\5\0\3\46\1\0\7\46\102\0\2\46\23\0\1\46"+
    "\34\0\1\46\15\0\1\46\40\0\22\46\120\0\1\46\4\0\1\46"+
    "\2\0\12\46\1\0\1\46\3\0\5\46\6\0\1\46\1\0\1\46"+
    "\1\0\1\46\1\0\4\46\1\0\3\46\1\0\7\46\3\0\3\46"+
    "\5\0\5\46\26\0\44\46\u0e81\0\3\46\31\0\11\46\7\0\5\46"+
    "\2\0\5\46\4\0\126\46\6\0\3\46\1\0\137\46\5\0\50\46"+
    "\4\0\136\46\21\0\30\46\70\0\20\46\u0200\0\u19b6\46\112\0\u51a6\46"+
    "\132\0\u048d\46\u0773\0\u2ba4\46\u215c\0\u012e\46\2\0\73\46\225\0\7\46"+
    "\14\0\5\46\5\0\1\46\1\0\12\46\1\0\15\46\1\0\5\46"+
    "\1\0\1\46\1\0\2\46\1\0\2\46\1\0\154\46\41\0\u016b\46"+
    "\22\0\100\46\2\0\66\46\50\0\15\46\66\0\2\46\30\0\3\46"+
    "\31\0\1\46\6\0\5\46\1\0\207\46\7\0\1\46\34\0\32\46"+
    "\4\0\1\46\1\0\32\46\12\0\132\46\3\0\6\46\2\0\6\46"+
    "\2\0\6\46\2\0\3\46\3\0\2\46\3\0\2\46\31\0";

  /** 
   * Translates characters to character classes
   */
  private static final char [] ZZ_CMAP = zzUnpackCMap(ZZ_CMAP_PACKED);

  /** 
   * Translates DFA states to action switch labels.
   */
  private static final int [] ZZ_ACTION = zzUnpackAction();

  private static final String ZZ_ACTION_PACKED_0 =
    "\2\0\1\1\1\2\1\3\1\4\1\5\1\4\1\5"+
    "\1\4\1\6\1\7\1\10\1\11\1\12\1\13\1\14"+
    "\1\15\1\16\1\17\1\20\1\21\1\22\1\23\1\1"+
    "\2\4\1\24\1\25\1\1\1\26\1\6\1\27\1\30"+
    "\1\31\1\32\1\33\1\34\1\5\1\35\4\0\1\4"+
    "\1\0\1\6\1\36\2\21\1\37\2\40\2\4\1\0"+
    "\1\41\1\42\1\0\1\43\1\0\1\5\1\43\2\44"+
    "\1\4\1\5\1\45\1\4\1\0\1\43\1\5\2\0"+
    "\1\4\1\5\1\46\1\42\2\44\1\47";

  private static int [] zzUnpackAction() {
    int [] result = new int[81];
    int offset = 0;
    offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAction(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /** 
   * Translates a state to a row index in the transition table
   */
  private static final int [] ZZ_ROWMAP = zzUnpackRowMap();

  private static final String ZZ_ROWMAP_PACKED_0 =
    "\0\0\0\62\0\144\0\226\0\144\0\310\0\372\0\u012c"+
    "\0\u015e\0\u0190\0\310\0\144\0\144\0\144\0\144\0\144"+
    "\0\144\0\144\0\144\0\u01c2\0\144\0\u01f4\0\u0226\0\144"+
    "\0\u0258\0\u028a\0\u02bc\0\u02ee\0\u0320\0\u0352\0\144\0\u0384"+
    "\0\144\0\144\0\144\0\144\0\144\0\144\0\144\0\144"+
    "\0\u03b6\0\u03e8\0\u041a\0\u044c\0\u047e\0\u04b0\0\u04e2\0\144"+
    "\0\u0514\0\144\0\144\0\144\0\u0546\0\u0578\0\u05aa\0\u05dc"+
    "\0\144\0\u060e\0\u0640\0\u0672\0\u06a4\0\u06d6\0\u0708\0\u073a"+
    "\0\u076c\0\u079e\0\u07d0\0\310\0\u0802\0\u0834\0\144\0\u0866"+
    "\0\u0898\0\u08ca\0\u08fc\0\u092e\0\310\0\u0960\0\u0898\0\u08ca"+
    "\0\310";

  private static int [] zzUnpackRowMap() {
    int [] result = new int[81];
    int offset = 0;
    offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackRowMap(String packed, int offset, int [] result) {
    int i = 0;  /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int high = packed.charAt(i++) << 16;
      result[j++] = high | packed.charAt(i++);
    }
    return j;
  }

  /** 
   * The transition table of the DFA
   */
  private static final int [] ZZ_TRANS = zzUnpackTrans();

  private static final String ZZ_TRANS_PACKED_0 =
    "\1\3\3\4\1\5\1\6\1\7\1\6\1\10\11\6"+
    "\1\11\2\6\1\12\1\13\1\14\1\15\1\16\1\17"+
    "\1\20\1\21\1\22\1\23\1\24\1\25\1\26\1\6"+
    "\1\27\1\30\1\31\1\3\1\32\1\33\1\6\1\34"+
    "\1\6\1\35\2\6\1\36\2\6\5\37\20\40\1\41"+
    "\1\42\6\37\1\40\1\37\1\43\2\37\1\40\4\37"+
    "\3\40\1\37\1\40\1\37\5\40\63\0\3\4\63\0"+
    "\20\6\1\13\1\6\10\0\1\6\2\0\1\6\4\0"+
    "\3\6\1\0\1\6\1\0\2\6\1\0\2\6\6\0"+
    "\1\7\1\44\2\45\2\46\2\47\1\50\2\51\1\0"+
    "\1\7\1\0\1\52\1\53\1\54\40\0\20\6\1\13"+
    "\1\6\10\0\1\6\2\0\1\6\4\0\3\6\1\0"+
    "\1\6\1\0\2\6\1\0\1\55\1\6\6\0\1\7"+
    "\1\44\2\45\2\46\2\47\1\50\2\51\1\0\1\7"+
    "\1\56\1\52\1\53\1\54\40\0\1\57\1\6\13\57"+
    "\1\6\4\57\10\0\1\6\2\0\1\57\4\0\3\57"+
    "\1\0\1\57\1\0\2\57\1\0\2\57\40\0\1\60"+
    "\27\0\1\61\13\0\1\61\17\0\1\62\63\0\1\63"+
    "\15\0\1\64\2\0\2\64\1\65\1\64\12\65\2\64"+
    "\2\65\21\64\4\65\1\64\1\65\1\64\1\65\2\64"+
    "\2\65\5\0\7\6\1\66\10\6\1\13\1\6\10\0"+
    "\1\6\2\0\1\6\4\0\3\6\1\0\1\6\1\0"+
    "\2\6\1\0\2\6\5\0\17\6\1\67\1\13\1\6"+
    "\10\0\1\6\2\0\1\6\4\0\3\6\1\0\1\6"+
    "\1\0\2\6\1\0\2\6\45\34\1\70\4\34\1\71"+
    "\7\34\1\35\1\0\60\35\1\72\3\0\22\72\1\73"+
    "\10\0\1\72\2\0\1\72\3\0\4\72\1\0\1\72"+
    "\1\0\5\72\5\0\20\40\10\0\1\40\1\0\1\43"+
    "\2\0\1\40\4\0\3\40\1\0\1\40\1\0\5\40"+
    "\6\0\1\74\12\0\1\75\1\74\44\0\14\76\1\0"+
    "\3\76\22\0\2\76\2\0\1\76\1\0\1\76\2\0"+
    "\2\76\6\0\1\77\13\0\1\77\45\0\1\100\13\0"+
    "\1\101\44\0\5\6\1\102\12\6\1\13\1\6\10\0"+
    "\1\6\2\0\1\6\4\0\3\6\1\0\1\6\1\0"+
    "\2\6\1\0\2\6\5\0\2\103\1\0\2\103\4\0"+
    "\3\103\1\0\1\103\35\0\1\103\6\0\22\57\10\0"+
    "\1\57\2\0\1\57\4\0\3\57\1\0\1\57\1\0"+
    "\2\57\1\0\2\57\6\0\1\61\13\0\1\61\44\0"+
    "\1\65\1\0\12\65\2\0\2\65\21\0\4\65\1\0"+
    "\1\65\1\0\1\65\2\0\2\65\5\0\5\6\1\104"+
    "\12\6\1\13\1\6\10\0\1\6\2\0\1\6\4\0"+
    "\3\6\1\0\1\6\1\0\2\6\1\0\2\6\5\0"+
    "\20\6\1\13\1\6\10\0\1\6\2\0\1\6\4\0"+
    "\3\6\1\0\1\105\1\0\2\6\1\0\2\6\2\34"+
    "\1\0\57\34\1\72\3\0\22\72\1\106\10\0\1\72"+
    "\2\0\1\72\3\0\4\72\1\0\1\72\1\0\5\72"+
    "\1\73\3\0\22\73\1\106\10\0\1\73\2\0\1\73"+
    "\3\0\4\73\1\0\1\73\1\0\5\73\6\0\1\74"+
    "\1\107\2\45\4\0\1\50\3\0\1\74\45\0\1\74"+
    "\13\0\1\74\44\0\12\76\2\110\1\0\3\76\1\53"+
    "\1\54\20\0\2\76\2\0\1\76\1\0\1\76\2\0"+
    "\2\76\6\0\1\77\1\107\2\45\4\0\1\50\2\51"+
    "\1\0\1\77\45\0\1\100\13\0\1\100\1\0\1\111"+
    "\43\0\1\100\13\0\1\100\1\112\1\111\42\0\20\6"+
    "\1\13\1\6\10\0\1\6\2\0\1\6\4\0\3\6"+
    "\1\0\1\6\1\0\2\6\1\0\1\6\1\113\5\0"+
    "\2\103\1\44\2\103\2\46\2\47\1\103\2\114\1\0"+
    "\1\103\2\0\1\53\1\54\31\0\1\103\6\0\12\6"+
    "\1\115\5\6\1\13\1\6\10\0\1\6\2\0\1\6"+
    "\4\0\3\6\1\0\1\6\1\0\2\6\1\0\2\6"+
    "\1\72\3\116\2\72\1\73\13\72\1\73\3\72\1\106"+
    "\10\116\1\72\2\116\1\72\3\116\4\72\1\116\1\72"+
    "\1\116\5\72\5\0\12\76\2\110\1\75\3\76\1\53"+
    "\1\54\20\0\2\76\2\0\1\76\1\0\1\76\2\0"+
    "\2\76\5\0\14\117\1\0\3\117\22\0\2\117\2\0"+
    "\1\117\1\0\1\117\2\0\2\117\5\0\2\120\1\0"+
    "\2\120\4\0\3\120\1\0\1\120\35\0\1\120\6\0"+
    "\12\6\1\121\5\6\1\13\1\6\10\0\1\6\2\0"+
    "\1\6\4\0\3\6\1\0\1\6\1\0\2\6\1\0"+
    "\2\6\5\0\2\103\1\44\2\103\2\46\2\47\1\103"+
    "\2\114\1\75\1\103\2\0\1\53\1\54\31\0\1\103"+
    "\1\0\1\116\3\0\22\116\11\0\1\116\2\0\1\116"+
    "\3\0\4\116\1\0\1\116\1\0\5\116";

  private static int [] zzUnpackTrans() {
    int [] result = new int[2450];
    int offset = 0;
    offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackTrans(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      value--;
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /* error codes */
  private static final int ZZ_UNKNOWN_ERROR = 0;
  private static final int ZZ_NO_MATCH = 1;
  private static final int ZZ_PUSHBACK_2BIG = 2;
  private static final char[] EMPTY_BUFFER = new char[0];
  private static final int YYEOF = -1;
  private static java.io.Reader zzReader = null; // Fake

  /* error messages for the codes above */
  private static final String ZZ_ERROR_MSG[] = {
    "Unkown internal scanner error",
    "Error: could not match input",
    "Error: pushback value was too large"
  };

  /**
   * ZZ_ATTRIBUTE[aState] contains the attributes of state <code>aState</code>
   */
  private static final int [] ZZ_ATTRIBUTE = zzUnpackAttribute();

  private static final String ZZ_ATTRIBUTE_PACKED_0 =
    "\2\0\1\11\1\1\1\11\6\1\10\11\1\1\1\11"+
    "\2\1\1\11\6\1\1\11\1\1\10\11\4\0\1\1"+
    "\1\0\1\1\1\11\1\1\3\11\3\1\1\0\1\11"+
    "\1\1\1\0\1\1\1\0\10\1\1\0\1\11\1\1"+
    "\2\0\7\1";

  private static int [] zzUnpackAttribute() {
    int [] result = new int[81];
    int offset = 0;
    offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAttribute(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }

  /** the current state of the DFA */
  private int zzState;

  /** the current lexical state */
  private int zzLexicalState = YYINITIAL;

  /** this buffer contains the current text to be matched and is
      the source of the yytext() string */
  private CharSequence zzBuffer = "";

  /** this buffer may contains the current text array to be matched when it is cheap to acquire it */
  private char[] zzBufferArray;

  /** the textposition at the last accepting state */
  private int zzMarkedPos;

  /** the textposition at the last state to be included in yytext */
  private int zzPushbackPos;

  /** the current text position in the buffer */
  private int zzCurrentPos;

  /** startRead marks the beginning of the yytext() string in the buffer */
  private int zzStartRead;

  /** endRead marks the last character in the buffer, that has been read
      from input */
  private int zzEndRead;

  /**
   * zzAtBOL == true <=> the scanner is currently at the beginning of a line
   */
  private boolean zzAtBOL = true;

  /** zzAtEOF == true <=> the scanner is at the EOF */
  private boolean zzAtEOF;

  /** denotes if the user-EOF-code has already been executed */
  private boolean zzEOFDone;

  /* user code: */
  /*
  public final int getTokenStart(){
    return zzStartRead;
  }

  public final int getTokenEnd(){
    return getTokenStart() + yylength();
  }

  public void reset(CharSequence buffer, int start, int end,int initialState) {
    char [] buf = buffer.toString().substring(start,end).toCharArray();
    yyreset( new CharArrayReader( buf ) );
    yybegin(initialState);
  }
  
  public void reset(CharSequence buffer, int initialState){
    reset(buffer, 0, buffer.length(), initialState);
  }
  */


  public _ClojureLexer(java.io.Reader in) {
    this.zzReader = in;
  }

  /**
   * Creates a new scanner.
   * There is also java.io.Reader version of this constructor.
   *
   * @param   in  the java.io.Inputstream to read input from.
   */
  public _ClojureLexer(java.io.InputStream in) {
    this(new java.io.InputStreamReader(in));
  }

  /** 
   * Unpacks the compressed character translation table.
   *
   * @param packed   the packed character translation table
   * @return         the unpacked character translation table
   */
  private static char [] zzUnpackCMap(String packed) {
    char [] map = new char[0x10000];
    int i = 0;  /* index in packed string  */
    int j = 0;  /* index in unpacked array */
    while (i < 1318) {
      int  count = packed.charAt(i++);
      char value = packed.charAt(i++);
      do map[j++] = value; while (--count > 0);
    }
    return map;
  }

  public final int getTokenStart(){
    return zzStartRead;
  }

  public final int getTokenEnd(){
    return getTokenStart() + yylength();
  }

  public void reset(CharSequence buffer, int start, int end,int initialState){
    zzBuffer = buffer;
    zzBufferArray = com.intellij.util.text.CharArrayUtil.fromSequenceWithoutCopying(buffer);
    zzCurrentPos = zzMarkedPos = zzStartRead = start;
    zzPushbackPos = 0;
    zzAtEOF  = false;
    zzAtBOL = true;
    zzEndRead = end;
    yybegin(initialState);
  }

  // For Demetra compatibility
  public void reset(CharSequence buffer, int initialState){
    zzBuffer = buffer;
    zzBufferArray = null;
    zzCurrentPos = zzMarkedPos = zzStartRead = 0;
    zzPushbackPos = 0;
    zzAtEOF = false;
    zzAtBOL = true;
    zzEndRead = buffer.length();
    yybegin(initialState);
  }

  /**
   * Refills the input buffer.
   *
   * @return      <code>false</code>, iff there was new input.
   *
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  private boolean zzRefill() throws java.io.IOException {
    return true;
  }


  /**
   * Returns the current lexical state.
   */
  public final int yystate() {
    return zzLexicalState;
  }


  /**
   * Enters a new lexical state
   *
   * @param newState the new lexical state
   */
  public final void yybegin(int newState) {
    zzLexicalState = newState;
  }


  /**
   * Returns the text matched by the current regular expression.
   */
  public final CharSequence yytext() {
    return zzBuffer.subSequence(zzStartRead, zzMarkedPos);
  }


  /**
   * Returns the character at position <tt>pos</tt> from the
   * matched text.
   *
   * It is equivalent to yytext().charAt(pos), but faster
   *
   * @param pos the position of the character to fetch.
   *            A value from 0 to yylength()-1.
   *
   * @return the character at position pos
   */
  public final char yycharat(int pos) {
    return zzBufferArray != null ? zzBufferArray[zzStartRead+pos]:zzBuffer.charAt(zzStartRead+pos);
  }


  /**
   * Returns the length of the matched text region.
   */
  public final int yylength() {
    return zzMarkedPos-zzStartRead;
  }


  /**
   * Reports an error that occured while scanning.
   *
   * In a wellformed scanner (no or only correct usage of
   * yypushback(int) and a match-all fallback rule) this method
   * will only be called with things that "Can't Possibly Happen".
   * If this method is called, something is seriously wrong
   * (e.g. a JFlex bug producing a faulty scanner etc.).
   *
   * Usual syntax/scanner level error handling should be done
   * in error fallback rules.
   *
   * @param   errorCode  the code of the errormessage to display
   */
  private void zzScanError(int errorCode) {
    String message;
    try {
      message = ZZ_ERROR_MSG[errorCode];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR];
    }

    throw new Error(message);
  }


  /**
   * Pushes the specified amount of characters back into the input stream.
   *
   * They will be read again by then next call of the scanning method
   *
   * @param number  the number of characters to be read again.
   *                This number must not be greater than yylength()!
   */
  public void yypushback(int number)  {
    if ( number > yylength() )
      zzScanError(ZZ_PUSHBACK_2BIG);

    zzMarkedPos -= number;
  }


  /**
   * Contains user EOF-code, which will be executed exactly once,
   * when the end of file is reached
   */
  private void zzDoEOF() {
    if (!zzEOFDone) {
      zzEOFDone = true;
    
    }
  }


  /**
   * Resumes scanning until the next regular expression is matched,
   * the end of input is encountered or an I/O-Error occurs.
   *
   * @return      the next token
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  public IElementType advance() throws java.io.IOException {
    int zzInput;
    int zzAction;

    // cached fields:
    int zzCurrentPosL;
    int zzMarkedPosL;
    int zzEndReadL = zzEndRead;
    CharSequence zzBufferL = zzBuffer;
    char[] zzBufferArrayL = zzBufferArray;
    char [] zzCMapL = ZZ_CMAP;

    int [] zzTransL = ZZ_TRANS;
    int [] zzRowMapL = ZZ_ROWMAP;
    int [] zzAttrL = ZZ_ATTRIBUTE;

    while (true) {
      zzMarkedPosL = zzMarkedPos;

      zzAction = -1;

      zzCurrentPosL = zzCurrentPos = zzStartRead = zzMarkedPosL;

      zzState = zzLexicalState;


      zzForAction: {
        while (true) {

          if (zzCurrentPosL < zzEndReadL)
            zzInput = zzBufferL.charAt(zzCurrentPosL++);
          else if (zzAtEOF) {
            zzInput = YYEOF;
            break zzForAction;
          }
          else {
            // store back cached positions
            zzCurrentPos  = zzCurrentPosL;
            zzMarkedPos   = zzMarkedPosL;
            boolean eof = zzRefill();
            // get translated positions and possibly new buffer
            zzCurrentPosL  = zzCurrentPos;
            zzMarkedPosL   = zzMarkedPos;
            zzBufferL      = zzBuffer;
            zzEndReadL     = zzEndRead;
            if (eof) {
              zzInput = YYEOF;
              break zzForAction;
            }
            else {
              zzInput = zzBufferL.charAt(zzCurrentPosL++);
            }
          }
          int zzNext = zzTransL[ zzRowMapL[zzState] + zzCMapL[zzInput] ];
          if (zzNext == -1) break zzForAction;
          zzState = zzNext;

          int zzAttributes = zzAttrL[zzState];
          if ( (zzAttributes & 1) == 1 ) {
            zzAction = zzState;
            zzMarkedPosL = zzCurrentPosL;
            if ( (zzAttributes & 8) == 8 ) break zzForAction;
          }

        }
      }

      // store back cached position
      zzMarkedPos = zzMarkedPosL;

      switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
        case 30: 
          { return SHARPUP;
          }
        case 40: break;
        case 12: 
          { return RIGHT_CURLY;
          }
        case 41: break;
        case 17: 
          { return symIMPLICIT_ARG;
          }
        case 42: break;
        case 15: 
          { return SHARP;
          }
        case 43: break;
        case 5: 
          { return INTEGER_LITERAL;
          }
        case 44: break;
        case 34: 
          { return COLON_SYMBOL;
          }
        case 45: break;
        case 28: 
          { return LONG_LITERAL;
          }
        case 46: break;
        case 23: 
          { return symDOT;
          }
        case 47: break;
        case 35: 
          { return BIG_DECIMAL_LITERAL;
          }
        case 48: break;
        case 14: 
          { return BACKQUOTE;
          }
        case 49: break;
        case 25: 
          { yybegin(YYINITIAL); return symATOM;
          }
        case 50: break;
        case 39: 
          { return FALSE;
          }
        case 51: break;
        case 7: 
          { return LEFT_PAREN;
          }
        case 52: break;
        case 6: 
          { return symATOM;
          }
        case 53: break;
        case 22: 
          { yypushback(yytext().length()); yybegin(YYINITIAL);
          }
        case 54: break;
        case 27: 
          { return FLOAT_LITERAL;
          }
        case 55: break;
        case 3: 
          { return COMMA;
          }
        case 56: break;
        case 16: 
          { return UP;
          }
        case 57: break;
        case 19: 
          { return AT;
          }
        case 58: break;
        case 20: 
          { return WRONG_STRING_LITERAL;
          }
        case 59: break;
        case 18: 
          { return TILDA;
          }
        case 60: break;
        case 33: 
          { return STRING_LITERAL;
          }
        case 61: break;
        case 31: 
          { return TILDAAT;
          }
        case 62: break;
        case 26: 
          { return BIG_INT_LITERAL;
          }
        case 63: break;
        case 10: 
          { return RIGHT_SQUARE;
          }
        case 64: break;
        case 2: 
          { return WHITESPACE;
          }
        case 65: break;
        case 21: 
          { return LINE_COMMENT;
          }
        case 66: break;
        case 13: 
          { return QUOTE;
          }
        case 67: break;
        case 29: 
          { return DOUBLE_LITERAL;
          }
        case 68: break;
        case 8: 
          { return RIGHT_PAREN;
          }
        case 69: break;
        case 36: 
          { return RATIO;
          }
        case 70: break;
        case 24: 
          { return symNS_SEP;
          }
        case 71: break;
        case 11: 
          { return LEFT_CURLY;
          }
        case 72: break;
        case 32: 
          { return CHAR_LITERAL;
          }
        case 73: break;
        case 37: 
          { return NIL;
          }
        case 74: break;
        case 38: 
          { return TRUE;
          }
        case 75: break;
        case 1: 
          { return BAD_CHARACTER;
          }
        case 76: break;
        case 9: 
          { return LEFT_SQUARE;
          }
        case 77: break;
        case 4: 
          { yypushback(yytext().length()); yybegin(SYMBOL);
          }
        case 78: break;
        default:
          if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
            zzAtEOF = true;
            zzDoEOF();
            return null;
          }
          else {
            zzScanError(ZZ_NO_MATCH);
          }
      }
    }
  }


}
