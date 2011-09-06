package org.jetbrains.plugins.clojure.repl;

import com.intellij.openapi.project.Project;

public class ClojureConsoleView extends LanguageConsoleViewImpl
{
  public ClojureConsoleView(Project project, String title, ConsoleHistoryModel historyModel)
  {
    super(project, new ClojureConsole(project, title, historyModel));
  }

  public ClojureConsole getConsole()
  {
    return (ClojureConsole) super.getConsole();
  }
}
