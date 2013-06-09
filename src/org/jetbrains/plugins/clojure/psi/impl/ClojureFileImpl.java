package org.jetbrains.plugins.clojure.psi.impl;

import clojure.lang.*;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.file.ClojureFileType;
import org.jetbrains.plugins.clojure.metrics.Metrics;
import org.jetbrains.plugins.clojure.parser.ClojureElementTypes;
import org.jetbrains.plugins.clojure.parser.ClojureParser;
import org.jetbrains.plugins.clojure.psi.ClojureConsoleElement;
import org.jetbrains.plugins.clojure.psi.api.ClList;
import org.jetbrains.plugins.clojure.psi.api.ClojureFile;
import org.jetbrains.plugins.clojure.psi.api.defs.ClDef;
import org.jetbrains.plugins.clojure.psi.api.ns.ClNs;
import org.jetbrains.plugins.clojure.psi.api.symbols.ClSymbol;
import org.jetbrains.plugins.clojure.psi.impl.ns.ClSyntheticNamespace;
import org.jetbrains.plugins.clojure.psi.impl.ns.NamespaceUtil;
import org.jetbrains.plugins.clojure.psi.impl.synthetic.ClSyntheticClassImpl;
import org.jetbrains.plugins.clojure.psi.resolve.ResolveUtil;
import org.jetbrains.plugins.clojure.psi.resolve.completion.CompleteSymbol;
import org.jetbrains.plugins.clojure.psi.stubs.api.ClFileStub;
import org.jetbrains.plugins.clojure.psi.stubs.index.ClDefNameIndex;
import org.jetbrains.plugins.clojure.psi.stubs.index.ClojureNsNameIndex;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiFactory;
import org.jetbrains.plugins.clojure.psi.util.ClojurePsiUtil;
import org.jetbrains.plugins.clojure.psi.util.ClojureTextUtil;
import org.jetbrains.plugins.clojure.repl.ClojureConsole;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.util.*;

import static org.jetbrains.plugins.clojure.utils.ClojureUtils.truthy;

