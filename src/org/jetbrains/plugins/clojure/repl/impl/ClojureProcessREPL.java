package org.jetbrains.plugins.clojure.repl.impl;

import clojure.lang.Keyword;
import clojure.lang.RT;
import clojure.lang.Var;
import clojure.tools.nrepl.Connection;
import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleViewContentType;
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
import org.jetbrains.plugins.clojure.repl.*;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * @author Colin Fleming
 */
public class ClojureProcessREPL extends REPLBase {
  private static final Logger log = Logger.getLogger(ClojureProcessREPL.class);

  public static final String REPL_TITLE = ClojureBundle.message("repl.toolWindowName");
  private static final Pattern SPACES = Pattern.compile("\\s+");

  @NonNls
  public static final String WAIT_FOR_ACK = "wait-for-ack";
  @NonNls
  public static final String RESET_ACK_PORT = "reset-ack-port!";
  private static final long PROCESS_WAIT_TIME = 30000L;
  @NonNls
  public static final String INTERNAL_NAMESPACE = "la-clojure.repl";

  private final Module module;
  private final String workingDir;
  private ProcessHandler processHandler = null;
  private Connection connection = null;
  private AtomicReference<Map<Keyword, Collection<String>>> completions =
      new AtomicReference<Map<Keyword, Collection<String>>>();
  private AtomicReference<String> namespace = new AtomicReference<String>("user");

  public ClojureProcessREPL(Project project, Module module, ClojureConsoleView consoleView, String workingDir) {
    super(consoleView, project);
    this.module = module;
    this.workingDir = workingDir;
  }

