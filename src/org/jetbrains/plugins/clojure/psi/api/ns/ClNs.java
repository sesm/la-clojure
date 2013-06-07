package org.jetbrains.plugins.clojure.psi.api.ns;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.psi.api.ClList;
import org.jetbrains.plugins.clojure.psi.api.ClListLike;

/**
 * @author ilyas
 */
public interface ClNs extends ClList, PsiNamedElement {
  @Nullable
  ClListLike addImportForClass(PsiElement place, PsiClass clazz);

  boolean isClassDefinition();
}
