package org.jetbrains.plugins.clojure.repl.impl;

import clojure.lang.Keyword;
import clojure.tools.nrepl.Connection;
import clojure.tools.nrepl.SafeFn;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.clojure.ClojureBundle;
import org.jetbrains.plugins.clojure.config.ClojureConfigUtil;
import org.jetbrains.plugins.clojure.config.ClojureFacet;
import org.jetbrains.plugins.clojure.repl.ClojureConsoleView;
import org.jetbrains.plugins.clojure.repl.REPL;
import org.jetbrains.plugins.clojure.repl.REPLComponent;
import org.jetbrains.plugins.clojure.repl.REPLException;
import org.jetbrains.plugins.clojure.repl.REPLProviderBase;
import org.jetbrains.plugins.clojure.repl.REPLUtil;
import org.jetbrains.plugins.clojure.repl.Response;
import org.jetbrains.plugins.clojure.repl.TerminateREPLDialog;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @author Colin Fleming
 */
public class ClojureProcessREPL extends REPLBase
{
  private static final Logger log = Logger.getLogger(ClojureProcessREPL.class);

  public static final String REPL_TITLE = ClojureBundle.message("repl.toolWindowName");
  private static final Pattern SPACES = Pattern.compile("\\s+");

  @NonNls
  public static final String WAIT_FOR_ACK = "wait-for-ack";
  @NonNls
  public static final String RESET_ACK_PORT = "reset-ack-port!";
  private static final long PROCESS_WAIT_TIME = 30000L;

  private final Module module;
  private final String workingDir;
  private ProcessHandler processHandler = null;
  private Connection connection = null;

  public ClojureProcessREPL(Project project, Module module, ClojureConsoleView consoleView, String workingDir)
  {
    super(consoleView, project);
    this.module = module;
    this.workingDir = workingDir;
  }

  public void start() throws REPLException
  {
    SafeFn.find(REPLComponent.NREPL_NS, RESET_ACK_PORT).sInvoke();

    Process process;
    GeneralCommandLine commandLine;
    try
    {
      commandLine = createRuntimeArgs();
      process = createProcess(commandLine);
    }
    catch (ExecutionException e)
    {
      throw new REPLException(e);
    }

    processHandler = new ClojureProcessHandler(process, commandLine.getCommandLineString());
    ProcessTerminatedListener.attach(processHandler);
    processHandler.addProcessListener(new ProcessAdapter()
    {
      @Override
      public void processTerminated(ProcessEvent event)
      {
        consoleView.getConsole().getConsoleEditor().setRendererMode(true);
        hideEditor();
      }
    });

    getConsoleView().attachToProcess(processHandler);
    processHandler.startNotify();

    SafeFn waitForAck = SafeFn.find(REPLComponent.NREPL_NS, WAIT_FOR_ACK);
    Integer maybePort = (Integer) waitForAck.sInvoke(Long.valueOf(PROCESS_WAIT_TIME));

    if (maybePort == null)
    {
      stop();
      throw new REPLException("Error waiting for REPL process start");
    }

    try
    {
      connection = new Connection("localhost", maybePort.intValue());
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
    }

    if (processHandler instanceof KillableProcess && processHandler.isProcessTerminating())
    {
      ((KillableProcess) processHandler).killProcess();
      return;
    }

    if (processHandler.detachIsDefault())
    {
      processHandler.detachProcess();
    }
    else
    {
      processHandler.destroyProcess();
    }
  }

  @Override
  protected TerminateREPLDialog getTerminateDialog()
  {
    return new TerminateREPLDialog(project,
                                   ClojureBundle.message("repl.is.running", displayName),
                                   ClojureBundle.message("do.you.want.to.terminate.the.repl", displayName),
                                   ExecutionBundle.message("button.terminate"));
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
    if (!processHandler.isStartNotified())
    {
      return false;
    }
    return !(processHandler.isProcessTerminated() || processHandler.isProcessTerminating());
  }

  public String getType()
  {
    return "Process";
  }

  protected Process createProcess(GeneralCommandLine commandLine) throws ExecutionException
  {
    Process process = null;
    try
    {
      process = commandLine.createProcess();
    }
    catch (Exception e)
    {
      log.error("Error creating REPL process", e);
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), REPL_TITLE, null);
    }

    return process;
  }

  public GeneralCommandLine createRuntimeArgs() throws CantRunException
  {
    JavaParameters params = new JavaParameters();
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
    params.getVMParametersList().addAll(getJvmClojureOptions(module));
    params.getProgramParametersList().addAll(getReplClojureOptions(module));
    params.getProgramParametersList()
      .addAll(Arrays.asList("--port", Integer.toString(0), "--ack", Integer.toString(REPLComponent.getLocalPort())));

    boolean sdkConfigured = ClojureConfigUtil.isClojureConfigured(module);
    if (!sdkConfigured)
    {
      String jarPath = ClojureConfigUtil.CLOJURE_SDK;
      assert jarPath != null;
      params.getClassPath().add(jarPath);
    }

    String jarPath = ClojureConfigUtil.NREPL_LIB;
    assert jarPath != null;
    params.getClassPath().add(jarPath);

    REPLUtil.addSourcesToClasspath(module, params);

    params.setMainClass(ClojureUtils.REPL_MAIN);
    params.setWorkingDirectory(new File(workingDir));

    if (!sdkConfigured)
    {
      ClojureConfigUtil.warningDefaultClojureJar(module);
    }

    return REPLUtil.getCommandLine(params);
  }

  private static List<String> getJvmClojureOptions(Module module)
  {
    ClojureFacet facet = getClojureFacet(module);
    String opts = facet != null ? facet.getJvmOptions() : null;
    if (opts == null || opts.trim().isEmpty())
    {
      return Arrays.asList();
    }
    return Arrays.asList(SPACES.split(opts));
  }

  private static ClojureFacet getClojureFacet(Module module)
  {
    FacetManager manager = FacetManager.getInstance(module);
    return manager.getFacetByType(ClojureFacet.ID);
  }

  private static List<String> getReplClojureOptions(Module module)
  {
    ClojureFacet facet = getClojureFacet(module);
    String opts = facet != null ? facet.getReplOptions() : null;
    if (opts == null || opts.trim().isEmpty())
    {
      return Arrays.asList();
    }
    return Arrays.asList(SPACES.split(opts));
  }

  private static class ClojureProcessHandler extends ColoredProcessHandler
  {
    private ClojureProcessHandler(Process process, String commandLineString)
    {
      super(process, commandLineString, CharsetToolkit.UTF8_CHARSET);
    }

    @Override
    protected void textAvailable(String text, Key attributes)
    {
      log.info(text);
    }
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
      return new ClojureProcessREPL(project, module, consoleView, workingDir);
    }
  }
}