/**
 * User: peter
 * Date: Nov 21, 2008
 * Time: 9:50:00 AM
 * Copyright 2007, 2008, 2009 Red Shark Technology
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class ClojureFileImpl extends PsiFileBase implements ClojureFile {
  public static final Keyword REPL_KEYWORD = Keyword.intern("repl");
  private PsiElement myContext = null;
  private PsiClass myClass;
  private boolean myScriptClassInitialized = false;

  @NonNls
  public static final Keyword NAMESPACES_KEYWORD = Keyword.intern("namespaces");
  @NonNls
  public static final Keyword IMPORTS_KEYWORD = Keyword.intern("imports");
  @NonNls
  public static final Keyword SYMBOLS_KEYWORD = Keyword.intern("symbols");

  @Override
  public String toString() {
    return "ClojureFile";
  }

  public ClojureFileImpl(FileViewProvider viewProvider) {
    super(viewProvider, ClojureFileType.CLOJURE_LANGUAGE);
  }

  @Override
  public PsiElement getContext() {
    if (myContext != null) {
      return myContext;
    }
    return super.getContext();
  }

  public PsiClass getDefinedClass() {
    if (!myScriptClassInitialized) {
      if (isScript()) {
        myClass = new ClSyntheticClassImpl(this);
      }

      myScriptClassInitialized = true;
    }
    return myClass;
  }

  public void setNamespace(String newNs) {
    final ClList nsElem = getNamespaceElement();
    if (nsElem != null) {
      final ClSymbol first = nsElem.getFirstSymbol();
      final PsiElement second = nsElem.getSecondNonLeafElement();
      if (first != null && second != null) {
        final ClojurePsiFactory factory = ClojurePsiFactory.getInstance(getProject());
        final ASTNode newNode = factory.createSymbolNodeFromText(newNs);
        final ASTNode parentNode = nsElem.getNode();
        if (parentNode != null) {
          parentNode.replaceChild(second.getNode(), newNode);
        }
      }
    }
  }

  public String getNamespacePrefix() {
    final String ns = getNamespace();
    if (ns != null) {
      return ns.substring(0, ns.lastIndexOf("."));
    }
    return null;

  }

  protected PsiFileImpl clone() {
    final ClojureFileImpl clone = (ClojureFileImpl) super.clone();
    clone.myContext = myContext;
    return clone;
  }

  @NotNull
  public FileType getFileType() {
    return ClojureFileType.CLOJURE_FILE_TYPE;
  }

  @NotNull
  public String getPackageName() {
    StubElement stub = getStub();
    if (stub instanceof ClFileStub) {
      return ((ClFileStub) stub).getPackageName().getString();
    }

    String ns = getNamespace();
    if (ns == null) {
      return "";
    } else {
      return ClojureTextUtil.getSymbolPrefix(ns);
    }
  }

  public boolean isScript() {
    return true;
  }

  private boolean isWrongElement(PsiElement element) {
    return element == null ||
        (element instanceof LeafPsiElement || element instanceof PsiWhiteSpace || element instanceof PsiComment);
  }

  public PsiElement getFirstNonLeafElement() {
    PsiElement first = getFirstChild();
    while (first != null && isWrongElement(first)) {
      first = first.getNextSibling();
    }
    return first;
  }

  public PsiElement getNonLeafElement(int k) {
    final List<PsiElement> elements = ContainerUtil.filter(getChildren(), new Condition<PsiElement>() {
      public boolean value(PsiElement psiElement) {
        return !isWrongElement(psiElement);
      }
    });
    if (k - 1 >= elements.size()) return null;
    return elements.get(k - 1);
  }

  public PsiElement getLastNonLeafElement() {
    PsiElement lastChild = getLastChild();
    while (lastChild != null && isWrongElement(lastChild)) {
      lastChild = lastChild.getPrevSibling();
    }
    return lastChild;
  }

  public void setContext(PsiElement context) {
    if (context != null) {
      myContext = context;
    }
  }

  public List<ClDef> getFileDefinitions() {
    final List<ClDef> result = new ArrayList<ClDef>();
    StubTree stubTree = getStubTree();
    if (stubTree != null) {
      for (StubElement<?> element : stubTree.getPlainList()) {
        if (element.getStubType() == ClojureElementTypes.DEF || element.getStubType() == ClojureElementTypes.DEFMETHOD) {
          PsiElement psi = element.getPsi();
          if (psi instanceof ClDef) {
            result.add((ClDef) psi);
          }
        }
      }
    } else {
      PsiTreeUtil.processElements(this, new PsiElementProcessor() {
        public boolean execute(@NotNull PsiElement element) {
          if (element instanceof ClDef) {
            result.add((ClDef) element);
          }
          return true;
        }
      });
    }
    return result;
  }

  public boolean isClassDefiningFile() {
    StubElement stub = getStub();
    if (stub instanceof ClFileStub) {
      return ((ClFileStub) stub).isClassDefinition();
    }

    final ClNs ns = (ClNs) ClojurePsiUtil.findFormByName(this, "ns");
    if (ns == null) return false;
    return ns.isClassDefinition();
  }

  public String getNamespace() {
    final ClNs ns = getNamespaceElement();
    return ns == null ? null : ns.getName();
  }

  public ClNs getNamespaceElement() {
    return ((ClNs) ClojurePsiUtil.findFormByNameSet(this, ClojureParser.NS_TOKENS));
  }

  @NotNull
  public ClNs findOrCreateNamespaceElement() throws IncorrectOperationException {
    final ClNs ns = getNamespaceElement();
    if (ns != null) return ns;
    commitDocument();
    final ClojurePsiFactory factory = ClojurePsiFactory.getInstance(getProject());
    final ClList nsList = factory.createListFromText(ImportOwner.NS + " " + getName());
    final PsiElement anchor = getFirstChild();
    if (anchor != null) {
      return (ClNs) addBefore(nsList, anchor);
    } else {
      return (ClNs) add(nsList);
    }
  }

  public String getClassName() {
    StubElement stub = getStub();
    if (stub instanceof ClFileStub) {
      return ((ClFileStub) stub).getClassName().getString();
    }

    String namespace = getNamespace();
    if (namespace == null) return null;
    int i = namespace.lastIndexOf(".");
    return i > 0 && i < namespace.length() - 1 ? namespace.substring(i + 1) : namespace;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (truthy(FileResolve.var.invoke(this, processor, state, lastParent, place)))
    {
      return false;
    }
    return super.processDeclarations(processor, state, lastParent, place);
  }

  private static final class FileResolve
  {
    private static final Var var = RT.var("plugin.resolve.files", "process-file-declarations");
  }

  public static boolean processDeclarations(ClojureFileImpl file, PsiScopeProcessor processor, ResolveState resolveState, PsiElement lastParent, PsiElement place) {
    //Process precedent read forms
    processChildren(file, processor, resolveState, lastParent, place);

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());

    Atom state = file.getCopyableUserData(ClojureConsole.STATE_KEY);
    if (state == null) {
      Project project = place.getProject();
      Metrics metrics = Metrics.getInstance(project);

      Metrics.Timer.Instance timer = metrics.start("file.topLevelPackages");
      try {
        //Add top-level package names
        final PsiPackage rootPackage = facade.findPackage("");
        if (rootPackage != null) {
          NamespaceUtil.getNamespaceElement(rootPackage).processDeclarations(processor, resolveState, null, place);
        }
      } finally {
        timer.stop();
      }

      String name = ResolveUtil.getName(processor, resolveState);
      if (name == null) {
        timer = metrics.start("file.javaLangClasses.noindex");
        try {
          // Add all java.lang classes
          final PsiPackage javaLang = facade.findPackage(ClojurePsiUtil.JAVA_LANG);
          if (javaLang != null) {
            for (PsiClass clazz : javaLang.getClasses()) {
              if (!ResolveUtil.processElement(processor, clazz)) {
                return false;
              }
            }
          }
        } finally {
          timer.stop();
        }
      } else {
        timer = metrics.start("file.javaLangClasses.indexed");
        try {
          PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(project);
          for (PsiClass psiClass : namesCache.getClassesByName(name, GlobalSearchScope.allScope(project))) {
            if (psiClass.getQualifiedName().equals(ClojurePsiUtil.JAVA_LANG + "." + name)) {
              if (!ResolveUtil.processElement(processor, psiClass)) {
                return false;
              }
            }
          }
        } finally {
          timer.stop();
        }
      }

      if (!processCoreSymbols(file, processor, resolveState, place)) return false;
    } else {
      // Completion for REPL
      Associative stateValue = (Associative) state.deref();
      Object repl = stateValue.valAt(REPL_KEYWORD);
      Var doCompletions = RT.var("plugin.repl", "completions");

      Map<Keyword, Collection<String>> completions =
          (Map<Keyword, Collection<String>>) doCompletions.invoke(repl, state);

      Associative symbols = (Associative) completions.get(SYMBOLS_KEYWORD);
      if (symbols != null) {
        for (ISeq seq = symbols.seq(); seq != null; seq = seq.next()) {
          Map.Entry entry = (Map.Entry) seq.first();
          ClojureConsoleElement element = new ClojureConsoleElement(file.getManager(), (String) entry.getKey(), Keyword.intern((String) entry.getValue()));
          if (!ResolveUtil.processElement(processor, element)) {
            return false;
          }
        }
      }

      Collection<String> namespaces = completions.get(NAMESPACES_KEYWORD);
      if (namespaces != null) {
        for (String namespace : topLevel(namespaces)) {
          if (!ResolveUtil.processElement(
              processor,
              new CompletionSyntheticNamespace(state, PsiManager.getInstance(file.getProject()), namespace, namespace, namespaces))) {
            return false;
          }
        }
      }

      Collection<String> imports = completions.get(IMPORTS_KEYWORD);
      if (imports != null) {
        for (String fqn : imports) {
          PsiClass psiClass = facade.findClass(fqn, GlobalSearchScope.allScope(file.getProject()));
          if (psiClass != null) {
            if (!ResolveUtil.processElement(processor, psiClass)) {
              return false;
            }
          }
        }
      }
    }

    return true;
  }

  private static boolean processCoreSymbols(ClojureFileImpl file, PsiScopeProcessor processor, ResolveState resolveState, PsiElement place) {
    // We don't resolve symbols that come from the same file as place. This is to stop us
    // resolving symbols in infinite loops when editing e.g. clojure.core.
    PsiFile placeFile = place.getContainingFile();

    String name = ResolveUtil.getName(processor, resolveState);
    if (name == null) {
      Metrics.Timer.Instance timer = Metrics.getInstance(place.getProject()).start("file.coreSymbols.noindex");
      try {
        // If we don't know the name of the element (i.e. during completion) we add all symbols from clojure.core
        for (PsiNamedElement element : NamespaceUtil.getDeclaredElements(ClojureUtils.CORE_NAMESPACE, file.getProject())) {
          if (element.getContainingFile() != placeFile && !ResolveUtil.processElement(processor, element)) {
            return false;
          }
        }
      } finally {
        timer.stop();
      }
    } else {
      Metrics.Timer.Instance timer = Metrics.getInstance(place.getProject()).start("file.coreSymbols.indexed");
      try {
        // We know the name of the element, so we'll look up all files containing symbols from clojure.core.
        // Then we'll look up all defined symbols with the given name and process any of them whose files
        // are one of the clojure.core files.
        StubIndex stubIndex = StubIndex.getInstance();
        Project project = file.getProject();
        Collection<VirtualFile> coreFiles = new HashSet<VirtualFile>();
        for (ClNs ns : stubIndex.get(ClojureNsNameIndex.KEY, ClojureUtils.CORE_NAMESPACE, project, GlobalSearchScope.allScope(project))) {
          PsiFile psiFile = ns.getContainingFile();
          if (!psiFile.equals(placeFile)) {
            coreFiles.add(psiFile.getVirtualFile());
          }
        }
        for (ClDef def : stubIndex.get(ClDefNameIndex.KEY, name, project, GlobalSearchScope.filesScope(project, coreFiles))) {
          if (!ResolveUtil.processElement(processor, def)) {
            return false;
          }
        }
      } finally {
        timer.stop();
      }
    }
    return true;
  }

  public static boolean processChildren(PsiElement element, PsiScopeProcessor processor,
                                        ResolveState substitutor, PsiElement lastParent, PsiElement place) {
    Metrics.Timer.Instance timer = Metrics.getInstance(place.getProject()).start("file.processChildren.noindex");
    try {
      PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
      while (run != null) {
        if (PsiTreeUtil.findCommonParent(place, run) != run && !run.processDeclarations(processor, substitutor, null, place))
          return false;
        run = run.getPrevSibling();
      }

      return true;
    } finally {
      timer.stop();
    }
  }

  public static Collection<String> topLevel(Collection<String> namespaces) {
    Collection<String> ret = new HashSet<String>();
    for (String namespace : namespaces) {
      int index = namespace.indexOf('.');
      if (index > 0) {
        ret.add(namespace.substring(0, index));
      } else {
        ret.add(namespace);
      }
    }
    return ret;
  }

  public static Collection<String> nextLevel(Collection<String> namespaces, String fqn) {
    Collection<String> ret = new HashSet<String>();
    for (String namespace : namespaces) {
      if (namespace.startsWith(fqn + '.')) {
        String shortName = StringUtil.trimStart(namespace, fqn + ".");
        int index = shortName.indexOf('.');
        if (index > 0) {
          ret.add(shortName.substring(0, index));
        } else {
          ret.add(shortName);
        }
      }
    }
    return ret;
  }

  public static class CompletionSyntheticNamespace extends ClSyntheticNamespace {
    private final Atom state;
    private final Collection<String> namespaces;

    public CompletionSyntheticNamespace(Atom state, PsiManager manager, String name, String fqn, Collection<String> namespaces) {
      super(manager, name, fqn, null);
      this.state = state;
      this.namespaces = namespaces;
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
      return !truthy(CompletionResolve.var.invoke(this, processor, state, lastParent, place));
    }

    private static final class CompletionResolve
    {
      private static final Var var = RT.var("plugin.resolve.namespaces", "process-completion-ns-decls");
    }

    public static boolean
    processDeclarations(CompletionSyntheticNamespace namespace, @NotNull PsiScopeProcessor processor, @NotNull ResolveState state) {
      final String qualifiedName = namespace.getQualifiedName();
      PsiElement separator = state.get(CompleteSymbol.SEPARATOR);

      if ((separator == null) || separator.getText().equals(".")) {
        for (String ns : nextLevel(namespace.namespaces, qualifiedName)) {
          if (!ResolveUtil.processElement(processor, new CompletionSyntheticNamespace(namespace.state, namespace.getManager(), ns, qualifiedName + '.' + ns, namespace.namespaces))) {
            return false;
          }
        }
      }

      if ((separator == null) || separator.getText().equals("/")) {
        Associative stateValue = (Associative) namespace.state.deref();
        Object repl = stateValue.valAt(REPL_KEYWORD);
        Var nsSymbols = RT.var("plugin.repl", "ns-symbols");

        Collection<String> symbolsInNS = (Collection<String>) nsSymbols.invoke(repl, namespace.state, qualifiedName);
        if (symbolsInNS != null) {
          for (String symbol : symbolsInNS) {
            if (!ResolveUtil.processElement(processor, new ClojureConsoleElement(namespace.getManager(), symbol, Keyword.intern(qualifiedName, symbol)))) {
              return false;
            }
          }
        }
      }

      return true;
    }
  }

  public PsiElement setClassName(@NonNls String s) {
    //todo implement me!
    return null;
  }

  protected void commitDocument() {
    final Project project = getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(this);
    if (document != null) {
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
  }
}
