package org.jetbrains.plugins.clojure.repl.impl;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.CommandLineArgumentsProvider;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.plugins.clojure.ClojureBundle;
import org.jetbrains.plugins.clojure.config.ClojureConfigUtil;
import org.jetbrains.plugins.clojure.config.ClojureFacet;
import org.jetbrains.plugins.clojure.repl.ClojureConsoleView;
import org.jetbrains.plugins.clojure.repl.REPL;
import org.jetbrains.plugins.clojure.repl.REPLException;
import org.jetbrains.plugins.clojure.repl.REPLProviderBase;
import org.jetbrains.plugins.clojure.repl.REPLUtil;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Colin Fleming
 */
public class ClojureProcessREPL extends REPLBase
{
  public static final String REPL_TITLE = ClojureBundle.message("repl.toolWindowName");
  private final Module module;
  private final String workingDir;
  private ProcessHandler processHandler;
  private ClojureOutputProcessor outputProcessor;

  public ClojureProcessREPL(Project project, Module module, ClojureConsoleView consoleView, String workingDir)
  {
    super(consoleView, project);
    this.module = module;
    this.workingDir = workingDir;
  }

  @Override
  public void start() throws REPLException
  {
    Process process;
    GeneralCommandLine commandLine;
    try
    {
      commandLine = createRuntimeArgs(module, workingDir);
      process = createProcess(commandLine);
    }
    catch (ExecutionException e)
    {
      throw new REPLException(e);
    }

    outputProcessor = new ClojureOutputProcessor(consoleView.getConsole());
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
  }

  @Override
  public void stop()
  {
    if (processHandler instanceof KillableProcess && processHandler.isProcessTerminating())
    {
      ((KillableProcess) processHandler).killProcess();
      outputProcessor.flush();
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
    outputProcessor.flush();
  }

  @Override
  public void execute(String command)
  {
    setEditorEnabled(false);

    outputProcessor.executing(new Runnable()
    {
      public void run()
      {
        setEditorEnabled(true);
      }
    });

    OutputStream outputStream = processHandler.getProcessInput();
    try
    {
      byte[] bytes = (command + '\n').getBytes();
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException ignore)
    {
    }
  }

  @Override
  public boolean isActive()
  {
    if (!processHandler.isStartNotified())
    {
      return false;
    }
    return !(processHandler.isProcessTerminated() || processHandler.isProcessTerminating());
  }

  @Override
  protected String getType()
  {
    return "Process";
  }

  public boolean isExecuting()
  {
    return outputProcessor.ifExecuting(new Runnable()
    {
      public void run()
      {
        // NOP
      }
    });
  }

  @Override
  public Collection<PsiNamedElement> getSymbolVariants(PsiManager manager, PsiElement symbol)
  {
    // TODO
    return Collections.emptyList();
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
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), REPL_TITLE, null);
    }

    return process;
  }

  public GeneralCommandLine createRuntimeArgs(Module module, String workingDir) throws CantRunException
  {
    JavaParameters params = new JavaParameters();
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
    params.getVMParametersList().addAll(getJvmClojureOptions(module));
    params.getProgramParametersList().addAll(getReplClojureOptions(module));

    final boolean sdkConfigured = ClojureConfigUtil.isClojureConfigured(module);
    if (!sdkConfigured)
    {
      final String jarPath = ClojureConfigUtil.CLOJURE_SDK;
      assert jarPath != null;
      params.getClassPath().add(jarPath);
    }

    REPLUtil.addSourcesToClasspath(module, params);

    params.setMainClass(ClojureUtils.CLOJURE_MAIN);
    params.setWorkingDirectory(new File(workingDir));

    if (!sdkConfigured)
    {
      ClojureConfigUtil.warningDefaultClojureJar(module);
    }

    return REPLUtil.getCommandLine(params);
  }

  private static List<String> getJvmClojureOptions(Module module)
  {
    final ClojureFacet facet = getClojureFacet(module);
    String opts = facet != null ? facet.getJvmOptions() : null;
    if (opts == null || opts.trim().isEmpty())
    {
      return Arrays.asList();
    }
    return Arrays.asList(opts.split("\\s+"));
  }

  private static ClojureFacet getClojureFacet(Module module)
  {
    final FacetManager manager = FacetManager.getInstance(module);
    return manager.getFacetByType(ClojureFacet.ID);
  }

  private static List<String> getReplClojureOptions(Module module)
  {
    final ClojureFacet facet = getClojureFacet(module);
    String opts = facet != null ? facet.getReplOptions() : null;
    if (opts == null || opts.trim().isEmpty())
    {
      return Arrays.asList();
    }
    return Arrays.asList(opts.split("\\s+"));
  }

  private class ClojureProcessHandler extends ColoredProcessHandler
  {
    public ClojureProcessHandler(Process process, String commandLineString)
    {
      super(process, commandLineString, CharsetToolkit.UTF8_CHARSET);
    }

    @Override
    protected void textAvailable(String text, Key attributes)
    {
      // TODO use attributes?
      outputProcessor.processOutput(text);
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
