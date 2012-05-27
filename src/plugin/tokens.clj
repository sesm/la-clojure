(ns plugin.tokens
  (:import (org.jetbrains.plugins.clojure.parser ClojureElementTypes)
           (org.jetbrains.plugins.clojure.lexer ClojureTokenTypes)
           (com.intellij.lang ASTNode)))

(def opening-braces #{ClojureTokenTypes/LEFT_PAREN
                      ClojureTokenTypes/LEFT_SQUARE
                      ClojureTokenTypes/LEFT_CURLY})

(def closing-braces #{ClojureTokenTypes/RIGHT_PAREN
                      ClojureTokenTypes/RIGHT_SQUARE
                      ClojureTokenTypes/RIGHT_CURLY})

(defn brace? [element]
  (or (opening-braces element)
      (closing-braces element)))

(def strings #{ClojureTokenTypes/STRING_LITERAL
               ClojureTokenTypes/WRONG_STRING_LITERAL})

(def comments #{ClojureTokenTypes/LINE_COMMENT})

(def whitespace #{ClojureTokenTypes/EOL
                  ClojureTokenTypes/EOF
                  ClojureTokenTypes/WHITESPACE
                  ClojureTokenTypes/COMMA})

(def list-like-forms #{ClojureElementTypes/LIST
                       ClojureElementTypes/VECTOR
                       ClojureElementTypes/DEF
                       ClojureElementTypes/DEFMETHOD
                       ClojureElementTypes/NS})

(def modifiers #{ClojureTokenTypes/SHARP
                 ClojureTokenTypes/UP
                 ClojureTokenTypes/SHARPUP
                 ClojureTokenTypes/TILDA
                 ClojureTokenTypes/AT
                 ClojureTokenTypes/TILDAAT
                 ClojureTokenTypes/QUOTE
                 ClojureTokenTypes/BACKQUOTE})

(defn meta-form? [element] (= ClojureElementTypes/META_FORM element))
(defn symbol-token? [element] (= ClojureElementTypes/SYMBOL element))
(defn keyword-token? [element] (= ClojureElementTypes/KEYWORD element))

(defn non-empty? [^ASTNode node] (> (.length (.trim (.getText node))) 0))
