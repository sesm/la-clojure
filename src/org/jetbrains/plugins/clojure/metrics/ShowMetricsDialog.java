/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.clojure.metrics;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

public class ShowMetricsDialog extends DialogWrapper {

  private static final ColumnInfo<Metrics.Timer.Snapshot, String> NAME = new SnapshotColumnInfo("name");
  private static final ColumnInfo<Metrics.Timer.Snapshot, String> COUNT = new SnapshotColumnInfo("count");
//  private static final ColumnInfo<Metrics.Timer.Snapshot, String> MIN = new SnapshotColumnInfo("min", TimeUnit.MILLISECONDS);
  private static final ColumnInfo<Metrics.Timer.Snapshot, String> MAX = new SnapshotColumnInfo("max", TimeUnit.MILLISECONDS);
  private static final ColumnInfo<Metrics.Timer.Snapshot, String> MEAN = new SnapshotColumnInfo("mean", TimeUnit.MILLISECONDS);
  private static final ColumnInfo<Metrics.Timer.Snapshot, String> STDDEV = new SnapshotColumnInfo("stdDev", TimeUnit.MILLISECONDS);

  private static final ColumnInfo[] COLUMNS = new ColumnInfo[]{NAME, COUNT, MAX, MEAN, STDDEV};

  private final Project project;

  public ShowMetricsDialog(Project project) {
    super(project, true);
    this.project = project;
    setTitle("Clojure Metrics");
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);
    init();
  }

  protected String getDimensionServiceKey() {
    return "#org.jetbrains.plugins.clojure.metrics.ShowMetricsDialog";
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[] {getCancelAction(), getHelpAction()};
  }

  protected JComponent createCenterPanel() {
    SortedMap<String,Metrics.Timer> timers = Metrics.getInstance(project).getTimers();
    List<Metrics.Timer.Snapshot> features = new ArrayList<Metrics.Timer.Snapshot>();
    for (Metrics.Timer timer : timers.values()) {
      features.add(timer.getSnapshot());
    }
    final TableView table = new TableView<Metrics.Timer.Snapshot>(new ListTableModel<Metrics.Timer.Snapshot>(COLUMNS, features, 0));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER);

    return panel;
  }

  private static class SnapshotComparator implements Comparator<Metrics.Timer.Snapshot> {
    private final Field field;

    private SnapshotComparator(Field field) {
      this.field = field;
    }

    @Override
    public int compare(Metrics.Timer.Snapshot o1, Metrics.Timer.Snapshot o2) {
      try {
        Comparable value1 = (Comparable) field.get(o1);
        Comparable value2 = (Comparable) field.get(o2);
        return value1.compareTo(value2);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class SnapshotColumnInfo extends ColumnInfo<Metrics.Timer.Snapshot, String> {
    private final Field field;
    private final double factor;
    private final Comparator<Metrics.Timer.Snapshot> comparator;

    private SnapshotColumnInfo(String name, double factor) {
      super(name);
      try {
        field = Metrics.Timer.Snapshot.class.getDeclaredField(name);
        comparator = new SnapshotComparator(field);
        this.factor = factor;
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }

    public SnapshotColumnInfo(String name) {
      this(name, -1.0);
    }

    public SnapshotColumnInfo(String name, TimeUnit timeUnit) {
      this(name, 1.0 / timeUnit.toNanos(1));
    }

    @Nullable
    @Override
    public String valueOf(Metrics.Timer.Snapshot snapshot) {
      try {
        Object value = field.get(snapshot);
        if (value instanceof Number && factor >= 0) {
          return String.format("%2.2f", factor * ((Number) value).doubleValue());
        }
        return value.toString();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Nullable
    @Override
    public Comparator<Metrics.Timer.Snapshot> getComparator() {
      return comparator;
    }
  }
}
