package org.jetbrains.plugins.clojure.repl.toolwindow.actions;

import org.jetbrains.plugins.clojure.repl.REPLProvider;
import org.jetbrains.plugins.clojure.repl.impl.ClojureIDEREPL;

public class NewIDEConsoleAction extends NewConsoleActionBase
{
  @Override
  protected REPLProvider getProvider()
  {
    return new ClojureIDEREPL.Provider();
  }
}
