package org.jetbrains.plugins.clojure.repl;

import com.intellij.openapi.project.Project;

public class ClojureConsoleView extends LanguageConsoleViewImpl
{
  public ClojureConsoleView(Project project, String title)
  {
    super(project, new ClojureConsole(project, title));
  }

  public ClojureConsole getConsole()
  {
    return (ClojureConsole) super.getConsole();
  }
}
