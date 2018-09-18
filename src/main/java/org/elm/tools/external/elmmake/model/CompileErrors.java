package org.elm.tools.external.elmmake.model;

import java.util.List;

public class CompileErrors {
    public String type;
    public List<CompileError> errors;
    public String path;
    public String title;
    public List<Object> message;
}
