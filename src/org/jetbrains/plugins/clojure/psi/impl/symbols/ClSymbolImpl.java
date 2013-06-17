package org.jetbrains.plugins.clojure.psi.impl.symbols;

import clojure.lang.Atom;
import clojure.lang.RT;
import clojure.lang.Var;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.ClojureIcons;
import org.jetbrains.plugins.clojure.lexer.ClojureTokenTypes;
import org.jetbrains.plugins.clojure.lexer.TokenSets;
import org.jetbrains.plugins.clojure.metrics.Metrics;
import org.jetbrains.plugins.clojure.psi.ClojurePsiElement;
import org.jetbrains.plugins.clojure.psi.ClojurePsiElementImpl;
import org.jetbrains.plugins.clojure.psi.api.*;
import org.jetbrains.plugins.clojure.psi.api.ns.ClNs;
import org.jetbrains.plugins.clojure.psi.api.symbols.ClSymbol;
import org.jetbrains.plugins.clojure.psi.impl.ImportOwner;
import org.jetbrains.plugins.clojure.psi.impl.ns.ClSyntheticNamespace;
import org.jetbrains.plugins.clojure.psi.impl.ns.NamespaceUtil;
import org.jetbrains.plugins.clojure.psi.resolve.ClojureResolveResult;
import org.jetbrains.plugins.clojure.psi.resolve.ClojureResolveResultImpl;
import org.jetbrains.plugins.clojure.psi.resolve.ResolveUtil;
import org.jetbrains.plugins.clojure.psi.resolve.completion.CompleteSymbol;
import org.jetbrains.plugins.clojure.psi.resolve.processors.ResolveKind;
import org.jetbrains.plugins.clojure.psi.resolve.processors.ResolveProcessor;
import org.jetbrains.plugins.clojure.psi.resolve.processors.SymbolResolveProcessor;
import org.jetbrains.plugins.clojure.psi.util.ClojureKeywords;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiFactory;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.repl.ClojureConsole;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ilyas
 */
public class ClSymbolImpl extends ClojurePsiElementImpl implements ClSymbol {
  public ClSymbolImpl(ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public PsiReference[] getReferences() {
    return new PsiReference[] {this};
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public String toString() {
    return "ClSymbol";
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    final PsiElement refNameElement = getReferenceNameElement();
    if (refNameElement != null) {
      final int offsetInParent = refNameElement.getStartOffsetInParent();
      return new TextRange(offsetInParent, offsetInParent + refNameElement.getTextLength());
    }
    return new TextRange(0, getTextLength());
  }

  @Nullable
  public PsiElement getReferenceNameElement() {
    final ASTNode lastChild = getNode().getLastChildNode();
    if (lastChild == null) return null;
    for (IElementType elementType : TokenSets.REFERENCE_NAMES.getTypes()) {
      if (lastChild.getElementType() == elementType) return lastChild.getPsi();
    }

    return null;
  }

  @Nullable
  public String getReferenceName() {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      if (nameElement.getNode().getElementType() == ClojureTokenTypes.symATOM)
        return nameElement.getText();
    }
    return null;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean incomplete) {
    Metrics.Timer.Instance timer = Metrics.getInstance(getProject()).start("symbol.multiResolve");
    try {
      final ResolveCache resolveCache = ResolveCache.getInstance(getProject());
      return resolveCache.resolveWithCaching(this, RESOLVER, true, incomplete);
    } finally {
      timer.stop();
    }
  }

  public PsiElement setName(@NotNull @NonNls String newName) throws IncorrectOperationException {
    final ASTNode newNode = ClojurePsiFactory.getInstance(getProject()).createSymbolNodeFromText(newName);
    getParent().getNode().replaceChild(getNode(), newNode);
    return newNode.getPsi();
  }

  @Override
  public Icon getIcon(int flags) {
    return ClojureIcons.SYMBOL;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String name = getName();
        return name == null ? "<undefined>" : name;
      }

      @Nullable
      public String getLocationString() {
        String name = getContainingFile().getName();
        //todo show namespace
        return "(in " + name + ")";
      }

      @Nullable
      public Icon getIcon(boolean open) {
        return ClSymbolImpl.this.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    };
  }


