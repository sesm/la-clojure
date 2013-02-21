package org.jetbrains.plugins.clojure.repl;

import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.ClojureBundle;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.util.List;

/**
 * @author Colin Fleming
 */
public class REPLComponent implements ApplicationComponent
{
  private final Logger logger = Logger.getLogger(REPLComponent.class);
  @NonNls
  public static final String REQUIRE_FUNCTION = "require";
  @NonNls
  public static final String NREPL_NS = "clojure.tools.nrepl";
  @NonNls
  public static final Symbol NREPL_NS_SYMBOL = Symbol.intern(NREPL_NS);
  @NonNls
  private static final Symbol START_SERVER = Symbol.intern("clojure.tools.nrepl/start-server");
  @NonNls
  public static final String INITIALISE_NS = "plugin.initialise";
  @NonNls
  public static final Symbol INITIALISE_NS_SYMBOL = Symbol.intern(INITIALISE_NS);
  @NonNls
  private static final Symbol INITIALISE = Symbol.intern("plugin.initialise/initialise-all");
  @NonNls
  private static final String COMPONENT_NAME = "clojure.support.repl";

  private ServerSocket replServerSocket = null;

  public void initComponent()
  {
    ClassLoader loader = REPLComponent.class.getClassLoader();

    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
    try
    {
      Thread.currentThread().setContextClassLoader(loader);

      StringWriter writer = new StringWriter();

      Var.pushThreadBindings(RT.map(clojure.lang.Compiler.LOADER, loader,
                                    RT.var("clojure.core", "*warn-on-reflection*"), true,
                                    RT.ERR, writer));

      RT.var(ClojureUtils.CORE_NAMESPACE, REQUIRE_FUNCTION).invoke(NREPL_NS_SYMBOL);
      replServerSocket = (ServerSocket) ((List<?>) Var.find(START_SERVER).invoke()).get(0);
      logger.info(ClojureBundle.message("started.local.repl", Integer.valueOf(replServerSocket.getLocalPort())));

      RT.var(ClojureUtils.CORE_NAMESPACE, REQUIRE_FUNCTION).invoke(INITIALISE_NS_SYMBOL);
      Var.find(INITIALISE).invoke();

      String result = writer.toString();
      logger.error("Reflection warnings:\n" + result);
    }
    catch (Exception e)
    {
      logger.error(e, e);
    }
    finally
    {
      Var.popThreadBindings();
      Thread.currentThread().setContextClassLoader(oldLoader);
    }
  }

  public void disposeComponent()
  {
    if (replServerSocket != null)
    {
      try
      {
        replServerSocket.close();
      }
      catch (IOException e)
      {
        logger.error(e, e);
      }
    }
  }

  @NotNull
  public String getComponentName()
  {
    return COMPONENT_NAME;
  }

  public static int getLocalPort()
  {
    Application application = ApplicationManager.getApplication();
    REPLComponent component = (REPLComponent) application.getComponent(COMPONENT_NAME);
    if ((component != null) && (component.replServerSocket != null))
    {
      return component.replServerSocket.getLocalPort();
    }
    return -1;
  }
}
