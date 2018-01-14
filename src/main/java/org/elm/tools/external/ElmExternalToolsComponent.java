package org.elm.tools.external;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import org.jetbrains.annotations.NotNull;


public class ElmExternalToolsComponent {
    private static final String COMPONENT_NAME = "Elm External Tools Component";

    public void initComponent() {
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        MessageBusConnection connection = bus.connect();
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new ElmExternalToolsFileDocumentManager());
    }

    public void disposeComponent() {
    }

    @NotNull
    public String getComponentName() {
        return COMPONENT_NAME;
    }
}
