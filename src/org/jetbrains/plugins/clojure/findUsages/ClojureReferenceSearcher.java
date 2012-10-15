package org.jetbrains.plugins.clojure.findUsages;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.clojure.psi.api.symbols.ClSymbol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mostly copied from com.intellij.psi.impl.search.PsiSearchHelperImpl
 * For reasoning, see:
 * http://devnet.jetbrains.net/thread/284176
 * http://youtrack.jetbrains.com/issue/IDEA-25346
 *
 * @author colin
 * @author ilyas
 */
public class ClojureReferenceSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#" + ClojureReferenceSearcher.class.getName());
  private PsiManagerEx myManager;

  public boolean execute(ReferencesSearch.SearchParameters params, final Processor<PsiReference> consumer) {
    final PsiElement elem = params.getElementToSearch();
    Project project = elem.getProject();
    myManager = (PsiManagerEx) PsiManager.getInstance(project);

    final SearchScope scope = params.getScope();
    if (elem instanceof PsiNamedElement) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final PsiNamedElement symbolToSearch = (PsiNamedElement) elem;
          final String name = symbolToSearch.getName();
          if (name != null) {
            final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
              public boolean execute(PsiElement element, int offsetInElement) {
                // TODO if we want to search in comments and strings we'll have to modify here
                // TODO element is the element where the text is found (e.g. PsiComment)
                if (element instanceof ClSymbol) {
                  ClSymbol refSymbol = (ClSymbol) element;
                  for (PsiReference ref : refSymbol.getReferences()) {
                    if (ref.getRangeInElement().contains(offsetInElement) &&
                        // atom may refer to definition or to the symbol in it
                        (ref.resolve() == symbolToSearch ||
                            ref.resolve() == symbolToSearch.getParent())) {
                      if (!consumer.process(ref)) return false;
                    }
                  }
                }
                return true;
              }
            };
            processElementsWithWord(processor, scope, name, UsageSearchContext.ANY, true);
          }
        }
      });
    }
    return true;
  }

  /*
   *  From here down, mostly copied from com.intellij.psi.impl.search.PsiSearchHelperImpl
   *  with some simplifications.
   */
  public boolean processElementsWithWord(@NotNull final TextOccurenceProcessor processor,
                                         @NotNull SearchScope searchScope,
                                         @NotNull final String text,
                                         short searchContext,
                                         final boolean caseSensitively) {
    if (text.length() == 0) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text, caseSensitively, true);

      return processElementsWithTextInGlobalScope(processor,
          (GlobalSearchScope) searchScope,
          searcher,
          searchContext, caseSensitively, progress);
    } else {
      LocalSearchScope scope = (LocalSearchScope) searchScope;
      PsiElement[] scopeElements = scope.getScope();
      final boolean ignoreInjectedPsi = scope.isIgnoreInjectedPsi();

      return JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Arrays.asList(scopeElements),
                                                                       progress,
                                                                       false,
                                                                       new Processor<PsiElement>()
                                                                       {
                                                                         public boolean process(PsiElement scopeElement)
                                                                         {
                                                                           return processElementsWithWordInScopeElement(
                                                                             scopeElement,
                                                                             processor,
                                                                             text,
                                                                             caseSensitively,
                                                                             ignoreInjectedPsi,
                                                                             progress);
                                                                         }
                                                                       });
    }
  }

  private boolean processElementsWithTextInGlobalScope(@NotNull final TextOccurenceProcessor processor,
                                                       @NotNull final GlobalSearchScope scope,
                                                       @NotNull final StringSearcher searcher,
                                                       final short searchContext,
                                                       final boolean caseSensitively,
                                                       final ProgressIndicator progress) {
    LOG.assertTrue(!Thread.holdsLock(PsiLock.LOCK), "You must not run search from within updating PSI activity. Please consider invokeLatering it instead.");
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    String text = searcher.getPattern();
    List<VirtualFile> fileSet = getFilesWithText(scope, searchContext, caseSensitively, text);

    if (progress != null) {
      progress.setText(PsiBundle.message("psi.search.for.word.progress", text));
    }

    try {
      return processPsiFileRoots(fileSet, new Processor<PsiElement>() {
        public boolean process(PsiElement psiRoot) {
          return LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher, true, progress);
        }
      }, progress);
    } finally {
      if (progress != null) {
        progress.popState();
      }
    }
  }

  @NotNull
  private List<VirtualFile> getFilesWithText(@NotNull GlobalSearchScope scope,
                                             final short searchContext,
                                             final boolean caseSensitively,
                                             @NotNull String text) {
    myManager.startBatchFilesProcessingMode();
    try {
      final List<VirtualFile> result = new ArrayList<VirtualFile>();
      boolean success = processFilesWithText(
          scope,
          searchContext,
          caseSensitively,
          text,
          new CommonProcessors.CollectProcessor<VirtualFile>(result)
      );
      LOG.assertTrue(success);
      return result;
    } finally {
      myManager.finishBatchFilesProcessingMode();
    }
  }

  public boolean processFilesWithText(@NotNull final GlobalSearchScope scope,
                                      final short searchContext,
                                      final boolean caseSensitively,
                                      @NotNull String text,
                                      @NotNull final Processor<VirtualFile> processor) {
    final CommonProcessors.CollectProcessor<VirtualFile> collectProcessor = new CommonProcessors.CollectProcessor<VirtualFile>();
    processFilesContainingAllKeys(scope, new Condition<Integer>() {
      public boolean value(Integer integer) {
        return (integer.intValue() & searchContext) != 0;
      }
    }, collectProcessor, Collections.singletonList(new IdIndexEntry(text, caseSensitively)));

    final FileIndexFacade index = FileIndexFacade.getInstance(myManager.getProject());
    return ContainerUtil.process(collectProcessor.getResults(), new ReadActionProcessor<VirtualFile>() {
      @Override
      public boolean processInReadAction(VirtualFile virtualFile) {
        return !index.shouldBeFound(scope, virtualFile) || processor.process(virtualFile);
      }
    });
  }

  private static boolean processFilesContainingAllKeys(final GlobalSearchScope scope,
                                                       @Nullable final Condition<Integer> checker,
                                                       final Processor<VirtualFile> processor, final Collection<IdIndexEntry> keys) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<Boolean>() {
      public Boolean compute() {
        return FileBasedIndex.getInstance().processFilesContainingAllKeys(IdIndex.NAME, keys, scope, checker, processor);
      }
    });
  }

  private boolean processPsiFileRoots(@NotNull List<VirtualFile> files,
                                      @NotNull final Processor<PsiElement> psiRootProcessor,
                                      final ProgressIndicator progress) {
    myManager.startBatchFilesProcessingMode();
    try {
      final AtomicInteger counter = new AtomicInteger(0);
      final AtomicBoolean canceled = new AtomicBoolean(false);
      final AtomicBoolean pceThrown = new AtomicBoolean(false);

      final int size = files.size();
      boolean completed = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(files, progress, false, new Processor<VirtualFile>() {
        public boolean process(final VirtualFile vfile) {
          final PsiFile file = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            public PsiFile compute() {
              return myManager.findFile(vfile);
            }
          });
          if (file != null && !(file instanceof PsiBinaryFile)) {
            file.getViewProvider().getContents(); // load contents outside readaction
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                try {
                  if (myManager.getProject().isDisposed()) throw new ProcessCanceledException();
                  List<PsiFile> psiRoots = file.getViewProvider().getAllFiles();
                  Set<PsiElement> processed = new HashSet<PsiElement>(psiRoots.size() * 2, (float) 0.5);
                  for (PsiElement psiRoot : psiRoots) {
                    if (progress != null) progress.checkCanceled();
                    if (!processed.add(psiRoot)) continue;
                    assert psiRoot != null : "One of the roots of file " + file + " is null. All roots: " + Arrays.asList(psiRoots) + "; Viewprovider: " + file.getViewProvider() + "; Virtual file: " + file.getViewProvider().getVirtualFile();
                    if (!psiRootProcessor.process(psiRoot)) {
                      canceled.set(true);
                      return;
                    }
                  }
                  myManager.dropResolveCaches();
                } catch (ProcessCanceledException e) {
                  canceled.set(true);
                  pceThrown.set(true);
                }
              }
            });
          }
          if (progress != null && progress.isRunning()) {
            double fraction = (double) counter.incrementAndGet() / size;
            progress.setFraction(fraction);
          }
          return !canceled.get();
        }
      });

      if (pceThrown.get()) {
        throw new ProcessCanceledException();
      }

      return completed;
    } finally {
      myManager.finishBatchFilesProcessingMode();
    }
  }

  private static boolean processElementsWithWordInScopeElement(final PsiElement scopeElement,
                                                               final TextOccurenceProcessor processor,
                                                               final String word,
                                                               final boolean caseSensitive,
                                                               final boolean ignoreInjectedPsi,
                                                               final ProgressIndicator progress) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        StringSearcher searcher = new StringSearcher(word, caseSensitive, true);

        return LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher, !ignoreInjectedPsi, progress);
      }
    }).booleanValue();
  }
}
