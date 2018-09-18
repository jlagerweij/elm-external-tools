package org.elm.tools.external.elmmake.parser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.elm.tools.external.elmmake.model.Problems;

import java.util.List;

public class Elm018Parser {

    public static List<Problems> parseProblemJsonElm018(String output) {
        return new Gson().fromJson(output, new TypeToken<List<Problems>>() {
        }.getType());
    }

}
