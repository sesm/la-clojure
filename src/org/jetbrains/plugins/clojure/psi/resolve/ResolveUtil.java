package org.jetbrains.plugins.clojure.psi.resolve;

import clojure.lang.RT;
import clojure.lang.Var;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
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

  private static Var punt;
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
    return processElement(processor, namedElement, ResolveState.initial());
  }

  public static boolean processElement(PsiScopeProcessor processor, PsiNamedElement namedElement, ResolveState state) {
    if (namedElement == null) return true;
    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(ResolveState.initial());
    String actualName = namedElement.getName();
    final String renamed = state.get(RENAMED_KEY);
    if (renamed != null) actualName = renamed;
    if (name == null || name.equals(actualName)) {
      return processor.execute(namedElement, state);
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

  public static Key<String> RENAMED_KEY = Key.create("clojure.renamed.key");

  public static boolean processDeclarations(PsiElement element, PsiScopeProcessor processor, ResolveState state, PsiElement lastParent, PsiElement place) {
    Var var;
    synchronized (lock) {
      if (punt == null) {
        punt = RT.var("plugin.resolve.core", "punt");
      }
      var = punt;
    }

    Object ret;
    try {
      ret = var.invoke(element, processor, state, lastParent, place);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof ProcessCanceledException) {
        throw (ProcessCanceledException) e.getCause();
      }
      throw e;
    }
    return !((Boolean) ret).booleanValue();
  }
}
