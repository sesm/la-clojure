package org.jetbrains.plugins.clojure.psi.util;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.clojure.psi.api.ClList;

/**
 * @author ilyas
 */
public abstract class ClojurePsiCheckers {

  public static boolean isImportList(PsiElement elem) {
    return specificHeadText(elem, ClojureKeywords.IMPORT);
  }

  public static boolean isRequireList(PsiElement elem) {
    return specificHeadText(elem, ClojureKeywords.REQUIRE);
  }

  public static boolean isUseList(PsiElement elem) {
    return specificHeadText(elem, ClojureKeywords.USE);
  }

  private static boolean specificHeadText(PsiElement elem, String head) {
    return (elem instanceof ClList) &&
        head.equals(((ClList) elem).getHeadText());
  }

  public static boolean isImportingClause(PsiElement elem) {
    return isImportList(elem) ||
        isRequireList(elem) ||
        isUseList(elem);
  }
}
