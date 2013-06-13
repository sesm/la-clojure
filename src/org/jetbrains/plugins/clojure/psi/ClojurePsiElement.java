package org.jetbrains.plugins.clojure.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.psi.api.ns.ClNs;

/**
 * @author ilyas
 */
public interface ClojurePsiElement extends PsiElement {

  @Nullable
  PsiElement getFirstNonLeafElement();

  @Nullable
  PsiElement getLastNonLeafElement();

  @Nullable
  PsiElement getNonLeafElement(int k);

  ClNs getNs();
}
