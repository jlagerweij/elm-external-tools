package org.elm.tools.external.elmmake;

import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessNotCreatedException;
import com.intellij.openapi.diagnostic.Logger;

import org.elm.tools.external.ElmExternalToolsComponent;
import org.elm.tools.external.elmmake.model.Problems;
import org.elm.tools.external.elmmake.parser.Elm018Parser;
import org.elm.tools.external.elmmake.parser.Elm019Parser;
import org.elm.tools.external.utils.NotificationUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class ElmMake {
    private static final Logger LOG = Logger.getInstance(ElmExternalToolsComponent.class);

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
                        NotificationUtil.showNotification("Problem found performing Elm make", errorResult);
                    }
                } else {
                    for (String line : errorLines) {
                        List<Problems> makeResult = Elm019Parser.parseCompileErrors(line);
                        allProblems.addAll(makeResult);
                    }
                }

            }
            for (String line : lines) {
                if (notValidJsonArray(line)) {
                    continue;
                }
                output = line;
                List<Problems> makeResult = Elm018Parser.parseProblemJsonElm018(output);
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
