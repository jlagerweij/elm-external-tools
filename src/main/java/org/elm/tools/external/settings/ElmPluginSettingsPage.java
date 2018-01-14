package org.elm.tools.external.settings;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;

import org.apache.commons.lang.StringUtils;
import org.elm.tools.external.elmmake.ElmMake;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

import java.awt.event.ItemEvent;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class ElmPluginSettingsPage implements Configurable {
    private final Project project;

    private JCheckBox pluginEnabledCheckbox;
    private JPanel panel;
    private JLabel versionLabel;
    private JLabel elmMakeExeLabel;
    private TextFieldWithHistoryWithBrowseButton elmMakeExeField;

    public ElmPluginSettingsPage(@NotNull final Project project) {
        this.project = project;
        configElmMakeBinField();
    }

    private void addListeners() {
        pluginEnabledCheckbox.addItemListener(e -> {
            boolean enabled = e.getStateChange() == ItemEvent.SELECTED;
            setEnabledState(enabled);
        });
        DocumentAdapter docAdp = new DocumentAdapter() {
            protected void textChanged(DocumentEvent e) {
                updateLaterInEDT();
            }
        };
        elmMakeExeField.getChildComponent().getTextEditor().getDocument().addDocumentListener(docAdp);
    }

    private void updateLaterInEDT() {
        UIUtil.invokeLaterIfNeeded(ElmPluginSettingsPage.this::update);
    }

    private void update() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        validate();
    }

    private void setEnabledState(boolean enabled) {
        elmMakeExeField.setEnabled(enabled);
        elmMakeExeLabel.setEnabled(enabled);
    }

    private void validate() {
        updateVersion();
    }

    private void updateVersion() {
        getVersion(elmMakeExeField.getChildComponent().getText(), project.getBasePath());
    }

    private void getVersion(String elmMakeExePath, String cwd) {
        if (StringUtils.isEmpty(elmMakeExePath)) {
            return;
        }
        try {
            String version = ElmMake.getVersion(elmMakeExePath);
            versionLabel.setForeground(new DefaultColorsScheme().getDefaultForeground());
            versionLabel.setText("Version: " + version);
        } catch (Exception e) {
            versionLabel.setForeground(JBColor.RED);
            versionLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void configElmMakeBinField() {
        TextFieldWithHistory textFieldWithHistory = elmMakeExeField.getChildComponent();
        textFieldWithHistory.setHistorySize(-1);
        textFieldWithHistory.setMinimumAndPreferredWidth(0);

        List<String> allExeFilesInPath = PathEnvironmentVariableUtil.findAllExeFilesInPath("elm-make")
                                                                    .stream()
                                                                    .map(File::getAbsolutePath)
                                                                    .distinct()
                                                                    .collect(Collectors.toList());

        SwingHelper.addHistoryOnExpansion(textFieldWithHistory, () -> allExeFilesInPath);

        SwingHelper.installFileCompletionAndBrowseDialog(project, elmMakeExeField, "Select Elm-Make Exe", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Elm External Tools";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        loadSettings();
        getVersion(elmMakeExeField.getChildComponent().getText(), project.getBasePath());
        addListeners();
        return panel;
    }

    @Override
    public boolean isModified() {
        return pluginEnabledCheckbox.isSelected() != getSettings().pluginEnabled
                || !elmMakeExeField.getChildComponent().getText().equals(getSettings().elmMakeExecutable);
    }

    @Override
    public void apply() throws ConfigurationException {
        saveSettings();
        PsiManager.getInstance(project).dropResolveCaches();
    }

    private void saveSettings() {
        ElmPluginSettings settings = getSettings();
        settings.pluginEnabled = pluginEnabledCheckbox.isSelected();
        settings.elmMakeExecutable = elmMakeExeField.getChildComponent().getText();
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    private void loadSettings() {
        ElmPluginSettings settings = getSettings();
        pluginEnabledCheckbox.setSelected(settings.pluginEnabled);
        elmMakeExeField.getChildComponent().setText(settings.elmMakeExecutable);

        setEnabledState(settings.pluginEnabled);
    }

    @Override
    public void reset() {
        loadSettings();
    }

    @Override
    public void disposeUIResources() {
    }

    private ElmPluginSettings getSettings() {
        return ElmPluginSettings.getInstance(project);
    }

    private void createUIComponents() {
    }
}
