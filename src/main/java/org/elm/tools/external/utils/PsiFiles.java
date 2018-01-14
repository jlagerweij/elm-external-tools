package org.elm.tools.external.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiFile;

public class PsiFiles {
    private PsiFiles() {
        // static class
    }

    public static boolean isPsiFileInProject(Project project, PsiFile file) {
        return ProjectRootManager.getInstance(project).getFileIndex().isInContent(file.getVirtualFile());
    }
}
