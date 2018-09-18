package org.elm.tools.external.elmmake.parser;

import com.google.gson.Gson;

import org.elm.tools.external.elmmake.model.CompileError;
import org.elm.tools.external.elmmake.model.CompileErrors;
import org.elm.tools.external.elmmake.model.CompileProblem;
import org.elm.tools.external.elmmake.model.Problems;
import org.elm.tools.external.utils.NotificationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Elm019Parser {

    public static List<Problems> parseCompileErrors(final String line) {
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
            NotificationUtil.showNotification(compileErrors.title, messagesToString(compileErrors.message));
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

}
