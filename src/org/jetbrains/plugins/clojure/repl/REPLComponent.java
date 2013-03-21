package org.jetbrains.plugins.clojure.repl;

import clojure.lang.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.ClojureBundle;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringWriter;
import java.net.ServerSocket;

/**
 * @author Colin Fleming
 */
public class REPLComponent implements ApplicationComponent
{
  private final Logger logger = Logger.getLogger(REPLComponent.class);
  @NonNls
  public static final String REQUIRE_FUNCTION = "require";
  @NonNls
  public static final String NREPL_ACK_NS = "clojure.tools.nrepl.ack";
  @NonNls
  public static final String PLUGIN_SERVER_NS = "plugin.repl.server";
  @NonNls
  public static final Symbol PLUGIN_SERVER_NS_SYMBOL = Symbol.intern(PLUGIN_SERVER_NS);
  @NonNls
  private static final Symbol START_SERVER = Symbol.intern("plugin.repl.server/start-server");
  @NonNls
  public static final String INITIALISE_NS = "plugin.initialise";
  @NonNls
  public static final Symbol INITIALISE_NS_SYMBOL = Symbol.intern(INITIALISE_NS);
  @NonNls
  private static final Symbol INITIALISE = Symbol.intern("plugin.initialise/initialise-all");
  @NonNls
  private static final String COMPONENT_NAME = "clojure.support.repl";
  private static final Keyword SS_KEYWORD = Keyword.intern("ss");

  private Object replServer = null;

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

      RT.var(ClojureUtils.CORE_NAMESPACE, REQUIRE_FUNCTION).invoke(PLUGIN_SERVER_NS_SYMBOL);
      replServer = Var.find(START_SERVER).invoke();
      ServerSocket serverSocket = (ServerSocket) ((IPersistentMap) replServer).valAt(SS_KEYWORD);
      logger.info(ClojureBundle.message("started.local.repl", serverSocket.getLocalPort()));

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
    if (replServer != null)
    {
      try
      {
        ((Closeable) replServer).close();
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
    if ((component != null) && (component.replServer != null))
    {
      ServerSocket serverSocket = (ServerSocket) ((IPersistentMap) component.replServer).valAt(SS_KEYWORD);
      return serverSocket.getLocalPort();
    }
    return -1;
  }
}
