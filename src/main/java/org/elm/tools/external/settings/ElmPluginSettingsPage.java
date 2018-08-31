package org.elm.tools.external.settings;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import org.elm.tools.external.ElmExternalToolsComponent;
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
    private static final Logger LOG = Logger.getInstance(ElmExternalToolsComponent.class);
    private final Project project;

    private JCheckBox pluginEnabledCheckbox;
    private JPanel panel;
    private JLabel versionLabel;
    private JLabel elmExeLabel;
    private TextFieldWithHistoryWithBrowseButton elmExeField;
    private TextFieldWithHistoryWithBrowseButton nodeExeField;
    private JLabel nodeExeLabel;

    public ElmPluginSettingsPage(@NotNull final Project project) {
        this.project = project;
        configElmMakeBinField();
        configNodeExeField();
    }

    private void addListeners() {
        pluginEnabledCheckbox.addItemListener(e -> {
            boolean enabled = e.getStateChange() == ItemEvent.SELECTED;
            setEnabledState(enabled);
            updateLaterInEDT();
        });
        DocumentAdapter docAdp = new DocumentAdapter() {
            protected void textChanged(DocumentEvent e) {
                updateLaterInEDT();
            }
        };
        elmExeField.getChildComponent().getTextEditor().getDocument().addDocumentListener(docAdp);
        nodeExeField.getChildComponent().getTextEditor().getDocument().addDocumentListener(docAdp);
    }

    private void updateLaterInEDT() {
        UIUtil.invokeLaterIfNeeded(ElmPluginSettingsPage.this::update);
    }

    private void update() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        validate();
    }

    private void setEnabledState(boolean enabled) {
        elmExeField.setEnabled(enabled);
        elmExeLabel.setEnabled(enabled);
        nodeExeField.setEnabled(enabled);
        nodeExeLabel.setEnabled(enabled);
    }

    private void validate() {
        updateVersion();
    }

    private void updateVersion() {
        final String elmMakePath = elmExeField.getChildComponent().getText();
        final String nodePath = nodeExeField.getChildComponent().getText();
        if (ElmMake.fileStartsWithShebang(elmMakePath)) {
            nodeExeLabel.setVisible(true);
            nodeExeField.setVisible(true);
        } else {
            nodeExeLabel.setVisible(false);
            nodeExeField.setVisible(false);
        }
        getVersion(nodePath, elmMakePath);
    }

    private void getVersion(String nodePath, String elmExePath) {
        if (!pluginEnabledCheckbox.isSelected()) {
            versionLabel.setForeground(new DefaultColorsScheme().getDefaultForeground());
            versionLabel.setText("Disabled");
            return;
        }
        if (ElmMake.fileStartsWithShebang(elmExePath)) {
            if (StringUtils.isEmpty(nodePath)) {
                return;
            }
            if (!new File(nodePath).exists()) {
                showError("Node not found: " + nodePath);
                return;
            }
        }
        if (StringUtils.isEmpty(elmExePath)) {
            return;
        }
        if (!new File(elmExePath).exists()) {
            showError("Elm executable not found: " + elmExePath);
            return;
        }
        try {
            String version = ElmMake.getVersion(nodePath, elmExePath);
            versionLabel.setForeground(new DefaultColorsScheme().getDefaultForeground());
            versionLabel.setText("Version: " + version);
        } catch (Exception e) {
            final String message = e.getMessage();
            showError(message);
            LOG.error("Unable to perform getVersion()", e);
        }
    }

    private void showError(final String message) {
        versionLabel.setForeground(JBColor.RED);
        versionLabel.setText("Error: " + message);
    }

    private void configElmMakeBinField() {
        TextFieldWithHistory elmMakeExeTextFieldWithHistory = elmExeField.getChildComponent();
        elmMakeExeTextFieldWithHistory.setHistorySize(-1);
        elmMakeExeTextFieldWithHistory.setMinimumAndPreferredWidth(0);

        List<String> allElmMakeExeInPath = PathEnvironmentVariableUtil.findAllExeFilesInPath("elm")
                                                                      .stream()
                                                                      .map(File::getAbsolutePath)
                                                                      .distinct()
                                                                      .collect(Collectors.toList());

        SwingHelper.addHistoryOnExpansion(elmMakeExeTextFieldWithHistory, () -> allElmMakeExeInPath);

        SwingHelper.installFileCompletionAndBrowseDialog(project, elmExeField, "Select Elm-Make Executable", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    }

    private void configNodeExeField() {
        TextFieldWithHistory nodeTextFieldWithHistory = nodeExeField.getChildComponent();
        nodeTextFieldWithHistory.setHistorySize(-1);
        nodeTextFieldWithHistory.setMinimumAndPreferredWidth(0);

        List<String> allNodeInPath = PathEnvironmentVariableUtil.findAllExeFilesInPath("node")
                                                                .stream()
                                                                .map(File::getAbsolutePath)
                                                                .distinct()
                                                                .collect(Collectors.toList());

        SwingHelper.addHistoryOnExpansion(nodeTextFieldWithHistory, () -> allNodeInPath);

        SwingHelper.installFileCompletionAndBrowseDialog(project, nodeExeField, "Select Node Executable", FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
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
        final String elmMakePath = elmExeField.getChildComponent().getText();
        final String nodePath = nodeExeField.getChildComponent().getText();
        getVersion(nodePath, elmMakePath);
        addListeners();
        return panel;
    }

    @Override
    public boolean isModified() {
        return pluginEnabledCheckbox.isSelected() != getSettings().isPluginEnabled()
                || !elmExeField.getChildComponent().getText().equals(getSettings().getElmMakeExecutable())
                || !nodeExeField.getChildComponent().getText().equals(getSettings().getNodeExecutable());
    }

    @Override
    public void apply() throws ConfigurationException {
        saveSettings();
        PsiManager.getInstance(project).dropResolveCaches();
    }

    private void saveSettings() {
        ElmPluginSettings settings = getSettings();
        settings.save(pluginEnabledCheckbox.isSelected(), elmExeField.getChildComponent().getText(), nodeExeField.getChildComponent().getText());
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    private void loadSettings() {
        ElmPluginSettings settings = getSettings();
        pluginEnabledCheckbox.setSelected(settings.isPluginEnabled());
        elmExeField.getChildComponent().setText(settings.getElmMakeExecutable());
        nodeExeField.getChildComponent().setText(settings.getNodeExecutable());

        setEnabledState(settings.isPluginEnabled());
        updateVersion();
    }

    @Override
    public void reset() {
        loadSettings();
    }

    private ElmPluginSettings getSettings() {
        return ElmPluginSettings.getInstance(project);
    }
}
