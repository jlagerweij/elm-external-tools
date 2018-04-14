package org.elm.tools.external.annotator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import org.elm.tools.external.elmmake.ElmMake;
import org.elm.tools.external.elmmake.Problems;
import org.elm.tools.external.elmmake.Region;
import org.elm.tools.external.settings.ElmPluginSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ElmMakeExternalAnnotator extends ExternalAnnotator<AnnotatorFile, List<Problems>> {
    private static final String TAB = "    ";

    @Nullable
    @Override
    public AnnotatorFile collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        AnnotatorFile annotatorFile = collectInformation(file);
        annotatorFile.setEditor(editor);
        return annotatorFile;
    }

    /** Called first; in our case, just return file and do nothing */
    @Override
    @NotNull
    public AnnotatorFile collectInformation(@NotNull PsiFile file) {
        return new AnnotatorFile(file);
    }

    /** Called 2nd; look for trouble in file and return list of issues.
     *
     *  For most custom languages, you would not reimplement your semantic
     *  analyzer using PSI trees. Instead, here is where you would call out to
     *  your custom languages compiler or interpreter to get error messages
     *  or other bits of information you'd like to annotate the document with.
     */
    @Nullable
    @Override
    public List<Problems> doAnnotate(final AnnotatorFile annotatorFile) {
        PsiFile file = annotatorFile.getFile();

        ElmPluginSettings elmPluginSettings = ElmPluginSettings.getInstance(file.getProject());

        if (!elmPluginSettings.isPluginEnabled()) {
            return Collections.emptyList();
        }
        if (!isValidPsiFile(file)) {
            return Collections.emptyList();
        }

        final Optional<String> basePath = findElmPackageDirectory(file);

        if (!basePath.isPresent()) {
            return Collections.emptyList();
        }

        String canonicalPath = file.getVirtualFile().getCanonicalPath();

        List<Problems> problems = ElmMake.execute(basePath.get(), elmPluginSettings.getNodeExecutable(), elmPluginSettings.getElmMakeExecutable(), canonicalPath);

        return problems
                .stream()
                .filter(res -> isIssueForCurrentFile(basePath.get(), canonicalPath, res))
                .collect(Collectors.toList());
    }

    private Optional<String> findElmPackageDirectory(PsiFile file) {
        final PsiDirectory[] parent = new PsiDirectory[1];
        ApplicationManager.getApplication().runReadAction(() -> {
            parent[0] = file.getParent();
            while (parent[0] != null && parent[0].isValid() && parent[0].findFile("elm-package.json") == null) {
                parent[0] = parent[0].getParent();
            }

        });

        if (parent[0] == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(parent[0].getVirtualFile().getCanonicalPath());
        }
    }

    private boolean isIssueForCurrentFile(String basePath, String canonicalPath, Problems res) {
        return res.file.replace("./", basePath + "/").equals(canonicalPath);
    }

    /** Called 3rd to actually annotate the editor window */
    @Override
    public void apply(@NotNull PsiFile file,
                      List<Problems> issues,
                      @NotNull AnnotationHolder holder) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);

        for (Problems issue : issues) {
            annotateForIssue(holder, document, issue, file);
        }
    }

    private void annotateForIssue(@NotNull AnnotationHolder holder, Document document, Problems issue, PsiFile file) {
        Optional<TextRange> optionalSelector = findAnnotationLocation(document, issue);
        if (!optionalSelector.isPresent()) {
            return;
        }
        final TextRange selector = optionalSelector.get();

        Annotation annotation;
        if (issue.type.equals("warning")) {
            if (issue.tag.equals("unused import")) {
                holder.createWeakWarningAnnotation(selector, null);
                annotation = holder.createWeakWarningAnnotation(selector, issue.overview);
                annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            } else {
                annotation = holder.createWarningAnnotation(selector, issue.overview);
            }
        } else {
            annotation = holder.createErrorAnnotation(selector, issue.overview);

            WolfTheProblemSolver theProblemSolver = WolfTheProblemSolver.getInstance(file.getProject());
            final Problem problem = theProblemSolver.convertToProblem(file.getVirtualFile(), issue.region.start.line, issue.region.start.column, new String[]{ issue.details });
            theProblemSolver.weHaveGotNonIgnorableProblems(file.getVirtualFile(), Collections.singletonList(problem));
        }

        String tooltip = createToolTip(issue);
        annotation.setTooltip(tooltip);
    }

    @NotNull
    private Optional<TextRange> findAnnotationLocation(Document document, Problems issue) {
        Region region = issue.subregion != null ? issue.subregion : issue.region;

        int offsetStart = StringUtil.lineColToOffset(document.getText(), region.start.line - 1, region.start.column - 1);
        int offsetEnd = StringUtil.lineColToOffset(document.getText(), region.end.line - 1, region.end.column - 1);

        if (offsetStart == -1 && offsetEnd == -1) {
            return Optional.empty();
        }

        if (isMultiLineRegion(region)) {
            offsetEnd = document.getLineEndOffset(region.start.line - 1);
        }
        return Optional.of(new TextRange(offsetStart, offsetEnd));
    }

    private boolean isMultiLineRegion(Region region) {
        return region.start.line != region.end.line;
    }

    private boolean isValidPsiFile(PsiFile file) {
        return file != null && file.getVirtualFile() != null && file.getVirtualFile().isValid();
    }

    @NotNull
    private String createToolTip(Problems issue) {
        String previousLine = "";
        StringBuilder tooltip = new StringBuilder("<html><strong>" + issue.overview + "</strong><br/><hr/>");
        String[] lines = issue.details.split("\\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if (!previousLine.startsWith(TAB) && line.startsWith(TAB)) {
                tooltip.append("<pre style=\"font-weight:bold;\">");
            } else if (previousLine.startsWith(TAB) && !line.startsWith(TAB)) {
                tooltip.append("</pre>");
            }
            if (line.startsWith(TAB)) {
                tooltip.append(line).append("\n");
            } else {
                tooltip.append(line).append("<br/>");
            }
            previousLine = line;
        }
        tooltip.append("</html>");
        return tooltip.toString();
    }

}

