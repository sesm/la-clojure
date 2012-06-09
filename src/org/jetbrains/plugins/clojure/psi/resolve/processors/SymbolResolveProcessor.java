package org.jetbrains.plugins.clojure.psi.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.NameHint;
import org.jetbrains.plugins.clojure.psi.impl.list.ListDeclarations;
import org.jetbrains.plugins.clojure.psi.resolve.ClojureResolveResultImpl;

import java.util.HashSet;
import java.util.Set;

/**
 * @author ilyas
 */
public class SymbolResolveProcessor extends ResolveProcessor implements NameHint {

  private final Set<PsiElement> myProcessedElements = new HashSet<PsiElement>();
  private final boolean onlyJava;

  public SymbolResolveProcessor(String myName, boolean onlyJava) {
    super(myName);
    this.onlyJava = onlyJava;
  }


  public boolean execute(PsiElement element, ResolveState resolveState) {
    // todo add resolve kinds
    if (onlyJava && !(element instanceof PsiClass)) return true;
    if (element instanceof PsiNamedElement && !myProcessedElements.contains(element)) {
      PsiNamedElement namedElement = (PsiNamedElement) element;
      boolean isAccessible = isAccessible();
      myCandidates.add(new ClojureResolveResultImpl(namedElement, isAccessible));
      myProcessedElements.add(namedElement);
      return !ListDeclarations.isLocal(element);
      //todo specify as it's possible!
    }

    return true;
  }

  /*
  todo: add ElementClassHints
   */
  public <T> T getHint(Key<T> hintKey) {
    if (hintKey == NameHint.KEY && myName != null) {
      return (T) this;
    }

    return null;
  }

  public String getName(ResolveState resolveState) {
    return myName;
  }

  protected boolean isAccessible() {
    //todo implement me
    return true;
  }

  public boolean shouldProcess(DeclarationKind kind) {
    return true;
  }
}
