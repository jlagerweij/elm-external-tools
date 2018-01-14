package org.elm.tools.external.elmmake;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;

import org.elm.tools.external.ElmExternalToolsComponent;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ElmMake {
    private static final Logger LOG = Logger.getInstance(ElmExternalToolsComponent.class);

    public static List<Problems> execute(Editor editor, String workDirectory, String elmMakeExePath, String file) {
        GeneralCommandLine commandLine = createGeneralCommandLine(elmMakeExePath);
        commandLine.setWorkDirectory(workDirectory);
        commandLine.addParameter("--report=json");
        commandLine.addParameter("--output=/dev/null");
        commandLine.addParameter("--yes");
        commandLine.addParameter("--warn");
        commandLine.addParameter(file);

        try {
            LOG.info(commandLine.getCommandLineString(elmMakeExePath));

            Process process = commandLine.createProcess();

            process.waitFor();
            LOG.debug("elm-make exit value: " + process.exitValue());
            if (process.exitValue() == 1) {
                return parseElmMakeOutput(editor, process.getInputStream(), process.getErrorStream());
            }

        } catch (ExecutionException | InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    public static List<Problems> parseElmMakeOutput(Editor editor, InputStream inputStream, InputStream errorStream) {
        List<Problems> allProblems = new ArrayList<>();
        String output = null;
        try {
            List<String> lines = CharStreams.readLines(new InputStreamReader(inputStream));
            if (lines.isEmpty()) {
                String errorResult = new BufferedReader(new InputStreamReader(errorStream))
                        .lines().collect(Collectors.joining("\n"));
                if (!errorResult.isEmpty()) {
                    String groupDisplayId = "org.elmlang.intellijplugin.elmmake";
                    String title = "Problem found performing Elm make";
                    Notification notification = new Notification(groupDisplayId, title, errorResult, NotificationType.ERROR, null);
                    Notifications.Bus.notify(notification);
                }
            }
            for (String line : lines) {
                if (notValidJsonArray(line)) {
                    continue;
                }
                output = line;
                List<Problems> makeResult = parseProblemJson(output);
                allProblems.addAll(makeResult);
            }
        } catch (JsonSyntaxException e) {
            LOG.error(e.getMessage(), e);
            LOG.error("Could not convert to JSON: " + output);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }

        return allProblems;
    }

    private static List<Problems> parseProblemJson(String output) {
        return new Gson().fromJson(output, new TypeToken<List<Problems>>() {
        }.getType());
    }

    private static boolean notValidJsonArray(String line) {
        return !line.startsWith("[");
    }

    public static String getVersion(String elmMakeExePath) throws Exception {
        GeneralCommandLine commandLine = createGeneralCommandLine(elmMakeExePath);
        commandLine.addParameter("--help");
        Process process = commandLine.createProcess();
        process.waitFor();
        if (process.exitValue() == 0) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return bufferedReader.readLine();
        } else {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            throw new IllegalStateException(bufferedReader.readLine());
        }
    }

    @NotNull
    private static GeneralCommandLine createGeneralCommandLine(String elmMakeExePath) {
        GeneralCommandLine commandLine = new GeneralCommandLine();

        File directory = new File(elmMakeExePath).getParentFile();
        File nodeExePath = new File(directory, "node");
        if (nodeExePath.exists()) {
            commandLine.setExePath(nodeExePath.getAbsolutePath());
            commandLine.addParameter(elmMakeExePath);
        } else {
            commandLine.setExePath(elmMakeExePath);
        }
        return commandLine;
    }

}
