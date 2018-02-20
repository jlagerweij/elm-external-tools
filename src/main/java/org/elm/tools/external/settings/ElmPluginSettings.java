package org.elm.tools.external.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

import org.jetbrains.annotations.Nullable;

@State(name = "ElmPluginSettings")
public class ElmPluginSettings implements PersistentStateComponent<ElmPluginSettings> {
    private String elmMakeExecutable = "";
    private String nodeExecutable = "";
    private boolean pluginEnabled;

    public static ElmPluginSettings getInstance(Project project) {
        return ServiceManager.getService(project, ElmPluginSettings.class);
    }

    @Nullable
    @Override
    public ElmPluginSettings getState() {
        return this;
    }

    @Override
    public void loadState(ElmPluginSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getElmMakeExecutable() {
        return elmMakeExecutable;
    }

    public String getNodeExecutable() {
        return nodeExecutable;
    }

    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    public void save(final boolean pluginEnabled, final String elmMakePath, final String nodePath) {
        this.pluginEnabled = pluginEnabled;
        this.elmMakeExecutable = elmMakePath;
        this.nodeExecutable = nodePath;
    }
}
