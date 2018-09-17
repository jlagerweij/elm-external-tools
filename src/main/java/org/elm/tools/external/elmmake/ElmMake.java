package org.elm.tools.external.elmmake;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import org.elm.tools.external.ElmExternalToolsComponent;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ElmMake {
    private static final Logger LOG = Logger.getInstance(ElmExternalToolsComponent.class);

    private static final NotificationGroup GROUP_DISPLAY_ID_BALLOON =
            new NotificationGroup("org.elmlang.intellijplugin.elmmake",
                    NotificationDisplayType.BALLOON, false);
    private static final String ELM_EXTERNAL_TOOLS = "Elm External Tools";

    private ElmMake() {
        // Private constructor to hide the implicit public one
    }

    public static List<Problems> execute(String workDirectory, String nodePath, String elmMakeExePath, String file) {
        GeneralCommandLine commandLine = createGeneralCommandLine(nodePath, elmMakeExePath);
        commandLine.setWorkDirectory(workDirectory);
        commandLine.addParameter("make");
        commandLine.addParameter("--report=json");
        commandLine.addParameter("--output=/dev/null");
        commandLine.addParameter(file);

        try {
            LOG.info(workDirectory + ": " + commandLine.getCommandLineString(elmMakeExePath));

            Process process = commandLine.createProcess();

            process.waitFor();
            LOG.debug("Elm exit value: " + process.exitValue());
            return parseElmMakeOutput(process.getInputStream(), process.getErrorStream());

        } catch (ExecutionException | InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    private static List<Problems> parseElmMakeOutput(InputStream inputStream, InputStream errorStream) {
        List<Problems> allProblems = new ArrayList<>();
        String output = null;
        try {
            List<String> lines = readLines(new InputStreamReader(inputStream));
            if (lines.isEmpty()) {
                List<String> errorLines = readLines(new InputStreamReader(errorStream));
                if (notValidJson(errorLines)) {
                    String errorResult = String.join("\n", errorLines);
                    if (!errorResult.isEmpty()) {
                        showNotification("Problem found performing Elm make", errorResult);
                    }
                } else {
                    for (String line : errorLines) {
                        List<Problems> makeResult = parseCompileErrors(line);
                        allProblems.addAll(makeResult);
                    }
                }

            }
            for (String line : lines) {
                if (notValidJsonArray(line)) {
                    continue;
                }
                output = line;
                List<Problems> makeResult = parseProblemJsonElm018(output);
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

    private static List<String> readLines(Reader r) throws IOException {
        List<String> result = new ArrayList<>();
        BufferedReader lineReader = new BufferedReader(r);

        String line;
        while ((line = lineReader.readLine()) != null) {
            result.add(line);
        }

        return result;
    }

    private static void showNotification(String title, String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Notification notification = GROUP_DISPLAY_ID_BALLOON.createNotification(ELM_EXTERNAL_TOOLS, title, message, NotificationType.ERROR);
            Optional<Project> projects = Arrays.stream(ProjectManager.getInstance().getOpenProjects()).findFirst();
            projects.ifPresent(project -> Notifications.Bus.notify(notification, project));
        });
    }

    private static List<Problems> parseCompileErrors(final String line) {
        List<Problems> problems = new ArrayList<>();

        final CompileErrors compileErrors = new Gson().fromJson(line, CompileErrors.class);
        if (compileErrors.errors != null) {
            for (final CompileError error : compileErrors.errors) {
                for (CompileProblem compileProblem : error.problems) {
                    String detailsMessage = messagesToString(compileProblem.message);

                    final Problems problem = new Problems();
                    problem.file = error.path;
                    problem.type = compileErrors.type;
                    problem.region = compileProblem.region;
                    problem.overview = compileProblem.title;
                    problem.details = detailsMessage;
                    problems.add(problem);
                }
            }
        }
        if ("error".equals(compileErrors.type) && compileErrors.message != null) {
            showNotification(compileErrors.title, messagesToString(compileErrors.message));
        }
        return problems;
    }

    @NotNull
    private static String messagesToString(final List<Object> messages) {
        StringBuilder details = new StringBuilder();
        for (Object message : messages) {
            if (message instanceof String) {
                details.append(message);
            } else if (message instanceof Map) {
                final Map messageMap = (Map) message;
                final boolean bold = Boolean.TRUE.equals(messageMap.get("bold"));
                final boolean underline = Boolean.TRUE.equals(messageMap.get("underline"));
                final String color = (String) messageMap.get("color");
                final String string = (String) messageMap.get("string");

                details.append(createStyledMessage(bold, underline, color, string));
            }
        }
        return details.toString();
    }

    @NotNull
    private static String createStyledMessage(final boolean bold, final boolean underline, final String color, final String string) {
        String style = "<font ";
        if (color != null) {
            style += "color=\"" + color + "\" style=\"background-color:black; ";
        } else {
            style += "style=\"";
        }
        if (bold) {
            style += "font-weight:bolder; ";
        }
        if (underline) {
            style += "text-decoration: underline; ";
        }
        style += "\">";
        style += string;
        style += "</font>";
        return style;
    }

    private static List<Problems> parseProblemJsonElm018(String output) {
        return new Gson().fromJson(output, new TypeToken<List<Problems>>() {
        }.getType());
    }

    private static boolean notValidJsonArray(String line) {
        return !line.startsWith("[");
    }

    private static boolean notValidJson(List<String> errorLines) {
        return !(!errorLines.isEmpty() && errorLines.get(0).startsWith("{"));
    }

    public static String getVersion(final String nodePath, String elmMakeExePath) throws ExecutionException, InterruptedException, IOException {
        GeneralCommandLine commandLine = createGeneralCommandLine(nodePath, elmMakeExePath);
        commandLine.addParameter("--version");
        try {
            Process process = commandLine.createProcess();
            process.waitFor();
            if (process.exitValue() == 0) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                return bufferedReader.readLine();
            } else {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                throw new IllegalStateException(bufferedReader.readLine());
            }
        } catch (ProcessNotCreatedException e) {
            return "-";
        }
    }

    public static boolean fileStartsWithShebang(final String elmMakeExePath) {
        if (elmMakeExePath.isEmpty() || !new File(elmMakeExePath).exists()) {
            return false;
        }
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(elmMakeExePath)))) {
            final String firstLine = bufferedReader.readLine();
            return firstLine.startsWith("#!");
        } catch (IOException e) {
            // Ignored
        }
        return false;
    }

    @NotNull
    private static GeneralCommandLine createGeneralCommandLine(final String nodePath, String elmMakeExePath) {
        GeneralCommandLine commandLine = new GeneralCommandLine();

        File nodeExePath = new File(nodePath);
        if (fileStartsWithShebang(elmMakeExePath) && nodeExePath.exists()) {
            commandLine.setExePath(nodeExePath.getAbsolutePath());
            commandLine.addParameter(elmMakeExePath);
        } else {
            commandLine.setExePath(elmMakeExePath);
        }
        return commandLine;
    }

}
