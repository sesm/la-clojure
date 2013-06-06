package org.jetbrains.plugins.clojure.metrics;

import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author Colin Fleming
 */
public class Metrics {
  public static final String ACTION_ID = ":org.jetbrains.plugins.clojure.metrics/show-dialog";
  private final SortedMap<String, Timer> timers = new TreeMap<String, Timer>();

  public static Metrics getInstance(Project project) {
    return ServiceManager.getService(project, Metrics.class);
  }

  public Metrics() {
    boolean isInternal = Boolean.valueOf(System.getProperty(IdeaApplication.IDEA_IS_INTERNAL_PROPERTY)).booleanValue();
    if (isInternal) {
      ActionManager manager = ActionManager.getInstance();
      if (manager.getAction(ACTION_ID) == null) {
        ShowMetricsAction action = new ShowMetricsAction();
        manager.registerAction(ACTION_ID, action);
        DefaultActionGroup helpMenu = (DefaultActionGroup) manager.getAction("HelpMenu");
        if (helpMenu != null) {
          helpMenu.add(action);
        }
      }
    }
  }

  public synchronized Timer.Instance start(String name) {
    Timer timer = timers.get(name);
    if (timer == null) {
      timer = new Timer(name);
      timers.put(name, timer);
    }
    return timer.start();
  }

  public synchronized SortedMap<String, Timer> getTimers() {
    return Collections.unmodifiableSortedMap(timers);
  }

  public synchronized void reset() {
    timers.clear();
  }

  public class ShowMetricsAction extends AnAction implements DumbAware {
    public ShowMetricsAction() {
      super("Show Clojure metrics");
    }

    public void actionPerformed(AnActionEvent e) {
      new ShowMetricsDialog(getEventProject(e)).show();
    }

    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(getEventProject(e) != null);
    }
  }

  /**
   * @see http://www.johndcook.com/standard_deviation.html
   */
  public static class Timer {
    final String name;
    long count = 0;
    long min = 0;
    long max = 0;
    double oldM = 0.0;
    double oldS = 0.0;
    double newM = 0.0;
    double newS = 0.0;

    public Timer(String name) {
      this.name = name;
    }

    public synchronized void update(long elapsed) {
      count++;
      if (count == 1) {
        oldM = elapsed;
        newM = elapsed;
        oldS = 0.0;
      } else {
        newM = oldM + (elapsed - oldM) / count;
        newS = oldS + (elapsed - oldM) * (elapsed - newM);

        oldM = newM;
        oldS = newS;
      }
      min = Math.min(min, elapsed);
      max = Math.max(max, elapsed);
    }

    public synchronized Snapshot getSnapshot() {
      return new Snapshot(name, count, min, max, newM,
          (count > 1 ? Math.sqrt(newS / (count - 1)) : 0.0));
    }

    public Instance start() {
      return new Instance();
    }

    public class Instance {
      final long start;

      public Instance() {
        this.start = System.nanoTime();
      }

      public void stop() {
        update(System.nanoTime() - start);
      }
    }

    public static class Snapshot {
      public final String name;
      public final long count;
      public final long min;
      public final long max;
      public final double mean;
      public final double stdDev;

      public Snapshot(String name, long count, long min, long max, double mean, double stdDev) {
        this.name = name;
        this.count = count;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.stdDev = stdDev;
      }
    }
  }
}
