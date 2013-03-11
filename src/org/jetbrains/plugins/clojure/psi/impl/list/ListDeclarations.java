package org.jetbrains.plugins.clojure.psi.impl.list;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.psi.api.ClList;
import org.jetbrains.plugins.clojure.psi.api.ClVector;
import org.jetbrains.plugins.clojure.psi.api.defs.ClDef;
import org.jetbrains.plugins.clojure.psi.api.symbols.ClSymbol;
import org.jetbrains.plugins.clojure.psi.impl.ImportOwner;
import org.jetbrains.plugins.clojure.psi.impl.ns.ClSyntheticNamespace;
import org.jetbrains.plugins.clojure.psi.impl.ns.NamespaceUtil;
import org.jetbrains.plugins.clojure.psi.impl.symbols.ClSymbolImpl;
import org.jetbrains.plugins.clojure.psi.resolve.ResolveUtil;
import org.jetbrains.plugins.clojure.psi.util.ClojureKeywords;

import java.util.Arrays;
import java.util.Set;

/**
 * @author ilyas
 */
public class ListDeclarations {

  public static final String LET = "let";
  public static final String WITH_OPEN = "with-open";
  public static final String WITH_LOCAL_VARS = "with-local-vars";
  public static final String WHEN_LET = "when-let";
  public static final String WHEN_FIRST = "when-let";
  public static final String FOR = "for";
  public static final String IF_LET = "if-let";
  public static final String LOOP = "loop";
  public static final String DECLARE = "declare";
  public static final String FN = "fn";
  public static final String DEFN = "defn";
  public static final String DEFN_ = "defn-";

  public static final String NS = "ns";

  public static final String IMPORT = "import";
  private static final String MEMFN = "memfn";
  public static final String USE = "use";
  public static final String REFER = "refer";
  public static final String REQUIRE = "require";

  private static final String DOT = ".";

  private static final Set<String> LOCAL_BINDINGS = new HashSet<String>(Arrays.asList(
      LET, WITH_OPEN, WITH_LOCAL_VARS, WHEN_LET, WHEN_FIRST, FOR, IF_LET, LOOP
  ));


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


  public static boolean isLocal(PsiElement element) {
    if (element instanceof ClSymbol) {
      ClSymbol symbol = (ClSymbol) element;
      final PsiElement parent = symbol.getParent();

      if (parent instanceof ClList) {
        ClList list = (ClList) parent;
        if (FN.equals(list.getHeadText())) return true;
      } else if (parent instanceof ClVector) {
        final PsiElement par = parent.getParent();
        if (par instanceof ClDef && ((ClDef) par).getSecondNonLeafElement() == element) return true;
        if (par instanceof ClList) {
          final String ht = ((ClList) par).getHeadText();
          if (LOCAL_BINDINGS.contains(ht)) return true;
        }
      }
    }

    return false;
  }
}
