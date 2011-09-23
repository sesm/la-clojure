package org.jetbrains.plugins.clojure.repl.impl;

import clojure.lang.Keyword;
import clojure.tools.nrepl.Connection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.apache.log4j.Logger;
import org.jetbrains.plugins.clojure.repl.ClojureConsoleView;
import org.jetbrains.plugins.clojure.repl.REPL;
import org.jetbrains.plugins.clojure.repl.REPLComponent;
import org.jetbrains.plugins.clojure.repl.REPLException;
import org.jetbrains.plugins.clojure.repl.REPLProviderBase;
import org.jetbrains.plugins.clojure.repl.Response;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Colin Fleming
 */
public class ClojureIDEREPL extends REPLBase
{
  private static final Logger log = Logger.getLogger(ClojureIDEREPL.class);

  private Connection connection = null;

  public ClojureIDEREPL(Project project, ClojureConsoleView consoleView)
  {
    super(consoleView, project);
  }

  public void start() throws REPLException
  {
    try
    {
      connection = new Connection("localhost", REPLComponent.getLocalPort());
    }
    catch (Exception e)
    {
      log.error("Error connecting to REPL: " + e.getMessage(), e);
      stop();
      throw new REPLException(e);
    }
  }

  @Override
  public void doStop()
  {
    if (connection != null)
    {
      connection.close();
      connection = null;

      hideEditor();
    }
  }

  @Override
  public Response doExecute(String command)
  {
    setEditorEnabled(false);

    return new Response(connection.send(command))
    {
      private final AtomicBoolean editorEnabled = new AtomicBoolean(false);

      @Override
      public Map<Keyword, Object> combinedResponse()
      {
        Map<Keyword, Object> ret = super.combinedResponse();

        if (!editorEnabled.getAndSet(true))
        {
          setEditorEnabled(true);
        }
        return ret;
      }
    };
  }

  public boolean isActive()
  {
    return (connection != null);
  }

  public String getType()
  {
    return "IDE";
  }

  public static class Provider extends REPLProviderBase
  {
    @Override
    public boolean isSupported()
    {
      return true;
    }

    @Override
    public REPL newREPL(Project project, Module module, ClojureConsoleView consoleView, String workingDir)
    {
      return new ClojureIDEREPL(project, consoleView);
    }
  }
}
