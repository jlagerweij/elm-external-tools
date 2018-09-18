package org.elm.tools.external.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import java.util.Arrays;
import java.util.Optional;

public class NotificationUtil {
    private static final NotificationGroup GROUP_DISPLAY_ID_BALLOON =
            new NotificationGroup("org.elmlang.intellijplugin.elmmake",
                    NotificationDisplayType.BALLOON, false);
    private static final String ELM_EXTERNAL_TOOLS = "Elm External Tools";

    public static void showNotification(String title, String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = GROUP_DISPLAY_ID_BALLOON.createNotification(ELM_EXTERNAL_TOOLS, title, message, NotificationType.ERROR);
            Optional<Project> projects = Arrays.stream(ProjectManager.getInstance().getOpenProjects()).findFirst();
            projects.ifPresent(project -> Notifications.Bus.notify(notification, project));
        });
    }
}
