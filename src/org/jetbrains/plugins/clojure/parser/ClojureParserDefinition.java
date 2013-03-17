package org.jetbrains.plugins.clojure.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.lexer.ClojureFlexLexer;
import org.jetbrains.plugins.clojure.lexer.ClojureTokenTypes;
import org.jetbrains.plugins.clojure.psi.impl.ClojureFileImpl;


/**
 * User: peter
 * Date: Nov 20, 2008
 * Time: 11:10:44 AM
 * Copyright 2007, 2008, 2009 Red Shark Technology
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ClojureParserDefinition implements ParserDefinition {

  @NotNull
  public Lexer createLexer(Project project) {
    return new ClojureFlexLexer();
  }

  public PsiParser createParser(Project project) {
    return new ClojureParser();
  }

  public IFileElementType getFileNodeType() {
    return ClojureElementTypes.FILE;
  }

  @NotNull
  public TokenSet getWhitespaceTokens() {
    return ClojureTokenTypes.WHITESPACE_SET;
  }

  @NotNull
  public TokenSet getCommentTokens() {
    return ClojureTokenTypes.COMMENTS;
  }

  @NotNull
  public TokenSet getStringLiteralElements() {
    return ClojureTokenTypes.STRINGS;
  }

  @NotNull
  public PsiElement createElement(ASTNode node) {
    return ClojurePsiCreator.createElement(node);
  }

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {

    IElementType leftType = left.getElementType();
    if (leftType == ClojureTokenTypes.QUOTE
        || leftType == ClojureTokenTypes.SHARP
        || leftType == ClojureTokenTypes.SHARPUP
        ) {

      return SpaceRequirements.MUST_NOT;

    } else {
      IElementType rightType = right.getElementType();
      if (leftType == ClojureTokenTypes.LEFT_PAREN
          || rightType == ClojureTokenTypes.RIGHT_PAREN
          || leftType == ClojureTokenTypes.RIGHT_PAREN
          || rightType == ClojureTokenTypes.LEFT_PAREN
          || leftType == ClojureTokenTypes.SHARP_PAREN
          || rightType == ClojureTokenTypes.SHARP_PAREN
          || leftType == ClojureTokenTypes.SHARP_CURLY
          || rightType == ClojureTokenTypes.SHARP_CURLY

          || leftType == ClojureTokenTypes.LEFT_CURLY
          || rightType == ClojureTokenTypes.RIGHT_CURLY
          || leftType == ClojureTokenTypes.RIGHT_CURLY
          || rightType == ClojureTokenTypes.LEFT_CURLY

          || leftType == ClojureTokenTypes.LEFT_SQUARE
          || rightType == ClojureTokenTypes.RIGHT_SQUARE
          || leftType == ClojureTokenTypes.RIGHT_SQUARE
          || rightType == ClojureTokenTypes.LEFT_SQUARE) {

        return SpaceRequirements.MAY;
      }
    }
    return SpaceRequirements.MUST;
  }

  public PsiFile createFile(FileViewProvider viewProvider) {
    return new ClojureFileImpl(viewProvider);
  }
}

