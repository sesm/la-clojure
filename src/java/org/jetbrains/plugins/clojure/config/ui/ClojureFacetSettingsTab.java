/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.clojure.config.ui;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.clojure.ClojureBundle;
import org.jetbrains.plugins.clojure.config.ClojureModuleSettings;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author ilyas
 */
public class ClojureFacetSettingsTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.clojure.config.ui.ClojureFacetTab");

  private Module myModule;
  private JPanel myPanel;
  private JTextField myJvmOpts;
  private JTextField myReplOpts;
  private JTextField myReplClass;
  private JPanel myReplPanel;
  private JCheckBox myUseNREPLCheckBox;
  private JTextField myHostTextField;
  private JTextField myPortTextField;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;
  private final ClojureModuleSettings mySettings;

  public ClojureFacetSettingsTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager, ClojureModuleSettings settings) {
    myModule = editorContext.getModule();
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;

    mySettings = settings;

    myJvmOpts.setText(mySettings.myJvmOpts);
    myReplClass.setText(mySettings.myReplClass);
    myReplOpts.setText(mySettings.myReplOpts);

    boolean runNrepl = mySettings.myRunNrepl;
    myUseNREPLCheckBox.setSelected(runNrepl);
    myHostTextField.setEnabled(runNrepl);
    myPortTextField.setEnabled(runNrepl);
    myHostTextField.setText(mySettings.myNreplHost);
    myPortTextField.setText(mySettings.myReplPort);

    myUseNREPLCheckBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        final boolean selected = myUseNREPLCheckBox.isSelected();
        myHostTextField.setEnabled(selected);
        myPortTextField.setEnabled(selected);
      }
    });

    reset();
  }

  @Nls
  public String getDisplayName() {
    return ClojureBundle.message("clojure.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !myJvmOpts.getText().trim().equals(mySettings.myJvmOpts) ||
        !myReplClass.getText().trim().equals(mySettings.myReplClass) ||
        !myReplOpts.getText().trim().equals(mySettings.myReplOpts) ||
        !myHostTextField.getText().trim().equals(mySettings.myNreplHost) ||
        !myPortTextField.getText().trim().equals(mySettings.myReplPort) ||
        myUseNREPLCheckBox.isSelected() != mySettings.myRunNrepl;
  }

  @Override
  public String getHelpTopic() {
    return super.getHelpTopic();
  }

  public void onFacetInitialized(@NotNull Facet facet) {
  }

  public void apply() throws ConfigurationException {
    mySettings.myJvmOpts = myJvmOpts.getText().trim();
    mySettings.myReplClass = myReplClass.getText().trim();
    mySettings.myReplOpts = myReplOpts.getText().trim();
    mySettings.myNreplHost = myHostTextField.getText().trim();
    mySettings.myReplPort = myPortTextField.getText().trim();
    mySettings.myRunNrepl = myUseNREPLCheckBox.isSelected();
  }

  public void reset() {
    myJvmOpts.setText(mySettings.myJvmOpts);
    myReplClass.setText(mySettings.myReplClass);
    myReplClass.setText(mySettings.myReplClass);
    myPortTextField.setText(mySettings.myReplPort);
    myHostTextField.setText(mySettings.myNreplHost);
    myUseNREPLCheckBox.setSelected(mySettings.myRunNrepl);
  }

  public void disposeUIResources() {
  }

  private void createUIComponents() {
  }


}
