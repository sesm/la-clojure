package org.jetbrains.plugins.clojure.repl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.file.ClojureFileType;

/**
 * @author Colin Fleming
 */
public class ClojureConsoleElement extends LightElement implements PsiNamedElement
{
  @NotNull
  private final String name;

  public ClojureConsoleElement(PsiManager manager, @NotNull String name)
  {
    super(manager, ClojureFileType.CLOJURE_LANGUAGE);
    this.name = name;
  }

  @Override
  public String getText()
  {
    return name;
  }

  @Override
  public PsiElement copy()
  {
    return null;
  }

  @NotNull
  public String getName()
  {
    return name;
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException
  {
    throw new UnsupportedOperationException("Can't set name for console elements");
  }

  @Override
  public String toString()
  {
    return "Console element " + name;
  }
}