  public ResolveKind[] getKinds() {
    return ResolveKind.allKinds();
  }
  public static class MyResolver implements ResolveCache.PolyVariantResolver<ClSymbol> {

    public ResolveResult[] resolve(ClSymbol symbol, boolean incompleteCode) {
      Metrics.Timer.Instance timer = Metrics.getInstance(symbol.getProject()).start("symbol.resolver.resolve");
      try {
        final String name = symbol.getReferenceName();
        if (name == null) return null;

        // Resolve Java methods invocations
        ClSymbol qualifier = symbol.getQualifierSymbol();
        final String nameString = symbol.getNameString();
        if (qualifier == null && nameString.startsWith(".")) {
          return resolveJavaMethodReference(symbol);
        }

        ResolveKind[] kinds = symbol.getKinds();
        if (nameString.endsWith(".")) {
          kinds = ResolveKind.javaClassesKinds();
        }
        ResolveProcessor processor = new SymbolResolveProcessor(StringUtil.trimEnd(name, "."), symbol, incompleteCode, kinds);
        resolveImpl(symbol, processor);

        ClojureResolveResult[] candidates = processor.getCandidates();
        if (candidates.length > 0) return candidates;

        return ClojureResolveResult.EMPTY_ARRAY;
      } finally {
        timer.stop();
      }
    }

    private static void addClass(String qualifiedName, Collection<PsiClass> classes, Project project) {
      final PsiClass clazz = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
      if (clazz != null) {
        classes.add(clazz);
      }
    }

    private static void checkImportStatement(PsiElement stmt, Collection<PsiClass> classes, Project project) {
      if (stmt instanceof ClSymbol) {
        addClass(((ClSymbol) stmt).getNameString(), classes, project);
      } else if (stmt instanceof ClVector || stmt instanceof ClList) {
        final ClListLike listLike = (ClListLike) stmt;
        final PsiElement fst = listLike.getFirstNonLeafElement();
        if (fst instanceof ClSymbol) {
          PsiElement next = fst.getNextSibling();
          while (next != null) {
            if (next instanceof ClSymbol) {
              ClSymbol clazzSym = (ClSymbol) next;
              addClass(((ClSymbol) fst).getNameString() + "." + clazzSym.getNameString(), classes, project);
            }
            next = next.getNextSibling();
          }
        }
      } else if (stmt instanceof ClQuotedForm) {
        final ClojurePsiElement quotedElement = ((ClQuotedForm) stmt).getQuotedElement();
        checkImportStatement(quotedElement, classes, project);
      }
    }

    public static Collection<PsiClass>  importedClasses(ClNs ns, Project project) {
      List<PsiClass> classes = new ArrayList<PsiClass>();
      for (PsiElement element : ns.getChildren()) {
        if (element instanceof ClList || element instanceof ClVector) {
          ClListLike directive = (ClListLike) element;
          final PsiElement first = directive.getFirstNonLeafElement();
          if (first != null) {
            final String headText = first.getText();
            if (ClojureKeywords.IMPORT.equals(headText) || ImportOwner.IMPORT.equals(headText)) {
              for (PsiElement stmt : directive.getChildren()) {
                checkImportStatement(stmt, classes, project);
              }
            }
          }
        }
      }
      return classes;
    }

    public static PsiClass[] allImportedClasses(final ClSymbol symbol) {
      // TODO: handle import forms
      ClNs symbolNs = symbol.getNs();
      if (symbolNs != null) {
        Collection<PsiClass> classes = importedClasses(symbolNs, symbol.getProject());
        return classes.toArray(new PsiClass[classes.size()]);
      }

      return PsiClass.EMPTY_ARRAY;
    }

