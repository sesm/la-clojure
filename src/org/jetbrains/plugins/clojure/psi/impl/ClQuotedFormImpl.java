package org.jetbrains.plugins.clojure.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.psi.ClojurePsiElementImpl;
import org.jetbrains.plugins.clojure.psi.ClojurePsiElement;
import org.jetbrains.plugins.clojure.psi.api.ClQuotedForm;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public class ClQuotedFormImpl extends ClojurePsiElementImpl implements ClQuotedForm {
  public ClQuotedFormImpl(ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "ClQuotedForm";
  }

  @Nullable
  public ClojurePsiElement getQuotedElement() {
    return findChildByClass(ClojurePsiElement.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return true;
  }
}
