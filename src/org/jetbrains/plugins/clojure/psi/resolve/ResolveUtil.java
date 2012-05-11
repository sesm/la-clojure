package org.jetbrains.plugins.clojure.psi.resolve;

import clojure.tools.nrepl.SafeFn;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author ilyas
 */
public abstract class ResolveUtil {

  private static SafeFn punt;
  private static final Object lock = new Object();

  public static boolean treeWalkUp(PsiElement place, PsiScopeProcessor processor) {
    PsiElement lastParent = null;
    PsiElement run = place;
    while (run != null) {
      if (!run.processDeclarations(processor, ResolveState.initial(), lastParent, place)) return false;
      lastParent = run;
      run = run.getContext(); //same as getParent
    }

    return true;
  }

  public static boolean processChildren(PsiElement element, PsiScopeProcessor processor,
                                        ResolveState substitutor, PsiElement lastParent, PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
    while (run != null) {
      if (PsiTreeUtil.findCommonParent(place, run) != run && !run.processDeclarations(processor, substitutor, null, place))
        return false;
      run = run.getPrevSibling();
    }

    return true;
  }

  public static boolean processElement(PsiScopeProcessor processor, PsiNamedElement namedElement) {
    if (namedElement == null) return true;
    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(ResolveState.initial());
    if (name == null || name.equals(namedElement.getName())) {
      return processor.execute(namedElement, ResolveState.initial());
    }
    return true;
  }

  public static PsiElement[] mapToElements(ClojureResolveResult[] candidates) {
    PsiElement[] elements = new PsiElement[candidates.length];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = candidates[i].getElement();
    }

    return elements;
  }

  public static boolean processDeclarations(PsiElement element, PsiScopeProcessor processor, ResolveState state, PsiElement lastParent, PsiElement place) {
    SafeFn safeFn;
    synchronized (lock) {
      if (punt == null)
      {
        punt = SafeFn.find("plugin.resolve.core", "punt");
      }
      safeFn = punt;
    }
    Object ret = safeFn.sInvoke(element, processor, state, lastParent, place);
    return !((Boolean) ret).booleanValue();
  }
}