    public static ResolveResult[] resolveJavaMethodReference(final ClSymbol symbol) {
      final String name = symbol.getReferenceName();
      assert name != null;
      final String originalName = StringUtil.trimStart(name, ".");

      final HashSet<ClojureResolveResult> results = new HashSet<ClojureResolveResult>();
      for (PsiClass clazz : allImportedClasses(symbol)) {
        addMethodsByName(clazz, originalName, results);
      }
      JavaPsiFacade facade = JavaPsiFacade.getInstance(symbol.getProject());
      PsiPackage javaLang = facade.findPackage(ClojurePsiUtil.JAVA_LANG);
      if (javaLang != null) {
        for (PsiClass clazz : javaLang.getClasses()) {
          addMethodsByName(clazz, originalName, results);
        }
      }

      return results.toArray(new ClojureResolveResult[results.size()]);
    }

    private static void addMethodsByName(PsiClass clazz, String methodName, HashSet<ClojureResolveResult> results) {
      for (PsiMethod method : clazz.findMethodsByName(methodName, true)) {
        if (!method.isConstructor() && method.hasModifierProperty(PsiModifier.PUBLIC)) {
          results.add(new ClojureResolveResultImpl(method, true));
        }
      }
    }

    private static ClSyntheticNamespace checkRequireStatementElement(PsiElement stmt, String headText, ClSymbol place, String prefix) {
      if (stmt instanceof ClVector || stmt instanceof ClList) {
        String namespace = null;
        for (PsiElement child : stmt.getChildren()) {
          if ((child instanceof ClSymbol) && (namespace == null)) {
            String name = ((ClSymbol) child).getName();
            namespace = prefix == null ? name : prefix + "." + name;
          } else if ((child instanceof ClKeyword) && child.getText().equals(ClojureKeywords.AS)) {
            PsiElement next = ClojurePsiUtil.getNextNonWhiteSpace(child);
            if (next instanceof ClSymbol) {
              if (((ClSymbol) next).getName().equals(place.getName())) {
                ClSyntheticNamespace ns = NamespaceUtil.getNamespace(namespace, place.getProject());
                if (ns != null) {
                  return ns;
                }
              }
            }
          } else if (child instanceof ClVector || child instanceof ClList) {
            ClSyntheticNamespace ns = checkRequireStatementElement(child, headText, place, namespace);
            if (ns != null) {
              return ns;
            }
          }
        }
      } else if (stmt instanceof ClQuotedForm && ImportOwner.REQUIRE.equals(headText)) {
        return checkRequireStatementElement(((ClQuotedForm) stmt).getQuotedElement(), headText, place, null);
      }
      return null;
    }

    public static ClSyntheticNamespace getNsAliasResolve(final ClSymbol symbol) {
      // TODO: handle require forms
      ClNs symbolNs = symbol.getNs();
      if (symbolNs == null) {
        return null;
      }
      for (PsiElement element : symbolNs.getChildren()) {
        if (element instanceof ClList || element instanceof ClVector) {
          ClListLike directive = (ClListLike) element;
          final PsiElement first = directive.getFirstNonLeafElement();
          if (first != null) {
            final String headText = first.getText();
            if (ClojureKeywords.REQUIRE.equals(headText) || ImportOwner.REQUIRE.equals(headText)) {
              for (PsiElement stmt : directive.getChildren()) {
                ClSyntheticNamespace resolves = checkRequireStatementElement(stmt, headText, symbol, null);
                if (resolves != null) {
                  return resolves;
                }
              }
            }
          }
        }
      }
      return null;
    }

    private Collection<PsiNamedElement> resolveSingleSegmentQualifier(ClSymbol qualifier) {
      Collection<PsiNamedElement> resolves = new ArrayList<PsiNamedElement>();

      // Try alias first
      ClSyntheticNamespace ns = getNsAliasResolve(qualifier);
      if (ns != null) {
        resolves.add(ns);
      }

      // Check for NS
      Project project = qualifier.getProject();
      String qual = qualifier.getName();
      ns = NamespaceUtil.getNamespace(qual, project);
      if (ns != null) {
        resolves.add(ns);
      }

      // Check for Class
      PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(project);
      PsiClass[] classes = namesCache.getClassesByName(qual, GlobalSearchScope.allScope(project));
      if (classes.length > 0) {
        PsiClass[] imported = allImportedClasses(qualifier);
        for (PsiClass psiClass : classes) {
          String qualifiedName = psiClass.getQualifiedName();
          if (qualifiedName.equals(ClojurePsiUtil.JAVA_LANG + "." + qual) || qualifiedName.equals(qual)) {
            resolves.add(psiClass);
          } else if (ArrayUtil.indexOf(imported, psiClass) >= 0) {
            resolves.add(psiClass);
          }
        }
      }

      return resolves;
    }

