package org.jetbrains.plugins.clojure.psi.impl.list;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.plugins.clojure.psi.api.ClList;
import org.jetbrains.plugins.clojure.psi.api.ClVector;
import org.jetbrains.plugins.clojure.psi.api.symbols.ClSymbol;
import org.jetbrains.plugins.clojure.psi.impl.symbols.ClSymbolImpl;
import org.jetbrains.plugins.clojure.psi.resolve.ResolveUtil;

/**
 * @author ilyas
 */
public class ListDeclarations {

  public static final String FN = "fn";
  public static final String DEFN = "defn";
  public static final String DEFN_ = "defn-";

  public static final String NS = "ns";

  public static final String IMPORT = "import";
  public static final String USE = "use";
  public static final String REFER = "refer";
  public static final String REQUIRE = "require";

  // TODO is this right?
  public static boolean processDeclareDeclaration(PsiScopeProcessor processor, ClList list, PsiElement place, PsiElement lastParent) {
    final ClVector paramVector = list.findFirstChildByClass(ClVector.class);
    if (paramVector != null) {
      for (ClSymbol symbol : paramVector.getOddSymbols()) {
        if (!ResolveUtil.processElement(processor, symbol)) return false;
      }
    }
    return true;
  }


  public static boolean processDotDeclaration(PsiScopeProcessor processor, ClList list, PsiElement place, PsiElement lastParent) {
    final PsiElement parent = place.getParent();
    if (parent == null || list == parent) return true;

    final PsiElement second = list.getSecondNonLeafElement();
    if (second instanceof ClSymbol && place != second) {
      ClSymbol symbol = (ClSymbol) second;
      for (ResolveResult result : symbol.multiResolve(false)) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiNamedElement && !ResolveUtil.processElement(processor, (PsiNamedElement) element)) {
          return false;
        }
      }

      if (lastParent == null || lastParent == list) {
        return true;
      }
      if (parent.getParent() == list) {
        if (place instanceof ClSymbol && ((ClSymbol) place).getQualifierSymbol() == null) {
          ClSymbol symbol2 = (ClSymbol) place;
          ResolveResult[] results = ClSymbolImpl.MyResolver.resolveJavaMethodReference(symbol2, list, true);
          for (ResolveResult result : results) {
            final PsiElement element = result.getElement();
            if (element instanceof PsiNamedElement && !ResolveUtil.processElement(processor, (PsiNamedElement) element)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  public static boolean processMemFnDeclaration(PsiScopeProcessor processor, ClList list, PsiElement place) {
    if (place instanceof ClSymbol && place.getParent() == list && ((ClSymbol) place).getQualifierSymbol() == null) {
      ClSymbol symbol = (ClSymbol) place;
      ResolveResult[] results = ClSymbolImpl.MyResolver.resolveJavaMethodReference(symbol, list.getParent(), true);
      for (ResolveResult result : results) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiNamedElement && !ResolveUtil.processElement(processor, (PsiNamedElement) element)) {
          return false;
        }
      }
    }

    return true;
  }
}
