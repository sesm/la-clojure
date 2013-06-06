package org.jetbrains.plugins.clojure.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * @author ilyas
 */
public abstract class ResolveUtil {

  public static boolean treeWalkUp(PsiElement place, PsiScopeProcessor processor, ResolveState state) {
    PsiElement lastParent = null;
    PsiElement run = place;
    while (run != null) {
      if (!run.processDeclarations(processor, state, lastParent, place)) return false;
      lastParent = run;
      run = run.getContext(); //same as getParent
    }

    return true;
  }

  public static boolean processElement(PsiScopeProcessor processor, PsiNamedElement namedElement) {
    return processElement(processor, namedElement, ResolveState.initial());
  }

  public static boolean processElement(PsiScopeProcessor processor, PsiNamedElement namedElement, ResolveState state) {
    if (namedElement == null) return true;
    String name = getName(processor, state);
    String actualName = getActualName(namedElement, state);
    if (name == null || name.equals(actualName)) {
      return processor.execute(namedElement, state);
    }
    return true;
  }

  public static String getActualName(PsiNamedElement namedElement, ResolveState state) {
    String actualName = namedElement.getName();
    final String renamed = state.get(RENAMED_KEY);
    if (renamed != null) actualName = renamed;
    return actualName;
  }

  public static boolean hasName(PsiScopeProcessor processor, ResolveState state) {
    return getName(processor, state) != null;
  }

  public static String getName(PsiScopeProcessor processor, ResolveState state) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    return nameHint == null ? null : nameHint.getName(state);
  }

  public static PsiElement[] mapToElements(ClojureResolveResult[] candidates) {
    PsiElement[] elements = new PsiElement[candidates.length];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = candidates[i].getElement();
    }

    return elements;
  }

  public static Key<String> RENAMED_KEY = Key.create("clojure.renamed.key");
}
