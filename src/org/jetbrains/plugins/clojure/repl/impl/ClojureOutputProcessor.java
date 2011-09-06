package org.jetbrains.plugins.clojure.repl.impl;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.text.StringUtil;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.plugins.clojure.repl.ClojureConsole;
import org.jetbrains.plugins.clojure.repl.ClojureConsoleHighlightingUtil;
import org.jetbrains.plugins.clojure.repl.LanguageConsoleImpl;
import org.jetbrains.plugins.clojure.utils.Editors;

import java.util.regex.Matcher;

/**
 * @author Colin Fleming
 */
public class ClojureOutputProcessor
{
//  private static final RunAutomaton ERRORS = errors();

  enum State
  {
    STARTING, AT_PROMPT, EXECUTING
  }

  @GuardedBy("lock")
  private State state = State.STARTING;
  @GuardedBy("lock")
  private Runnable onCommandExit;

  private final Object lock = new Object();

  private final ClojureConsole console;

  private final StringBuilder buffer = new StringBuilder();

  public ClojureOutputProcessor(ClojureConsole console)
  {
    this.console = console;
  }

  public void processOutput(String text)
  {
    if (text != null)
    {
      boolean sawPrompt = false;

      String trimmed = text;
      Matcher matcher = ClojureConsoleHighlightingUtil.CLOJURE_PROMPT_PATTERN.matcher(trimmed);
      while (matcher.lookingAt())
      {
        sawPrompt = true;
        String prefix = matcher.group();
        String namespace = matcher.group(1);
        console.setTitle(namespace);
        trimmed = StringUtil.trimStart(trimmed, prefix);
        matcher.reset(trimmed);
      }

      synchronized (lock)
      {
        if (sawPrompt)
        {
          if (state == State.STARTING)
          {
            state = State.AT_PROMPT;
          }
          else
          {
            flush();

            if (state == State.EXECUTING)
            {
              state = State.AT_PROMPT;
              if (onCommandExit != null)
              {
                onCommandExit.run();
                onCommandExit = null;
              }
            }
          }
          buffer.append(trimmed);
        }
        else
        {
          if (state == State.STARTING)
          {
            LanguageConsoleImpl.printToConsole(console, trimmed, ConsoleViewContentType.NORMAL_OUTPUT, null);
            Editors.scrollDown(console.getHistoryViewer());
          }
          else
          {
            buffer.append(trimmed);
          }
        }
      }
    }
  }

  public void executing(Runnable onCommandExit)
  {
    synchronized (lock)
    {
      if (state != State.AT_PROMPT)
      {
        throw new IllegalStateException("Expected state AT_PROMPT, found " + state);
      }

      state = State.EXECUTING;
      this.onCommandExit = onCommandExit;
    }
  }

  public boolean ifExecuting(Runnable runnable)
  {
    synchronized (lock)
    {
      if (state == State.EXECUTING)
      {
        runnable.run();
        return true;
      }
    }
    return false;
  }

  public void flush()
  {
    String output = buffer.toString();
    ConsoleViewContentType style = ConsoleViewContentType.NORMAL_OUTPUT;
//    if (looksLikeError(output))
//    {
//      style = ConsoleViewContentType.ERROR_OUTPUT;
//    }
    LanguageConsoleImpl.printToConsole(console, output, style, null);
    Editors.scrollDown(console.getHistoryViewer());
    buffer.setLength(0);
  }

//  static boolean looksLikeError(String output)
//  {
//    int length = ERRORS.run(output, 0);
//    return length > 0;
//  }

//  private static RunAutomaton errors()
//  {
//    Automaton errors = BasicOperations.union(Arrays.asList(automaton("Error\\.[ \t]*\n"),
//                                                           automaton("Error in .+\\.[ \t]*\n"),
//                                                           automaton("Error:[^\n]+\n"),
//                                                           automaton("Error in .+: [^\n]+\n"),
//                                                           automaton("Error in nested call\\.[ \t]*\n"),
//                                                           automaton("Error in nested call: [^\n]+\n"),
//                                                           automaton("Error in nested call from .+\\.[ \t]*\n"),
//                                                           automaton("Error in nested call from .+: [^\n]+\n")));
//    return new RunAutomaton(errors);
//  }
//
//  private static Automaton automaton(String regexp)
//  {
//    return new RegExp(regexp).toAutomaton();
//  }
}
