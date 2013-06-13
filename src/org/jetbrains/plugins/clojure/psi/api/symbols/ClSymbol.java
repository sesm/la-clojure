package org.jetbrains.plugins.clojure.psi.api.symbols;

import org.jetbrains.plugins.clojure.psi.ClojurePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.plugins.clojure.psi.resolve.processors.ResolveKind;

/**
 * @author ilyas
 */
public interface ClSymbol extends ClojurePsiElement, PsiPolyVariantReference, PsiNamedElement {

  @NotNull
  String getNameString();

  @Nullable
  PsiElement getReferenceNameElement();

  @Nullable
  String getReferenceName();

  @Nullable
  ClSymbol getQualifierSymbol();

  boolean isQualified();

  @Nullable
  PsiElement getSeparatorToken();

  public ResolveKind[] getKinds();
}
