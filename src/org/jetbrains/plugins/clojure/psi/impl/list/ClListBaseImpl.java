package org.jetbrains.plugins.clojure.psi.impl.list;

import clojure.lang.RT;
import clojure.lang.Var;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.lexer.ClojureTokenTypes;
import org.jetbrains.plugins.clojure.psi.ClojureBaseElementImpl;
import org.jetbrains.plugins.clojure.psi.api.ClList;
import org.jetbrains.plugins.clojure.psi.api.symbols.ClSymbol;

import static org.jetbrains.plugins.clojure.utils.ClojureUtils.truthy;

/**
 * @author ilyas
 */
public abstract class ClListBaseImpl<T extends StubElement> extends ClojureBaseElementImpl<T> implements ClList, StubBasedPsiElement<T> {

  public ClListBaseImpl(T stub, @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public ClListBaseImpl(ASTNode node) {
    super(node);
  }

  @Nullable
  public String getPresentableText() {
    final ClSymbol first = findChildByClass(ClSymbol.class);
    if (first == null) return null;
    final String text1 = getHeadText();
    PsiElement next = PsiTreeUtil.getNextSiblingOfType(first, ClSymbol.class);
    if (next == null) return text1;
    else return text1 + " " + next.getText();
  }

  @Nullable
  public String getHeadText() {
    PsiElement first = getFirstNonLeafElement();
    if (first == null) return null;
    return first.getText();
  }

  @Nullable
  public ClSymbol getFirstSymbol() {
    PsiElement child = getFirstChild();
    while (child instanceof LeafPsiElement) {
      child = child.getNextSibling();
    }
    if (child instanceof ClSymbol) {
      return (ClSymbol) child;
    }
    return null;
  }

  @NotNull
  public PsiElement getFirstBrace() {
    PsiElement element = findChildByType(ClojureTokenTypes.LEFT_PAREN);
    if (element == null) {
      element = findChildByType(ClojureTokenTypes.SHARP_PAREN);
    }
    assert element != null;
    return element;
  }

  public PsiElement getSecondNonLeafElement() {
    PsiElement first = getFirstChild();
    while (first != null && isWrongElement(first)) {
      first = first.getNextSibling();
    }
    if (first == null) {
      return null;
    }
    PsiElement second = first.getNextSibling();
    while (second != null && isWrongElement(second)) {
      second = second.getNextSibling();
    }
    return second;
  }

  public PsiElement getLastBrace() {
    return findChildByType(ClojureTokenTypes.RIGHT_PAREN);
  }

  public ClSymbol[] getAllSymbols() {
    return findChildrenByClass(ClSymbol.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return !truthy(ResolveList.var.invoke(this, processor, state, lastParent, place));
  }

  private static class ResolveList {
    private static final Var var = RT.var("plugin.resolve.lists", "process-list-declarations");
  }
}
