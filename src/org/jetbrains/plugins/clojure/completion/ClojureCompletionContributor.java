package org.jetbrains.plugins.clojure.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.lexer.ClojureTokenTypes;
import org.jetbrains.plugins.clojure.psi.api.ClKeyword;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author ilyas
 */
public class ClojureCompletionContributor extends CompletionContributor
{
  public ClojureCompletionContributor()
  {
    extend(CompletionType.BASIC,
           psiElement().withElementType(ClojureTokenTypes.COLON_SYMBOL),
           new KeywordContributor());
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result)
  {
    super.fillCompletionVariants(parameters, result);
  }

  private static class KeywordContributor extends CompletionProvider<CompletionParameters>
  {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result)
    {
      PsiElement position = parameters.getPosition();
      int offset = parameters.getOffset();
      String prefix = position.getText().substring(0, offset - position.getTextRange().getStartOffset());
      processAllKeywords(result.withPrefixMatcher(prefix), parameters.getPosition().getContainingFile());
    }

    public static boolean processAllKeywords(@NotNull CompletionResultSet result, PsiElement element)
    {
      if (element instanceof ClKeyword)
      {
        String text = element.getText();
        if (!text.contains(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED))
        {
          result.addElement(LookupElementBuilder.create(text));
        }
      }

      PsiElement child = element.getFirstChild();
      while (child != null)
      {
        processAllKeywords(result, child);
        child = child.getNextSibling();
      }

      return true;
    }
  }
}
