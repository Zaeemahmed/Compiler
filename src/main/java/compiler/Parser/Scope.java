package compiler.Parser;

import java.util.HashMap;
import java.util.Map;

class Scope {
    private final Scope parent;
    private final Map<String, VariableInfo> variables = new HashMap<>();

    Scope(Scope parent) {
        this.parent = parent;
    }

    boolean containsInCurrentScope(String name) {
        return variables.containsKey(name);
    }

    void define(String name, String type) {
        variables.put(name, new VariableInfo(type, null));
    }

    void define(String name, String type, String functionTarget) {
        variables.put(name, new VariableInfo(type, functionTarget));
    }

    String resolve(String name) {
        VariableInfo info = resolveVariable(name);
        return info == null ? null : info.type;
    }

    VariableInfo resolveVariable(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        return parent == null ? null : parent.resolveVariable(name);
    }

    void assign(String name, String type, String functionTarget) {
        if (variables.containsKey(name)) {
            variables.put(name, new VariableInfo(type, functionTarget));
            return;
        }
        if (parent != null) {
            parent.assign(name, type, functionTarget);
            return;
        }
        variables.put(name, new VariableInfo(type, functionTarget));
    }

    static class VariableInfo {
        final String type;
        final String functionTarget;

        VariableInfo(String type, String functionTarget) {
            this.type = type;
            this.functionTarget = functionTarget;
        }
    }
}
