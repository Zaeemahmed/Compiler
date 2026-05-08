package compiler.Parser;

import java.util.HashMap;
import java.util.Map;

class Scope {
    private final Scope parent;
    private final Map<String, String> variables = new HashMap<>();

    Scope(Scope parent) {
        this.parent = parent;
    }

    boolean containsInCurrentScope(String name) {
        return variables.containsKey(name);
    }

    void define(String name, String type) {
        variables.put(name, type);
    }

    String resolve(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        return parent == null ? null : parent.resolve(name);
    }
}
