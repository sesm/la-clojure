package org.jetbrains.plugins.clojure.psi.impl.symbols;

import clojure.lang.Atom;
import clojure.lang.RT;
import clojure.lang.Var;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.ClojureIcons;
import org.jetbrains.plugins.clojure.lexer.ClojureTokenTypes;
import org.jetbrains.plugins.clojure.lexer.TokenSets;
import org.jetbrains.plugins.clojure.metrics.Metrics;
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
import org.jetbrains.plugins.clojure.psi.resolve.completion.CompletionProcessor;
import org.jetbrains.plugins.clojure.psi.resolve.processors.ResolveKind;
import org.jetbrains.plugins.clojure.psi.resolve.processors.ResolveProcessor;
import org.jetbrains.plugins.clojure.psi.resolve.processors.SymbolResolveProcessor;
import org.jetbrains.plugins.clojure.psi.stubs.index.ClojureNsNameIndex;
import org.jetbrains.plugins.clojure.psi.util.ClojureKeywords;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiFactory;
import org.jetbrains.plugins.clojure.repl.ClojureConsole;

import javax.swing.*;
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
          return resolveJavaMethodReference(symbol, null, false);
        }

        ResolveKind[] kinds = symbol.getKinds();
        if (nameString.endsWith(".")) {
          kinds = ResolveKind.javaClassesKinds();
        }
        ResolveProcessor processor = new SymbolResolveProcessor(StringUtil.trimEnd(name, "."), symbol, incompleteCode, kinds);
        resolveImpl(symbol, processor);

        if (nameString.contains(".")) {
          ResolveProcessor nsProcessor = new SymbolResolveProcessor(nameString, symbol, incompleteCode, ResolveKind.namesSpaceKinds());
          resolveNamespace(symbol, nsProcessor);
        }

        ClojureResolveResult[] candidates = processor.getCandidates();
        if (candidates.length > 0) return candidates;

        return ClojureResolveResult.EMPTY_ARRAY;
      } finally {
        timer.stop();
      }
    }

    public static ResolveResult[] resolveJavaMethodReference(final ClSymbol symbol, @Nullable PsiElement start, final boolean forCompletion) {
      final CompletionProcessor processor = new CompletionProcessor(symbol, symbol.getKinds());
      if (start == null) start = symbol;
      ResolveUtil.treeWalkUp(start, processor);
      final String name = symbol.getReferenceName();
      assert name != null;

      final String originalName = StringUtil.trimStart(name, ".");
      final PsiElement[] elements = ResolveUtil.mapToElements(processor.getCandidates());
      final HashMap<MethodSignature, HashSet<PsiMethod>> sig2Method = CompleteSymbol.collectAvailableMethods(elements);
      final List<MethodSignature> goodSignatures = ContainerUtil.findAll(sig2Method.keySet(), new Condition<MethodSignature>() {
        public boolean value(MethodSignature methodSignature) {
          return forCompletion || originalName.equals(methodSignature.getName());
        }
      });

      final HashSet<ClojureResolveResult> results = new HashSet<ClojureResolveResult>();
      for (MethodSignature signature : goodSignatures) {
        final HashSet<PsiMethod> methodSet = sig2Method.get(signature);
        for (PsiMethod method : methodSet) {
          results.add(new ClojureResolveResultImpl(method, true));
        }
      }

      return results.toArray(new ClojureResolveResult[results.size()]);
    }

    private void resolveImpl(ClSymbol symbol, ResolveProcessor processor) {
      final ClSymbol qualifier = symbol.getQualifierSymbol();

      //process other places
      if (qualifier == null) {
        ResolveUtil.treeWalkUp(symbol, processor);
      } else {
        for (ResolveResult result : qualifier.multiResolve(false)) {
          final PsiElement element = result.getElement();
          if (element != null) {
            final PsiElement sep = symbol.getSeparatorToken();
            if (sep != null && "/".equals(sep.getText())) {

              //get class elements
              if (element instanceof PsiClass) {
                element.processDeclarations(processor, ResolveState.initial(), null, symbol);
              }

              //get namespace declarations
              if (element instanceof ClSyntheticNamespace) {
                final String fqn = ((ClSyntheticNamespace) element).getQualifiedName();
                // namespace declarations
                for (PsiNamedElement named : NamespaceUtil.getDeclaredElements(fqn, element.getProject())) {
                  if (!ResolveUtil.processElement(processor, named)) return;
                }
              }

            } else if (sep == null || ".".equals(sep.getText())) {
              element.processDeclarations(processor, ResolveState.initial(), null, symbol);
            }
          }
        }
      }
    }

    private void resolveNamespace(ClSymbol symbol, ResolveProcessor processor) {
      // process namespaces
      final Project project = symbol.getProject();
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      final Collection<ClNs> nses = StubIndex.getInstance().get(ClojureNsNameIndex.KEY, symbol.getNameString(), project, scope);
      for (ClNs ns : nses) {
        ResolveUtil.processElement(processor, ns);
      }
    }
  }

  @Nullable
  public ClSymbol getRawQualifierSymbol() {
    return findChildByClass(ClSymbol.class);
  }

  /**
   * For references, which hasn't prefix
   * (import '(prefix symbol))
   * (require '(prefix symbol))
   * (require '[prefix symbol])
   * (require '(prefix [symbol :as id]))
   * @return qualifier symbol
   */
  @Nullable
  public ClSymbol getQualifierSymbol() {
    final ClSymbol rawQualifierSymbol = getRawQualifierSymbol();
    if (rawQualifierSymbol != null) return rawQualifierSymbol;
    final PsiElement parent = getParent();
    return getQualifiedNameInner(parent, false);
  }

  private ClSymbol getQualifiedNameInner(PsiElement parent, boolean onlyRequireOrUse) {
    if (parent instanceof ClList) {
      return getListQualifier(onlyRequireOrUse, (ClList) parent, parent.getParent(), false);
    } else if (parent instanceof ClVector && ImportOwner.isSpecialVector((ClVector) parent)) {
      final ClSymbol[] symbols = ((ClVector) parent).getAllSymbols();
      return symbols[0] == this ? getQualifiedNameInner(parent.getParent(), true) : null;
    } else if (parent instanceof ClVector) {
      return getVectorQualifier(onlyRequireOrUse, (ClVector) parent, parent.getParent(), false);
    }
    return null;
  }

  private ClSymbol getListQualifier(boolean onlyRequireOrUse, ClList list, PsiElement listParent, boolean isQuoted) {
    if (listParent instanceof ClQuotedForm) {
      return getListQualifier(onlyRequireOrUse, list, listParent.getParent(), true);
    } else if (listParent instanceof ClList) {
      final PsiElement listParentFirstSymbol = ((ClList) listParent).getFirstNonLeafElement();
      if (listParentFirstSymbol instanceof ClSymbol || listParentFirstSymbol instanceof ClKeyword) {
        final String name;
        if (listParentFirstSymbol instanceof ClSymbol) {
          name = ((ClSymbol) listParentFirstSymbol).getNameString();
        } else {
          name = ((ClKeyword) listParentFirstSymbol).getName();
        }
        boolean isOk = false;
        if ((name.equals(ClojureKeywords.IMPORT) || name.equals(ImportOwner.IMPORT)) && !onlyRequireOrUse) isOk = true;
        else if ((name.equals(ClojureKeywords.REQUIRE) || name.equals(ClojureKeywords.USE)) && !isQuoted) isOk = true;
        else if ((name.equals(ImportOwner.REQUIRE) || name.equals(ImportOwner.USE)) && isQuoted) isOk = true;
        final ClSymbol firstSymbol = list.getFirstSymbol();
        if (isOk && firstSymbol != this && firstSymbol instanceof ClSymbol) {
          return firstSymbol;
        }
      }
    }
    return null;
  }

  private ClSymbol getVectorQualifier(boolean onlyRequireOrUse, ClVector vector, PsiElement list, boolean isQuoted) {
    if (list instanceof ClList) {
      final PsiElement firstSymbol = ((ClList) list).getFirstNonLeafElement();
      if (firstSymbol instanceof ClSymbol || firstSymbol instanceof ClKeyword) {
        final String name;
        if (firstSymbol instanceof ClSymbol) {
          name = ((ClSymbol) firstSymbol).getNameString();
        } else {
          name = ((ClKeyword) firstSymbol).getName();
        }
        boolean isOk = false;
        if ((name.equals(ClojureKeywords.IMPORT) || name.equals(ImportOwner.IMPORT)) && !onlyRequireOrUse) isOk = true;
        else if ((name.equals(ClojureKeywords.REQUIRE) || name.equals(ClojureKeywords.USE)) && !isQuoted) isOk = true;
        else if ((name.equals(ImportOwner.REQUIRE) || name.equals(ImportOwner.USE)) && isQuoted) isOk = true;
        if (isOk) {
          final PsiElement firstNonLeafElement = vector.getFirstNonLeafElement();
          if (firstNonLeafElement != null && firstNonLeafElement != this && firstNonLeafElement instanceof ClSymbol) {
            return (ClSymbol) firstNonLeafElement;
          }
        }
      }
    } else if (list instanceof ClQuotedForm) {
      return getVectorQualifier(onlyRequireOrUse, vector, list.getParent(), true);
    }
    return null;
  }

  public boolean isQualified() {
    return getQualifierSymbol() != null;
  }

  @Override
  public String getName() {
    return getNameString();
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
