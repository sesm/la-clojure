package org.jetbrains.plugins.clojure.psi.impl.javaView;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.compiler.ClojureCompilerSettings;
import org.jetbrains.plugins.clojure.psi.api.ClojureFile;
import org.jetbrains.plugins.clojure.psi.api.ns.ClNs;
import org.jetbrains.plugins.clojure.psi.stubs.index.ClojureFullScriptNameIndex;
import org.jetbrains.plugins.clojure.psi.stubs.index.ClojureNsNameIndex;
import org.jetbrains.plugins.clojure.psi.util.ClojureTextUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 */
public class ClojureClassFinder extends PsiElementFinder {
  private final Project myProject;

  public ClojureClassFinder(Project project) {
    myProject = project;
  }

  @Nullable
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return getClassByFQName(qualifiedName, scope);
  }

  @NotNull
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return getClassesByFQName(qualifiedName, scope);
  }

  @Nullable
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    return null;
  }

  @NotNull
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return new PsiPackage[0];
  }

  @NotNull
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    if (!ClojureCompilerSettings.getInstance(myProject).getState().COMPILE_CLOJURE) return PsiClass.EMPTY_ARRAY;

    List<PsiClass> result = new ArrayList<PsiClass>();
    StubIndex stubIndex = StubIndex.getInstance();
    String packageName = psiPackage.getQualifiedName();
    for (String fqn : stubIndex.getAllKeys(ClojureNsNameIndex.KEY, myProject)) {
      if (ClojureTextUtil.getSymbolPrefix(fqn).equals(packageName)) {
        for (ClNs ns : stubIndex.get(ClojureNsNameIndex.KEY, fqn, myProject, scope)) {
          if (ns.isClassDefinition()) {
            ClojureFile file = (ClojureFile) ns.getContainingFile();
            PsiClass psiClass = file.getDefinedClass();
            if (psiClass != null) {
              result.add(psiClass);
            }
          }
        }
      }
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  private Collection<PsiClass> getScriptClassesByFQName(final String name, final GlobalSearchScope scope) {
    Collection<ClojureFile> scripts = StubIndex.getInstance().get(ClojureFullScriptNameIndex.KEY, name.hashCode(), myProject, scope);

    scripts = ContainerUtil.findAll(scripts, new Condition<ClojureFile>() {
      public boolean value(final ClojureFile clojureFile) {
        final PsiClass clazz = clojureFile.getDefinedClass();
        return clojureFile.isClassDefiningFile() && clazz != null && name.equals(clazz.getQualifiedName());
      }
    });
    return ContainerUtil.map(scripts, new Function<ClojureFile, PsiClass>() {
      public PsiClass fun(final ClojureFile clojureFile) {
        return clojureFile.getDefinedClass();
      }
    });
  }

  @Nullable
  private PsiClass getClassByFQName(@NotNull @NonNls String name, @NotNull GlobalSearchScope scope) {
    if (!areClassesCompiled()) return null;

    final Collection<PsiClass> scriptClasses = getScriptClassesByFQName(name, scope);
    for (PsiClass clazz : scriptClasses) {
      if (name.equals(clazz.getQualifiedName())) return clazz;
    }
    return null;
  }

  @NotNull
  private PsiClass[] getClassesByFQName(@NotNull @NonNls String fqn, @NotNull GlobalSearchScope scope) {
    if (!areClassesCompiled()) return PsiClass.EMPTY_ARRAY;

    final Collection<PsiClass> result = getScriptClassesByFQName(fqn, scope);
    ArrayList<PsiClass> filtered = new ArrayList<PsiClass>();
    for (PsiClass clazz : result) {
      if (fqn.equals(clazz.getQualifiedName())) {
        filtered.add(clazz);
      }
    }
    return filtered.isEmpty() ? PsiClass.EMPTY_ARRAY : filtered.toArray(new PsiClass[filtered.size()]);
  }

  private boolean areClassesCompiled() {
    ClojureCompilerSettings settings = ClojureCompilerSettings.getInstance(myProject);
    return settings.getState().COMPILE_CLOJURE;
  }


}