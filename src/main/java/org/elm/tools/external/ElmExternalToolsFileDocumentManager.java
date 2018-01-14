package org.elm.tools.external;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import org.jetbrains.annotations.NotNull;

import static org.elm.tools.external.utils.PsiFiles.isPsiFileInProject;

public class ElmExternalToolsFileDocumentManager extends FileDocumentManagerAdapter {
    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        super.beforeDocumentSaving(document);
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (isPsiFileEligible(project, psiFile)) {
                processPsiFile(project, psiFile);
            }
        }
    }

    /**
     * The psi files seems to be shared between projects, so we need to check if the file is physically
     * in that project before reformating, or else the file is formatted twice and intellij will ask to
     * confirm unlocking of non-project file in the other project.
     */
    private boolean isPsiFileEligible(Project project, PsiFile psiFile) {
        return psiFile != null &&
                project.isInitialized() &&
                !project.isDisposed() &&
                isPsiFileInProject(project, psiFile) &&
                psiFile.getModificationStamp() != 0;
    }

    private void processPsiFile(Project project, PsiFile psiFile) {
        if (psiFile.getName().endsWith(".elm")) {
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
        }
    }
}
