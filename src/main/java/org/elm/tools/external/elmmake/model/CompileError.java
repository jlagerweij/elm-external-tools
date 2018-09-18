package org.elm.tools.external.elmmake.model;

import java.util.List;

public class CompileError {
    public String path;
    public String name;
    public List<CompileProblem> problems;
}
