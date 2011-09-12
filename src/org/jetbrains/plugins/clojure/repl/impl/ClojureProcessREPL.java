package org.jetbrains.plugins.clojure.repl.impl;

import clojure.lang.IMapEntry;
import clojure.lang.IPersistentList;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentSet;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.Keyword;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import clojure.tools.nrepl.Connection;
import clojure.tools.nrepl.Connection.Response;
import com.intellij.execution.CantRunException;
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
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.clojure.ClojureBundle;
import org.jetbrains.plugins.clojure.config.ClojureConfigUtil;
import org.jetbrains.plugins.clojure.config.ClojureFacet;
import org.jetbrains.plugins.clojure.highlighter.ClojureSyntaxHighlighter;
import org.jetbrains.plugins.clojure.repl.ClojureConsole;
import org.jetbrains.plugins.clojure.repl.ClojureConsoleView;
import org.jetbrains.plugins.clojure.repl.REPL;
import org.jetbrains.plugins.clojure.repl.REPLException;
import org.jetbrains.plugins.clojure.repl.REPLProviderBase;
import org.jetbrains.plugins.clojure.repl.REPLUtil;
import org.jetbrains.plugins.clojure.utils.ClojureUtils;
import org.jetbrains.plugins.clojure.utils.Editors;

import java.io.File;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Colin Fleming
 */
public class ClojureProcessREPL extends REPLBase
{
  private static final Logger log = Logger.getLogger(ClojureProcessREPL.class);

  public static final String REPL_TITLE = ClojureBundle.message("repl.toolWindowName");
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final int NREPL_PORT = 8197;

  @NonNls
  private static final Keyword NAMESPACE_KEYWORD = Keyword.intern("ns");
  @NonNls
  private static final Keyword OUTPUT_KEYWORD = Keyword.intern("out");
  @NonNls
  private static final Keyword ERROR_KEYWORD = Keyword.intern("err");
  private static final TextAttributes NORMAL_TEXT = ConsoleViewContentType.NORMAL_OUTPUT.getAttributes();
  private static final int REPL_RETRY_COUNT = 20;
  private static final long REPL_RETRY_PAUSE = 200L;

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

  @Override
  public void start() throws REPLException
  {
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

    try
    {
      connect();
    }
    catch (REPLException e)
    {
      stop();
      throw e;
    }

    // Initialise REPL and set namespace
    Response response = connection.send("(clojure-version)");
    List<Object> values = response.values();
    if (!values.isEmpty())
    {
      print("Clojure " + values.get(0) + '\n', NORMAL_TEXT);
    }

    Map<Keyword, Object> items = response.combinedResponse();
    String namespace = (String) items.get(NAMESPACE_KEYWORD);
    ClojureConsole console = getConsoleView().getConsole();
    console.setTitle(namespace);
  }

  private void connect() throws REPLException
  {
    connection = null;
    int errorCount = 0;
    while ((errorCount < REPL_RETRY_COUNT) && (connection == null))
    {
      try
      {
        connection = new Connection("localhost", NREPL_PORT);
      }
      catch (ConnectException e)
      {
        if (e.getMessage().contains("Connection refused"))
        {
          errorCount++;
          try
          {
            //noinspection BusyWait
            Thread.sleep(REPL_RETRY_PAUSE);
          }
          catch (InterruptedException ignore)
          {
            Thread.currentThread().interrupt();
          }
        }
        else
        {
          throw new REPLException("Error connecting to REPL: " + e.getMessage(), e);
        }
      }
      catch (Exception e)
      {
        throw new REPLException("Error connecting to REPL: " + e.getMessage(), e);
      }
    }

    if (errorCount == REPL_RETRY_COUNT)
    {
      throw new REPLException("Couldn't establish connection to REPL");
    }
  }

