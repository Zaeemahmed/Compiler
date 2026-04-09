package compiler.Parser;

import java.util.List;

class FunctionSignature {
    private final String name;
    private final String returnType;
    private final List<String> parameterTypes;

    FunctionSignature(String name, String returnType, List<String> parameterTypes) {
        this.name = name;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }

    String getName() {
        return name;
    }

    String getReturnType() {
        return returnType;
    }

    List<String> getParameterTypes() {
        return parameterTypes;
    }
}
