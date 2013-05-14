package org.jetbrains.plugins.clojure.psi.impl.ns;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
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
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author ilyas
 */
public class NamespaceUtil {

  public static final Key<PsiNamedElement[]> DEFAULT_DEFINITIONS_KEY = new Key<PsiNamedElement[]>("DEFAULT_DEFINITIONS_KEY");

  public static final String[] DEFAULT_NSES = new String[]{ClojureUtils.CORE_NAMESPACE,
//          "clojure.inspector",
//          "clojure.main",
//          "clojure.parallel",
//          "clojure.set",
//          "clojure.zip",
//          "clojure.xml"
  };

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

  public static PsiNamedElement[] getDefaultDefinitions(@NotNull Project project) {
    PsiNamedElement[] ret;
    synchronized (project) {
      ret = project.getUserData(DEFAULT_DEFINITIONS_KEY);
      if (ret != null) {
        for (PsiNamedElement namedElement : ret) {
          if (!namedElement.isValid()) {
            ret = null;
            break;
          }
        }
      }
      if (ret == null) {
        final ArrayList<PsiNamedElement> res = new ArrayList<PsiNamedElement>();
        for (String ns : DEFAULT_NSES) {
          res.addAll(Arrays.asList(getDeclaredElements(ns, project)));
        }
        ret = res.toArray(PsiNamedElement.EMPTY_ARRAY);
        project.putUserData(DEFAULT_DEFINITIONS_KEY, ret);
      }
    }
    return ret;
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
    private final Project project;

    public MyClSyntheticNamespace(Project project, String refName, String synthName, ClNs navigationElement) {
      super(PsiManager.getInstance(project), refName, synthName, navigationElement);
      this.project = project;
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
      return ResolveUtil.processDeclarations(this, processor, state, lastParent, place);
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
