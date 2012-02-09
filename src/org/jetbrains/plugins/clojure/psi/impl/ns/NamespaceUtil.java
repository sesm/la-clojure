package org.jetbrains.plugins.clojure.psi.impl.ns;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.psi.api.ClojureFile;
import org.jetbrains.plugins.clojure.psi.api.defs.ClDef;
import org.jetbrains.plugins.clojure.psi.api.ns.ClNs;
import org.jetbrains.plugins.clojure.psi.resolve.ResolveUtil;
import org.jetbrains.plugins.clojure.psi.stubs.index.ClojureNsNameIndex;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

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
          ClojureFile clf = (ClojureFile) file;
          final PsiElement[] elems = PsiTreeUtil.collectElements(clf, new PsiElementFilter() {
            public boolean isAccepted(PsiElement element) {
              return element instanceof ClDef;
            }
          });

          for (PsiElement elem : elems) {
            if (elem instanceof PsiNamedElement &&
                    ((PsiNamedElement) elem).getName() != null &&
                    ((PsiNamedElement) elem).getName().length() > 0 &&
                    suitsByPosition(((PsiNamedElement) elem), ns)) {
              result.add(((PsiNamedElement) elem));
            }
          }
        }
      }
    }
    return result.toArray(PsiNamedElement.EMPTY_ARRAY);
  }

  public static PsiNamedElement[] getDefaultDefinitions(@NotNull Project project) {
    PsiNamedElement[] ret;
    synchronized (project)
    {
      ret = project.getUserData(DEFAULT_DEFINITIONS_KEY);
      if (ret != null)
      {
        for (PsiNamedElement namedElement : ret) {
          if (!namedElement.isValid())
          {
            ret = null;
            break;
          }
        }
      }
      if (ret == null)
      {
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

  private static boolean suitsByPosition(PsiNamedElement candidate, ClNs ns) {
    final Trinity<PsiElement, PsiElement, PsiElement> tr = ClojurePsiUtil.findCommonParentAndLastChildren(ns, candidate);
    final PsiElement nsParent = tr.getSecond();
    final PsiElement candParent = tr.getThird();
    return ClojurePsiUtil.lessThan(nsParent, candParent);
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
      final String synthName = nsName.equals(fqn) ? nsName : fqn;
      final String refName = StringUtil.getShortName(synthName);

      final ClNs navigationElement = fqn.equals(ns.getName()) ? ns : null;
      return new MyClSyntheticNamespace(project, refName, synthName, navigationElement);

    }
    return null;
  }

  private static class MyClSyntheticNamespace extends ClSyntheticNamespace {

    private final Project project;
    private final ClNs navigationElement;

    public MyClSyntheticNamespace(Project project, String refName, String synthName, ClNs navigationElement) {
      super(PsiManager.getInstance(project), refName, synthName, navigationElement);
      this.project = project;
      this.navigationElement = navigationElement;
    }

    @NotNull
    @Override
    public PsiElement getNavigationElement() {
      return navigationElement != null ? navigationElement : super.getNavigationElement();
    }

    @Override
    public boolean canNavigateToSource() {
      return navigationElement != null;
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {

      // Add inner namespaces
      for (String fqn : StubIndex.getInstance().getAllKeys(ClojureNsNameIndex.KEY, project)) {
        final String outerName = getQualifiedName();
        if (fqn.startsWith(outerName) && !fqn.equals(outerName) &&
                !StringUtil.trimStart(fqn, outerName + ".").contains(".")) {
          final ClSyntheticNamespace inner = getNamespace(fqn, project);
          if (!ResolveUtil.processElement(processor, inner)) {
            return false;
          }

        }
      }

      // Add declared elements
      for (PsiNamedElement element : getDeclaredElements(getQualifiedName(), getProject())) {
        if (!ResolveUtil.processElement(processor, element)) {
          return false;
        }
      }

      return true;
    }

  }
}
