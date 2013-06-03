package org.jetbrains.plugins.clojure.psi.impl.ns;

import clojure.lang.RT;
import clojure.lang.Var;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.psi.api.ClojureFile;
import org.jetbrains.plugins.clojure.psi.api.defs.ClDef;
import org.jetbrains.plugins.clojure.psi.api.ns.ClNs;
import org.jetbrains.plugins.clojure.psi.resolve.ResolveUtil;
import org.jetbrains.plugins.clojure.psi.resolve.completion.CompleteSymbol;
import org.jetbrains.plugins.clojure.psi.stubs.index.ClojureNsNameIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import static org.jetbrains.plugins.clojure.utils.ClojureUtils.truthy;

/**
 * @author ilyas
 */
public class NamespaceUtil {

  public static PsiNamedElement[] getDeclaredElements(@NotNull String nsFqn, @NotNull Project project) {
    final Collection<ClNs> nses = StubIndex.getInstance().get(ClojureNsNameIndex.KEY, nsFqn, project, GlobalSearchScope.allScope(project));
    ArrayList<PsiNamedElement> result = new ArrayList<PsiNamedElement>();

    for (ClNs ns : nses) {
      if (nsFqn.equals(ns.getName())) {
        final PsiFile file = ns.getContainingFile();
        if (file instanceof ClojureFile) {
          for (ClDef elem : ((ClojureFile) file).getFileDefinitions()) {
            if (StringUtil.isNotEmpty(elem.getName()) && ns.getTextOffset() < elem.getTextOffset()) {
              result.add(elem);
            }
          }
        }
      }
    }
    return result.toArray(PsiNamedElement.EMPTY_ARRAY);
  }

  public static ClSyntheticNamespace[] getTopLevelNamespaces(@NotNull Project project) {
    ArrayList<ClSyntheticNamespace> result = new ArrayList<ClSyntheticNamespace>();
    for (String fqn : StubIndex.getInstance().getAllKeys(ClojureNsNameIndex.KEY, project)) {
      if (!fqn.contains(".")) {
        result.add(getNamespace(fqn, project));
      }
    }
    return result.toArray(new ClSyntheticNamespace[result.size()]);
  }

  @Nullable
  public static ClSyntheticNamespace getNamespace(@NotNull String fqn, @NotNull final Project project) {
    final Collection<ClNs> nsWithPrefix = StubIndex.getInstance().get(ClojureNsNameIndex.KEY, fqn, project, GlobalSearchScope.allScope(project));
    if (!nsWithPrefix.isEmpty()) {
      final ClNs ns = nsWithPrefix.iterator().next();
      final String nsName = ns.getName();
      assert nsName != null;
      final String refName = StringUtil.getShortName(fqn);

      ClNs navigationElement = null;
      for (ClNs clNs : nsWithPrefix) {
        if (fqn.equals(clNs.getName())) {
          navigationElement = clNs;
        }
      }
      return new MyClSyntheticNamespace(project, refName, fqn, navigationElement);
    }
    return null;
  }

  public static class MyClSyntheticNamespace extends ClSyntheticNamespace {

    public MyClSyntheticNamespace(Project project, String refName, String synthName, ClNs navigationElement) {
      super(PsiManager.getInstance(project), refName, synthName, navigationElement);
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
      return !truthy(NamespaceResolve.var.invoke(this, processor, state, lastParent, place));
    }

    private static final class NamespaceResolve
    {
      private static final Var var = RT.var("plugin.resolve.namespaces", "process-synthetic-ns-decls");
    }

    public static boolean
    processDeclarations(MyClSyntheticNamespace namespace, @NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
      final HashSet<String> innerNamespaces = new HashSet<String>();
      PsiElement separator = state.get(CompleteSymbol.SEPARATOR);

      // Add inner namespaces
      final String outerName = namespace.getQualifiedName();
      for (String fqn : StubIndex.getInstance().getAllKeys(ClojureNsNameIndex.KEY, namespace.getProject())) {
        if (fqn.startsWith(outerName) && !fqn.equals(outerName) &&
                !StringUtil.trimStart(fqn, outerName + ".").contains(".")) {
          final ClSyntheticNamespace inner = getNamespace(fqn, namespace.getProject());
          innerNamespaces.add(fqn);
          if (!ResolveUtil.processElement(processor, inner)) {
            return false;
          }
        }
      }

      if (separator == null || separator.getText().equals("/")) {
        // Add declared elements
        for (PsiNamedElement element : getDeclaredElements(namespace.getQualifiedName(), namespace.getProject())) {
          if (!ResolveUtil.processElement(processor, element)) {
            return false;
          }
        }
      }

      final String qualifiedName = namespace.getQualifiedName();
      final PsiPackage aPackage = JavaPsiFacade.getInstance(namespace.getProject()).findPackage(qualifiedName);
      if (aPackage != null) {
        for (PsiClass clazz : aPackage.getClasses(place.getResolveScope())) {
          if (!ResolveUtil.processElement(processor, clazz)) return false;
        }
        for (PsiPackage pack : aPackage.getSubPackages(place.getResolveScope())) {
          if (!innerNamespaces.contains(pack.getQualifiedName()) &&
              !ResolveUtil.processElement(processor, getNamespaceElement(pack))) {
            return false;
          }
        }
      }

      return true;
    }

  }

  public static ClSyntheticNamespace getNamespaceElement(PsiPackage pack) {
    return new MyClSyntheticNamespace(pack.getProject(), pack.getName(), pack.getQualifiedName(), null);
  }
}
