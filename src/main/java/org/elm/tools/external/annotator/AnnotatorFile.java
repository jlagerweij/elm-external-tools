package org.elm.tools.external.annotator;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;

public class AnnotatorFile {
    private final PsiFile file;
    private Editor editor;

    public AnnotatorFile(PsiFile file) {
        this.file = file;
    }

    public PsiFile getFile() {
        return file;
    }

    public void setEditor(Editor editor) {
        this.editor = editor;
    }

    public Editor getEditor() {
        return editor;
    }
}