    private Collection<PsiNamedElement> resolveMultiSegmentQualifier(ClSymbol qualifier, boolean processPackages) {
      Collection<PsiNamedElement> resolves = new ArrayList<PsiNamedElement>();

      // Check for NS
      Project project = qualifier.getProject();
      String qual = qualifier.getName();
      ClSyntheticNamespace ns = NamespaceUtil.getNamespace(qual, project);
      if (ns != null) {
        resolves.add(ns);
      }

      // Check for Class
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiClass psiClass = facade.findClass(qual, GlobalSearchScope.allScope(project));
      if (psiClass != null) {
        resolves.add(psiClass);
      }

      // Check for packages
      if (processPackages) {
        PsiPackage psiPackage = facade.findPackage(qual);
        if (psiPackage != null) {
          resolves.add(psiPackage);
        }
      }

      return resolves;
    }

    private boolean isSlashSeparated(ClSymbol symbol) {
      return (symbol.getSeparatorToken() != null) && "/".equals(symbol.getSeparatorToken().getText());
    }

    private void resolveImpl(ClSymbol place, ResolveProcessor processor) {
      boolean isQualified = place.getQualifierSymbol() != null;
      boolean isQualifier = place.getContext() instanceof ClSymbol;

      if (!isQualified && !isQualifier) {
        // Bare symbol cases
        // symbol
        // Class
        ResolveUtil.treeWalkUp(place, processor, ResolveState.initial());
      } else if (isQualifier) {
        // Find fully-qualified qualifier symbol
        ClSymbol qualifier = place;
        PsiElement next = qualifier.getContext();
        while ((next instanceof ClSymbol) && !isSlashSeparated((ClSymbol) next)) {
          qualifier = (ClSymbol) next;
          next = qualifier.getContext();
        }

        if (next instanceof ClSymbol && isSlashSeparated((ClSymbol) next)) {
          if (qualifier.isQualified()) {
            // Potential cases - qualifiers of:
            // ns.ns/symbol
            // pack.Class/method
            if (place == qualifier) {
              if (!resolveFullyQualified(processor, qualifier, false)) {
                return;
              }
            } else {
              if (!resolveSubSegment(processor, place, qualifier)) {
                return;
              }
            }
          } else {
            // Potential cases:
            // alias/symbol
            // ns/symbol
            // Class/method
            for (PsiNamedElement element : resolveSingleSegmentQualifier(qualifier)) {
              if (!processor.execute(element, ResolveState.initial())) {
                return;
              }
            }
          }
        } else {
          // Inner segment of qualified symbol cases:
          // ns.ns
          // pack.pack
          // pack.Class
          if (!resolveSubSegment(processor, place, qualifier)) {
            return;
          }
        }
      } else {
        ClSymbol qualifier = place.getQualifierSymbol();
        if (isSlashSeparated(place)) {
          for (ResolveResult result : qualifier.multiResolve(false)) {
            final PsiElement element = result.getElement();
            if (element != null) {
              //get class elements
              if ((element instanceof PsiClass) &&
                  !element.processDeclarations(processor, ResolveState.initial(), null, place)) {
                return;
              }

              //get namespace declarations
              if ((element instanceof ClSyntheticNamespace) &&
                  !element.processDeclarations(processor, ResolveState.initial(), null, place)) {
                return;
              }
            }
          }
        } else {
          // Qualified symbol cases:
          // ns.ns
          // pack.pack
          // pack.Class
          if (!resolveFullyQualified(processor, place, true)) {
            return;
          }
        }
      }
    }

    private boolean resolveFullyQualified(ResolveProcessor processor, ClSymbol symbol, boolean processPackages) {
      for (PsiNamedElement element : resolveMultiSegmentQualifier(symbol, processPackages)) {
        if (!processor.execute(element, ResolveState.initial())) {
          return false;
        }
      }
      return true;
    }