  public void start() throws REPLException {
    RT.var(REPLComponent.NREPL_ACK_NS, RESET_ACK_PORT).invoke();

    Process process;
    GeneralCommandLine commandLine;
    try {
      commandLine = createRuntimeArgs();
      process = createProcess(commandLine);
    } catch (ExecutionException e) {
      throw new REPLException(e);
    }

    processHandler = new ClojureProcessHandler(process, commandLine.getCommandLineString());
    ProcessTerminatedListener.attach(processHandler);
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        consoleView.getConsole().getConsoleEditor().setRendererMode(true);
        hideEditor();
      }
    });

    getConsoleView().attachToProcess(processHandler);
    processHandler.startNotify();

    Var waitForAck = RT.var(REPLComponent.NREPL_ACK_NS, WAIT_FOR_ACK);
    Number maybePort = (Number) waitForAck.invoke(Long.valueOf(PROCESS_WAIT_TIME));

    if (maybePort == null) {
      stop();
      throw new REPLException("Error waiting for REPL process start");
    }

    try {
      int port = maybePort.intValue();
      log.info("Received ack, connecting to port " + port);
      connection = new Connection("nrepl://localhost:" + port);
    } catch (Exception e) {
      log.error("Error connecting to REPL: " + e.getMessage(), e);
      stop();
      throw new REPLException(e);
    }

    try {
      // Initialise completion code
      String nsName = (String) getSingleResponse("(str (ns-name *ns*))");
      executeWithoutErrors("(ns " + INTERNAL_NAMESPACE + ")");
      executeWithoutErrors(
          "(defn ns-symbols [the-ns]\n" +
              "  (map str (keys (ns-interns the-ns))))");
      executeWithoutErrors(
          "(defn ns-symbols-by-name [ns-name]\n" +
              "  (if-let [the-ns (find-ns (symbol ns-name))]\n" +
              "    (ns-symbols the-ns)))");
      executeWithoutErrors(
          "(defn completions []\n" +
              "  {:imports    (map (fn [c] (.getName c)) (vals (ns-imports *ns*))),\n" +
              "   :symbols    (map str (keys (filter (fn [v] (var? (second v))) (seq (ns-map *ns*))))) \n" +
              "   :namespaces (map str (all-ns))})");
      completions.set((Map<Keyword, Collection<String>>) getSingleResponse("(" + INTERNAL_NAMESPACE + "/completions)"));
      executeWithoutErrors("(in-ns '" + nsName + ")");
    } catch (REPLException e) {
      stop();
      printError("Error initialising completion code:\n" + e.getMessage());
      throw e;
    } catch (Exception e) {
      stop();
      printError("Error initialising completion code:\n" + e.getMessage());
      throw new REPLException(e);
    }
  }

  private void executeWithoutErrors(String command) throws REPLException {
    Response response = innerDoExecute(command);
    if (response.errorOutput() != null) {
      throw new REPLException("Received error from call: " + command + ": " + response.errorOutput());
    }
  }

  private Object getSingleResponse(String command) throws REPLException {
    Response response = innerDoExecute(command);
    if (response.errorOutput() != null) {
      throw new REPLException("Received error from call: " + command + ": " + response.errorOutput());
    }
    List<Object> values = response.values();
    if (values.size() != 1) {
      throw new REPLException("Expected 1 value from call, received " + values.size() + ": " + values);
    }
    return values.iterator().next();
  }

  private void printError(String text) {
    ClojureConsole console = getConsoleView().getConsole();
    console.printToHistory(text, ConsoleViewContentType.ERROR_OUTPUT.getAttributes());
  }

  @Override
  public void doStop() {
    if (connection != null) {
      try {
        connection.close();
      } catch (IOException e) {
        log.error("Error closing connection", e);
      }
      connection = null;
    }

    if (processHandler instanceof KillableProcess && processHandler.isProcessTerminating()) {
      ((KillableProcess) processHandler).killProcess();
      return;
    }

    if (processHandler.detachIsDefault()) {
      processHandler.detachProcess();
    } else {
      processHandler.destroyProcess();
    }
  }

  @Override
  protected TerminateREPLDialog getTerminateDialog() {
    return new TerminateREPLDialog(project,
        ClojureBundle.message("repl.is.running", displayName),
        ClojureBundle.message("do.you.want.to.terminate.the.repl", displayName),
        ExecutionBundle.message("button.terminate"));
  }

  @Override
  public Response doExecute(String command) {
    Response response = innerDoExecute(command);

    try {
      completions.set((Map<Keyword, Collection<String>>) getSingleResponse("(" + INTERNAL_NAMESPACE + "/completions)"));
    } catch (REPLException e) {
      printError("Error getting completions: " + e.getMessage());
    }

    return response;
  }

  private Response innerDoExecute(final String command) {
    setEditorEnabled(false);

    return new Response(connection.send("op", "eval", "code", command, "ns", namespace.get())) {
      private final AtomicBoolean editorEnabled = new AtomicBoolean(false);

      @Override
      public Map<String, Object> combinedResponse() {
        Map<String, Object> ret = super.combinedResponse();

        namespace.getAndSet((String) ret.get(NAMESPACE));

        if (!editorEnabled.getAndSet(true)) {
          setEditorEnabled(true);
        }
        return ret;
      }
    };
  }

  public boolean isActive() {
    if (!processHandler.isStartNotified()) {
      return false;
    }
    return !(processHandler.isProcessTerminated() || processHandler.isProcessTerminating());
  }

  public String getType() {
    return "Process";
  }

  public Map<Keyword, Collection<String>> getCompletions() {
    return completions.get();
  }

  public Collection<String> getSymbolsInNS(String ns) {
    try {
      Collection<String> response = (Collection<String>) getSingleResponse("(" + INTERNAL_NAMESPACE + "/ns-symbols-by-name \"" + ns + "\")");
      return response == null ? Collections.<String>emptyList() : response;
    } catch (REPLException e) {
      printError("Error getting completions: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  protected Process createProcess(GeneralCommandLine commandLine) throws ExecutionException {
    Process process = null;
    try {
      process = commandLine.createProcess();
    } catch (Exception e) {
      log.error("Error creating REPL process", e);
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), REPL_TITLE, null);
    }

    return process;
  }

  public GeneralCommandLine createRuntimeArgs() throws CantRunException {
    JavaParameters params = new JavaParameters();
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
    params.getVMParametersList().addAll(getJvmClojureOptions(module));
    params.getProgramParametersList().addAll(getReplClojureOptions(module));
    params.getProgramParametersList()
        .addAll(Arrays.asList("--port", Integer.toString(0), "--ack", Integer.toString(REPLComponent.getLocalPort())));

    boolean sdkConfigured = ClojureConfigUtil.isClojureConfigured(module);
    if (!sdkConfigured) {
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

    if (!sdkConfigured) {
      ClojureConfigUtil.warningDefaultClojureJar(module);
    }

    return REPLUtil.getCommandLine(params);
  }

  private static List<String> getJvmClojureOptions(Module module) {
    ClojureFacet facet = getClojureFacet(module);
    String opts = facet != null ? facet.getJvmOptions() : null;
    if (opts == null || opts.trim().isEmpty()) {
      return Arrays.asList();
    }
    return Arrays.asList(SPACES.split(opts));
  }

  private static ClojureFacet getClojureFacet(Module module) {
    FacetManager manager = FacetManager.getInstance(module);
    return manager.getFacetByType(ClojureFacet.ID);
  }

  private static List<String> getReplClojureOptions(Module module) {
    ClojureFacet facet = getClojureFacet(module);
    String opts = facet != null ? facet.getReplOptions() : null;
    if (opts == null || opts.trim().isEmpty()) {
      return Arrays.asList();
    }
    return Arrays.asList(SPACES.split(opts));
  }

  private static class ClojureProcessHandler extends ColoredProcessHandler {
    private ClojureProcessHandler(Process process, String commandLineString) {
      super(process, commandLineString, CharsetToolkit.UTF8_CHARSET);
    }

    @Override
    protected void textAvailable(String text, Key attributes) {
      log.info(text);
    }
  }

  public static class Provider extends REPLProviderBase {
    @Override
    public boolean isSupported() {
      return true;
    }

    @Override
    public REPL newREPL(Project project, Module module, ClojureConsoleView consoleView, String workingDir) {
      return new ClojureProcessREPL(project, module, consoleView, workingDir);
    }
  }
}