  @Override
  public void stop()
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
  public void execute(String command)
  {
    setEditorEnabled(false);

    final Response response = connection.send(command);
    ApplicationManager.getApplication().runWriteAction(new Runnable()
    {
      public void run()
      {
        try
        {
          printResponse(response);
        }
        finally
        {
          setEditorEnabled(true);
        }
      }
    });
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

  private void printResponse(Response response)
  {
    Map<Keyword, Object> items = response.combinedResponse();

    List<Object> values = response.values();

    String namespace = (String) items.get(NAMESPACE_KEYWORD);
    String errorOutput = (String) items.get(ERROR_KEYWORD);
    String stdOutput = (String) items.get(OUTPUT_KEYWORD);

    ClojureConsole console = getConsoleView().getConsole();
    console.setTitle(namespace);

    if (errorOutput != null)
    {
      console.printToHistory(errorOutput, ConsoleViewContentType.ERROR_OUTPUT.getAttributes());
    }
    if (stdOutput != null)
    {
      console.printToHistory(stdOutput, NORMAL_TEXT);
    }

    for (Object value : values)
    {
      print("=> ", ConsoleViewContentType.USER_INPUT.getAttributes());
      printValue(value);
      print("\n", NORMAL_TEXT);
    }

    Editors.scrollDown(getConsoleView().getConsole().getHistoryViewer());
  }

  // Based on RT.print
  @SuppressWarnings("HardCodedStringLiteral")
  public void printValue(Object x)
  {
    // TODO add repl options for printing (*print-meta*, *print-dup*, *print-readably*)
    // TODO then add back meta code

    if (x == null)
    {
      print("nil", NORMAL_TEXT);
    }
    else if (x instanceof ISeq || x instanceof IPersistentList)
    {
      print("(", ClojureSyntaxHighlighter.PARENTS);
      printInnerSeq(RT.seq(x));
      print(")", ClojureSyntaxHighlighter.PARENTS);
    }
    else if (x instanceof String)
    {
      CharSequence charSequence = (CharSequence) x;
      StringBuilder buffer = new StringBuilder(charSequence.length() + 8);
      buffer.append('\"');
      for (int i = 0; i < charSequence.length(); i++)
      {
        char c = charSequence.charAt(i);
        switch (c)
        {
          case '\n':
            buffer.append("\\n");
            break;
          case '\t':
            buffer.append("\\t");
            break;
          case '\r':
            buffer.append("\\r");
            break;
          case '"':
            buffer.append("\\\"");
            break;
          case '\\':
            buffer.append("\\\\");
            break;
          case '\f':
            buffer.append("\\f");
            break;
          case '\b':
            buffer.append("\\b");
            break;
          default:
            buffer.append(c);
        }
      }
      buffer.append('\"');
      print(buffer.toString(), ClojureSyntaxHighlighter.STRING);
    }
    else if (x instanceof IPersistentMap)
    {
      print("{", ClojureSyntaxHighlighter.BRACES);
      for (ISeq seq = RT.seq(x); seq != null; seq = seq.next())
      {
        IMapEntry e = (IMapEntry) seq.first();
        printValue(e.key());
        print(" ", NORMAL_TEXT);
        printValue(e.val());
        if (seq.next() != null)
        {
          print(", ", NORMAL_TEXT);
        }
      }
      print("}", ClojureSyntaxHighlighter.BRACES);
    }
    else if (x instanceof IPersistentVector)
    {
      IPersistentVector vector = (IPersistentVector) x;
      print("[", ClojureSyntaxHighlighter.BRACES);
      for (int i = 0; i < vector.count(); i++)
      {
        printValue(vector.nth(i));
        if (i < vector.count() - 1)
        {
          print(" ", NORMAL_TEXT);
        }
      }
      print("]", ClojureSyntaxHighlighter.BRACES);
    }
    else if (x instanceof IPersistentSet)
    {
      print("#{", ClojureSyntaxHighlighter.BRACES);
      for (ISeq seq = RT.seq(x); seq != null; seq = seq.next())
      {
        printValue(seq.first());
        if (seq.next() != null)
        {
          print(" ", NORMAL_TEXT);
        }
      }
      print("}", ClojureSyntaxHighlighter.BRACES);
    }
    else if (x instanceof Character)
    {
      char c = ((Character) x).charValue();
      print("\\", ClojureSyntaxHighlighter.CHAR);
      switch (c)
      {
        case '\n':
          print("newline", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\t':
          print("tab", ClojureSyntaxHighlighter.CHAR);
          break;
        case ' ':
          print("space", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\b':
          print("backspace", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\f':
          print("formfeed", ClojureSyntaxHighlighter.CHAR);
          break;
        case '\r':
          print("return", ClojureSyntaxHighlighter.CHAR);
          break;
        default:
          print(Character.toString(c), ClojureSyntaxHighlighter.CHAR);
      }
    }
    else if (x instanceof Class)
    {
      print("#=", NORMAL_TEXT);
      print(((Class<?>) x).getName(), NORMAL_TEXT);
    }
    else if (x instanceof BigDecimal)
    {
      print(x.toString(), ClojureSyntaxHighlighter.NUMBER);
      print("M", ClojureSyntaxHighlighter.NUMBER);
    }
    else if (x instanceof Number)
    {
      print(x.toString(), ClojureSyntaxHighlighter.NUMBER);
    }
    else if (x instanceof Keyword)
    {
      print(x.toString(), ClojureSyntaxHighlighter.KEY);
    }
    else if (x instanceof Symbol)
    {
      print(x.toString(), ClojureSyntaxHighlighter.ATOM);
    }
    else if (x instanceof Var)
    {
      Var v = (Var) x;
      print("#=(var " + v.ns.name + '/' + v.sym + ')', NORMAL_TEXT);
    }
    else if (x instanceof Pattern)
    {
      Pattern p = (Pattern) x;
      print("#\"" + p.pattern() + '\"', NORMAL_TEXT);
    }
    else
    {
      print(x.toString(), NORMAL_TEXT);
    }
  }

  private void printInnerSeq(ISeq x)
  {
    for (ISeq seq = x; seq != null; seq = seq.next())
    {
      printValue(seq.first());
      if (seq.next() != null)
      {
        print(" ", NORMAL_TEXT);
      }
    }
  }

  private void print(String text, TextAttributes attributes)
  {
    ClojureConsole console = getConsoleView().getConsole();
    console.printToHistory(text, attributes);
  }

  private void print(String text, TextAttributesKey key)
  {
    ClojureConsole console = getConsoleView().getConsole();
    EditorColorsScheme clojureScheme = EditorColorsManager.getInstance().getGlobalScheme();
    console.printToHistory(text, clojureScheme.getAttributes(key));
  }

  @Override
  protected String getType()
  {
    return "nREPL";
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
    params.getProgramParametersList().addAll(Arrays.asList("--port", Integer.toString(NREPL_PORT)));

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