    private boolean resolveSubSegment(ResolveProcessor processor, ClSymbol place, ClSymbol qualifier) {
      for (PsiNamedElement element : resolveMultiSegmentQualifier(qualifier, true)) {
        String name = place.getName();
        if (element instanceof ClSyntheticNamespace) {
          ClSyntheticNamespace ns = NamespaceUtil.getNamespace(name, qualifier.getProject());
          if (ns != null && !processor.execute(ns, ResolveState.initial())) {
            return false;
          }
        } else if (element instanceof PsiClass || element instanceof PsiPackage) {
          JavaPsiFacade facade = JavaPsiFacade.getInstance(qualifier.getProject());
          PsiPackage psiPackage = facade.findPackage(name);
          if (psiPackage != null && !processor.execute(psiPackage, ResolveState.initial())) {
            return false;
          }
        }
      }
      return true;
    }
  }

  @Nullable
  public ClSymbol getQualifierSymbol() {
    return findChildByClass(ClSymbol.class);
  }

  public boolean isQualified() {
    return getQualifierSymbol() != null;
  }

  @Override
  public String getNamespace() {
    if (isQualified()) {
      PsiElement separator = getSeparatorToken();
      if (separator != null && "/".equals(separator.getText())) {
        return getQualifierSymbol().getNameString();
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public String getName() {
    if (isQualified()) {
      PsiElement separator = getSeparatorToken();
      if (separator != null && "/".equals(separator.getText())) {
        PsiElement atom = separator.getNextSibling();
        return atom == null ? null : atom.getText();
      } else {
        return getNameString();
      }
    } else {
      return getNameString();
    }
  }

  @Nullable
  public PsiElement getSeparatorToken() {
    return findChildByType(TokenSets.DOTS);
  }

  private static final MyResolver RESOLVER = new MyResolver();

  public PsiElement resolve() {
    final ResolveCache resolveCache = ResolveCache.getInstance(getProject());
    ResolveResult[] results = resolveCache.resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @NotNull
  public String getCanonicalText() {
    return getText();
  }

  private List<PsiElement> multipleResolveResults() {
    final ResolveCache resolveCache = ResolveCache.getInstance(getProject());
    final ResolveResult[] results = resolveCache.resolveWithCaching(this, RESOLVER, false, false);
    return ContainerUtil.map(results, new Function<ResolveResult, PsiElement>() {
      public PsiElement fun(ResolveResult resolveResult) {
        return resolveResult.getElement();
      }
    });
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      ASTNode newNameNode = ClojurePsiFactory.getInstance(getProject()).createSymbolNodeFromText(newElementName);
      assert newNameNode != null && node != null;
      node.getTreeParent().replaceChild(node, newNameNode);
    }
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return this;
    final PsiFile file = getContainingFile();
    if (element instanceof PsiClass && (file instanceof ClojureFile)) {
      final PsiClass clazz = (PsiClass) element;
      Atom state = file.getCopyableUserData(ClojureConsole.STATE_KEY);
      if (state == null) {
        // Import into current file
        final Application application = ApplicationManager.getApplication();
        application.runWriteAction(new Runnable() {
          public void run() {
            final ClNs ns = ((ClojureFile) file).findOrCreateNamespaceElement();
            ns.addImportForClass(ClSymbolImpl.this, clazz);
          }
        });
      } else {
        // Import into REPL session
        String command = "(import " + clazz.getQualifiedName() + ')';
        Var executeCommand = RT.var("plugin.repl.actions", "execute-command");
        executeCommand.invoke(state, command);
      }
      return this;
    }
    return this;
  }

  public boolean isReferenceTo(PsiElement element) {
    return multipleResolveResults().contains(element);
  }

  @NotNull
  public Object[] getVariants() {
    Metrics.Timer.Instance timer = Metrics.getInstance(getProject()).start("symbol.getVariants");
    try {
      return CompleteSymbol.getVariants(this);
    } finally {
      timer.stop();
    }
  }

  public boolean isSoft() {
    return false;
  }

  @NotNull
  public String getNameString() {
    return getText();
  }
}
