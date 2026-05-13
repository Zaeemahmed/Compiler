package compiler.Parser;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * JVM bytecode generator for the LINFO2132 project language.
 *
 * This generator deliberately lives in compiler.Parser because the current AST node
 * classes are package-private. It emits real JVM .class files using ASM.
 */
public class CodeGenerator {
    private static final String OBJECT = "java/lang/Object";
    private static final String SYSTEM = "java/lang/System";
    private static final String PRINT_STREAM = "java/io/PrintStream";

    private final Map<String, CollectionInfo> collections = new LinkedHashMap<>();
    private final Map<String, FunctionInfo> functions = new LinkedHashMap<>();
    private final Map<String, String> globals = new LinkedHashMap<>();
    private final Map<String, VarDeclarationNode> globalDeclarations = new LinkedHashMap<>();

    private ClassWriter classWriter;
    private MethodVisitor mv;
    private String mainClassName;
    private Path outputDirectory;

    private final Deque<Map<String, LocalVar>> scopes = new ArrayDeque<>();
    private int nextLocal;

    public void generate(AST ast, String targetFile) throws IOException {
        if (ast == null || ast.getProgram() == null) {
            throw new IllegalArgumentException("Cannot generate code from an empty AST");
        }

        Path outputFile = resolveOutputFile(targetFile);
        this.outputDirectory = outputFile.getParent() == null ? Paths.get(".") : outputFile.getParent();
        Files.createDirectories(outputDirectory);
        this.mainClassName = sanitizeClassName(removeClassExtension(outputFile.getFileName().toString()));

        collectDeclarations(ast.getProgram().getStatements());
        writeCollectionClasses();
        writeMainClass(ast.getProgram().getStatements(), outputFile);
    }

    private Path resolveOutputFile(String targetFile) {
        if (targetFile == null || targetFile.isBlank()) {
            return Paths.get("Main.class");
        }
        Path path = Paths.get(targetFile);
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".class")) {
            path = path.resolveSibling(fileName + ".class");
        }
        return path;
    }

    private String removeClassExtension(String fileName) {
        return fileName.endsWith(".class") ? fileName.substring(0, fileName.length() - 6) : fileName;
    }

    private String sanitizeClassName(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((i == 0 && Character.isJavaIdentifierStart(c)) || (i > 0 && Character.isJavaIdentifierPart(c))) {
                result.append(c);
            } else {
                result.append('_');
            }
        }
        return result.length() == 0 ? "Main" : result.toString();
    }

    private void collectDeclarations(List statements) {
        for (Object object : statements) {
            StatementNode stmt = (StatementNode) object;
            if (stmt instanceof CollectionNode) {
                CollectionNode collection = (CollectionNode) stmt;
                CollectionInfo info = new CollectionInfo(collection.getName());
                for (Object fieldObject : collection.getFields()) {
                    VarDeclarationNode field = (VarDeclarationNode) fieldObject;
                    info.fields.add(new FieldInfo(field.getIdentifier(), normalizeType(field.getType())));
                }
                collections.put(info.name, info);
            } else if (stmt instanceof FunctionNode) {
                FunctionNode function = (FunctionNode) stmt;
                FunctionInfo info = new FunctionInfo(function.getName(), normalizeType(function.getReturnType()), function);
                for (Object parameterObject : function.getParameters()) {
                    VarDeclarationNode parameter = (VarDeclarationNode) parameterObject;
                    info.parameterTypes.add(normalizeType(parameter.getType()));
                }
                functions.put(info.name, info);
            } else if (stmt instanceof VarDeclarationNode) {
                VarDeclarationNode global = (VarDeclarationNode) stmt;
                String type = normalizeType(global.getType());
                globals.put(global.getIdentifier(), type);
                globalDeclarations.put(global.getIdentifier(), global);
            }
        }
    }

    private void writeCollectionClasses() throws IOException {
        for (CollectionInfo collection : collections.values()) {
            ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V17, ACC_PUBLIC, collection.name, null, OBJECT, null);

            MethodVisitor constructor = cw.visitMethod(ACC_PUBLIC, "<init>", collectionConstructorDescriptor(collection), null, null);
            constructor.visitCode();
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false);

            int localIndex = 1;
            for (FieldInfo field : collection.fields) {
                cw.visitField(ACC_PUBLIC, field.name, descriptor(field.type), null, null).visitEnd();
                constructor.visitVarInsn(ALOAD, 0);
                constructor.visitVarInsn(loadOpcode(field.type), localIndex);
                constructor.visitFieldInsn(PUTFIELD, collection.name, field.name, descriptor(field.type));
                localIndex += localSlots(field.type);
            }

            constructor.visitInsn(RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
            cw.visitEnd();

            Files.write(outputDirectory.resolve(collection.name + ".class"), cw.toByteArray());
        }
    }

    private void writeMainClass(List statements, Path outputFile) throws IOException {
        classWriter = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V17, ACC_PUBLIC, mainClassName, null, OBJECT, null);
        writeDefaultConstructor();
        writeGlobalFields();
        writeGlobalInitializer();

        boolean hasMain = false;
        for (Object object : statements) {
            StatementNode stmt = (StatementNode) object;
            if (stmt instanceof FunctionNode) {
                FunctionNode function = (FunctionNode) stmt;
                writeFunction(function);
                if ("main".equals(function.getName())) {
                    hasMain = true;
                }
            }
        }

        if (!hasMain) {
            writeEmptyJavaMain();
        }

        classWriter.visitEnd();
        Files.write(outputFile, classWriter.toByteArray());
    }

    private void writeDefaultConstructor() {
        MethodVisitor constructor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private void writeGlobalFields() {
        for (Map.Entry<String, String> entry : globals.entrySet()) {
            classWriter.visitField(ACC_PRIVATE | ACC_STATIC, entry.getKey(), descriptor(entry.getValue()), null, null).visitEnd();
        }
    }

    private void writeGlobalInitializer() {
        mv = classWriter.visitMethod(ACC_PRIVATE | ACC_STATIC, "__initGlobals", "()V", null, null);
        mv.visitCode();
        scopes.clear();
        pushScope();
        nextLocal = 0;

        for (VarDeclarationNode global : globalDeclarations.values()) {
            String type = normalizeType(global.getType());
            if (global.getValue() == null) {
                emitDefaultValue(type);
            } else {
                emitExpressionAs(global.getValue(), type);
            }
            mv.visitFieldInsn(PUTSTATIC, mainClassName, global.getIdentifier(), descriptor(type));
        }

        popScope();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = null;
    }

    private void writeFunction(FunctionNode function) {
        boolean isMain = "main".equals(function.getName());
        String methodName = isMain ? "main" : function.getName();
        String methodDescriptor = isMain ? "([Ljava/lang/String;)V" : functionDescriptor(function);

        mv = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, methodName, methodDescriptor, null, null);
        mv.visitCode();
        scopes.clear();
        pushScope();
        nextLocal = isMain ? 1 : 0;

        if (isMain) {
            mv.visitMethodInsn(INVOKESTATIC, mainClassName, "__initGlobals", "()V", false);
        } else {
            int index = 0;
            for (Object parameterObject : function.getParameters()) {
                VarDeclarationNode parameter = (VarDeclarationNode) parameterObject;
                String type = normalizeType(parameter.getType());
                defineLocal(parameter.getIdentifier(), type, index);
                index += localSlots(type);
                nextLocal = index;
            }
        }

        emitStatements(function.getBody(), false);

        String returnType = isMain ? "void" : normalizeType(function.getReturnType());
        if ("void".equals(returnType)) {
            mv.visitInsn(RETURN);
        } else {
            emitDefaultValue(returnType);
            mv.visitInsn(returnOpcode(returnType));
        }

        popScope();
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = null;
    }

    private void writeEmptyJavaMain() {
        mv = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, mainClassName, "__initGlobals", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = null;
    }

    private void emitStatements(List statements, boolean newScope) {
        if (newScope) {
            pushScope();
        }
        for (Object object : statements) {
            emitStatement((StatementNode) object);
        }
        if (newScope) {
            popScope();
        }
    }

    private void emitStatement(StatementNode stmt) {
        if (stmt instanceof CollectionNode || stmt instanceof FunctionNode) {
            return;
        }
        if (stmt instanceof VarDeclarationNode) {
            emitVarDeclaration((VarDeclarationNode) stmt);
        } else if (stmt instanceof AssignmentNode) {
            emitAssignment((AssignmentNode) stmt);
        } else if (stmt instanceof IfNode) {
            emitIf((IfNode) stmt);
        } else if (stmt instanceof WhileNode) {
            emitWhile((WhileNode) stmt);
        } else if (stmt instanceof ForNode) {
            emitFor((ForNode) stmt);
        } else if (stmt instanceof ReturnNode) {
            emitReturn((ReturnNode) stmt);
        } else if (stmt instanceof ExpressionStatementNode) {
            emitExpressionStatement((ExpressionStatementNode) stmt);
        } else {
            throw new IllegalStateException("Unsupported statement node: " + stmt.getClass().getSimpleName());
        }
    }

    private void emitVarDeclaration(VarDeclarationNode declaration) {
        String type = normalizeType(declaration.getType());
        int index = allocateLocal(declaration.getIdentifier(), type);
        if (declaration.getValue() == null) {
            emitDefaultValue(type);
        } else {
            emitExpressionAs(declaration.getValue(), type);
        }
        mv.visitVarInsn(storeOpcode(type), index);
    }

    private void emitAssignment(AssignmentNode assignment) {
        LocalVar local = resolveLocal(assignment.getIdentifier());
        if (local != null) {
            emitExpressionAs(assignment.getValue(), local.type);
            mv.visitVarInsn(storeOpcode(local.type), local.index);
            return;
        }

        String globalType = globals.get(assignment.getIdentifier());
        if (globalType == null) {
            throw new IllegalStateException("Unknown assignment target: " + assignment.getIdentifier());
        }
        emitExpressionAs(assignment.getValue(), globalType);
        mv.visitFieldInsn(PUTSTATIC, mainClassName, assignment.getIdentifier(), descriptor(globalType));
    }

    private void emitIf(IfNode node) {
        Label elseLabel = new Label();
        Label endLabel = new Label();
        emitExpressionAs(node.getCondition(), "BOOLEAN");
        mv.visitJumpInsn(IFEQ, elseLabel);
        emitStatements(node.getThenBranch(), true);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(elseLabel);
        if (node.getElseBranch() != null) {
            emitStatements(node.getElseBranch(), true);
        }
        mv.visitLabel(endLabel);
    }

    private void emitWhile(WhileNode node) {
        Label startLabel = new Label();
        Label endLabel = new Label();
        mv.visitLabel(startLabel);
        emitExpressionAs(node.getCondition(), "BOOLEAN");
        mv.visitJumpInsn(IFEQ, endLabel);
        emitStatements(node.getBody(), true);
        mv.visitJumpInsn(GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    private void emitFor(ForNode node) {
        LocalVar loopVar = resolveLocal(node.getIdentifier());
        if (loopVar == null) {
            throw new IllegalStateException("For loop variable is not declared: " + node.getIdentifier());
        }

        emitExpressionAs(node.getRangeStart(), "INT");
        mv.visitVarInsn(ISTORE, loopVar.index);

        Label startLabel = new Label();
        Label endLabel = new Label();
        mv.visitLabel(startLabel);
        mv.visitVarInsn(ILOAD, loopVar.index);
        emitExpressionAs(node.getRangeEnd(), "INT");
        mv.visitJumpInsn(IF_ICMPGE, endLabel);

        emitStatements(node.getBody(), true);

        emitExpressionAs(node.getUpdate(), "INT");
        mv.visitVarInsn(ISTORE, loopVar.index);
        mv.visitJumpInsn(GOTO, startLabel);
        mv.visitLabel(endLabel);
    }

    private void emitReturn(ReturnNode node) {
        if (node.getValue() == null) {
            mv.visitInsn(RETURN);
            return;
        }
        String type = inferExpressionType(node.getValue());
        emitExpression(node.getValue());
        mv.visitInsn(returnOpcode(type));
    }

    private void emitExpressionStatement(ExpressionStatementNode node) {
        String type = inferExpressionType(node.getExpression());
        emitExpression(node.getExpression());
        if (!"void".equals(type)) {
            mv.visitInsn(popOpcode(type));
        }
    }

    private void emitExpressionAs(ExpressionNode expression, String expectedType) {
        String actualType = inferExpressionType(expression);
        emitExpression(expression);
        if ("FLOAT".equals(expectedType) && "INT".equals(actualType)) {
            mv.visitInsn(I2F);
        }
    }

    private void emitExpression(ExpressionNode expression) {
        if (expression instanceof IntegerNode) {
            mv.visitLdcInsn(((IntegerNode) expression).value);
        } else if (expression instanceof FloatNode) {
            mv.visitLdcInsn(((FloatNode) expression).value);
        } else if (expression instanceof BooleanNode) {
            mv.visitInsn(((BooleanNode) expression).value ? ICONST_1 : ICONST_0);
        } else if (expression instanceof StringNode) {
            mv.visitLdcInsn(((StringNode) expression).value);
        } else if (expression instanceof IdentifierNode) {
            emitIdentifier((IdentifierNode) expression);
        } else if (expression instanceof FunctionCallNode) {
            emitFunctionCall((FunctionCallNode) expression);
        } else if (expression instanceof OperationNode) {
            emitOperation((OperationNode) expression);
        } else if (expression instanceof ArrayCreationNode) {
            emitArrayCreation((ArrayCreationNode) expression);
        } else if (expression instanceof ArrayAccessNode) {
            emitArrayAccess((ArrayAccessNode) expression);
        } else if (expression instanceof FieldAccessNode) {
            emitFieldAccess((FieldAccessNode) expression);
        } else {
            throw new IllegalStateException("Unsupported expression node: " + expression.getClass().getSimpleName());
        }
    }

    private void emitIdentifier(IdentifierNode identifier) {
        LocalVar local = resolveLocal(identifier.name);
        if (local != null) {
            mv.visitVarInsn(loadOpcode(local.type), local.index);
            return;
        }

        String globalType = globals.get(identifier.name);
        if (globalType == null) {
            throw new IllegalStateException("Unknown identifier: " + identifier.name);
        }
        mv.visitFieldInsn(GETSTATIC, mainClassName, identifier.name, descriptor(globalType));
    }

    private void emitFunctionCall(FunctionCallNode call) {
        if (emitBuiltinCall(call)) {
            return;
        }

        CollectionInfo collection = collections.get(call.name);
        if (collection != null) {
            mv.visitTypeInsn(NEW, collection.name);
            mv.visitInsn(DUP);
            for (int i = 0; i < call.arguments.size(); i++) {
                FieldInfo field = collection.fields.get(i);
                emitExpressionAs((ExpressionNode) call.arguments.get(i), field.type);
            }
            mv.visitMethodInsn(INVOKESPECIAL, collection.name, "<init>", collectionConstructorDescriptor(collection), false);
            return;
        }

        FunctionInfo function = functions.get(call.name);
        if (function == null) {
            throw new IllegalStateException("Unknown function: " + call.name);
        }
        for (int i = 0; i < call.arguments.size(); i++) {
            emitExpressionAs((ExpressionNode) call.arguments.get(i), function.parameterTypes.get(i));
        }
        mv.visitMethodInsn(INVOKESTATIC, mainClassName, function.name, functionDescriptor(function), false);
    }

    private boolean emitBuiltinCall(FunctionCallNode call) {
        switch (call.name) {
            case "print_INT":
                emitPrintLike("print", "INT", firstArgument(call));
                return true;
            case "print_FLOAT":
                emitPrintLike("print", "FLOAT", firstArgument(call));
                return true;
            case "print":
                if (call.arguments.isEmpty()) {
                    return true;
                }
                emitPrintLike("print", inferExpressionType(firstArgument(call)), firstArgument(call));
                return true;
            case "println":
                if (call.arguments.isEmpty()) {
                    mv.visitFieldInsn(GETSTATIC, SYSTEM, "out", "L" + PRINT_STREAM + ";");
                    mv.visitMethodInsn(INVOKEVIRTUAL, PRINT_STREAM, "println", "()V", false);
                    return true;
                }
                emitPrintLike("println", inferExpressionType(firstArgument(call)), firstArgument(call));
                return true;
            case "read_INT":
                emitScannerRead("nextInt", "()I");
                return true;
            case "read_FLOAT":
                emitScannerRead("nextFloat", "()F");
                return true;
            case "read_STRING":
                emitScannerRead("nextLine", "()Ljava/lang/String;");
                return true;
            case "read_BOOL":
                emitScannerRead("nextBoolean", "()Z");
                return true;
            case "str":
                emitExpressionAs(firstArgument(call), "INT");
                mv.visitInsn(I2C);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "toString", "(C)Ljava/lang/String;", false);
                return true;
            case "floor":
                emitExpressionAs(firstArgument(call), "FLOAT");
                mv.visitInsn(F2D);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
                mv.visitInsn(D2I);
                return true;
            case "ceil":
                emitExpressionAs(firstArgument(call), "FLOAT");
                mv.visitInsn(F2D);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
                mv.visitInsn(D2I);
                return true;
            case "length":
                ExpressionNode argument = firstArgument(call);
                String type = inferExpressionType(argument);
                emitExpression(argument);
                if ("STRING".equals(type)) {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                } else if (type.endsWith("[]")) {
                    mv.visitInsn(ARRAYLENGTH);
                } else {
                    throw new IllegalStateException("length expects STRING or ARRAY, got " + type);
                }
                return true;
            default:
                return false;
        }
    }

    private ExpressionNode firstArgument(FunctionCallNode call) {
        if (call.arguments.isEmpty()) {
            throw new IllegalStateException(call.name + " expects an argument");
        }
        return (ExpressionNode) call.arguments.get(0);
    }

    private void emitPrintLike(String method, String type, ExpressionNode argument) {
        mv.visitFieldInsn(GETSTATIC, SYSTEM, "out", "L" + PRINT_STREAM + ";");
        emitExpressionAs(argument, type);
        mv.visitMethodInsn(INVOKEVIRTUAL, PRINT_STREAM, method, printStreamDescriptor(type), false);
    }

    private void emitScannerRead(String methodName, String methodDescriptor) {
        mv.visitTypeInsn(NEW, "java/util/Scanner");
        mv.visitInsn(DUP);
        mv.visitFieldInsn(GETSTATIC, SYSTEM, "in", "Ljava/io/InputStream;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/Scanner", methodName, methodDescriptor, false);
    }

    private void emitOperation(OperationNode operation) {
        String op = operation.operator;
        if ("not".equals(op)) {
            emitExpressionAs(operation.right, "BOOLEAN");
            emitBooleanNegation();
            return;
        }

        String leftType = inferExpressionType(operation.left);
        String rightType = inferExpressionType(operation.right);

        if ("+".equals(op) && ("STRING".equals(leftType) || "STRING".equals(rightType))) {
            emitStringConcat(operation.left, leftType, operation.right, rightType);
            return;
        }

        if (isArithmeticOperator(op)) {
            String resultType = numericResultType(leftType, rightType);
            emitExpressionAs(operation.left, resultType);
            emitExpressionAs(operation.right, resultType);
            emitArithmeticInstruction(op, resultType);
            return;
        }

        if ("&&".equals(op) || "||".equals(op)) {
            emitExpressionAs(operation.left, "BOOLEAN");
            emitExpressionAs(operation.right, "BOOLEAN");
            mv.visitInsn("&&".equals(op) ? IAND : IOR);
            return;
        }

        if (isComparisonOperator(op)) {
            emitComparison(op, operation.left, leftType, operation.right, rightType);
            return;
        }

        throw new IllegalStateException("Unsupported operator: " + op);
    }

    private void emitStringConcat(ExpressionNode left, String leftType, ExpressionNode right, String rightType) {
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        emitExpression(left);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", stringBuilderAppendDescriptor(leftType), false);
        emitExpression(right);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", stringBuilderAppendDescriptor(rightType), false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    private void emitArithmeticInstruction(String op, String type) {
        boolean isFloat = "FLOAT".equals(type);
        switch (op) {
            case "+": mv.visitInsn(isFloat ? FADD : IADD); break;
            case "-": mv.visitInsn(isFloat ? FSUB : ISUB); break;
            case "*": mv.visitInsn(isFloat ? FMUL : IMUL); break;
            case "/": mv.visitInsn(isFloat ? FDIV : IDIV); break;
            case "%": mv.visitInsn(isFloat ? FREM : IREM); break;
            default: throw new IllegalStateException("Unsupported arithmetic operator: " + op);
        }
    }

    private void emitComparison(String op, ExpressionNode left, String leftType, ExpressionNode right, String rightType) {
        if ("STRING".equals(leftType) && ("==".equals(op) || "=/=".equals(op) || "!=".equals(op))) {
            emitExpression(left);
            emitExpression(right);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            if ("=/=".equals(op) || "!=".equals(op)) {
                emitBooleanNegation();
            }
            return;
        }

        String comparisonType = numericResultType(leftType, rightType);
        emitExpressionAs(left, comparisonType);
        emitExpressionAs(right, comparisonType);

        if ("FLOAT".equals(comparisonType)) {
            mv.visitInsn(FCMPL);
            emitBooleanFromSingleCompare(floatJumpOpcode(op));
        } else if (isReferenceType(comparisonType)) {
            emitBooleanFromJump(referenceJumpOpcode(op));
        } else {
            emitBooleanFromJump(intJumpOpcode(op));
        }
    }

    private void emitBooleanNegation() {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(IFEQ, trueLabel);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(endLabel);
    }

    private void emitBooleanFromJump(int jumpOpcode) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(jumpOpcode, trueLabel);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(endLabel);
    }

    private void emitBooleanFromSingleCompare(int jumpOpcode) {
        Label trueLabel = new Label();
        Label endLabel = new Label();
        mv.visitJumpInsn(jumpOpcode, trueLabel);
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, endLabel);
        mv.visitLabel(trueLabel);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(endLabel);
    }

    private void emitArrayCreation(ArrayCreationNode node) {
        String elementType = normalizeType(node.elementType);
        emitExpressionAs(node.size, "INT");
        switch (elementType) {
            case "INT": mv.visitIntInsn(NEWARRAY, T_INT); break;
            case "FLOAT": mv.visitIntInsn(NEWARRAY, T_FLOAT); break;
            case "BOOLEAN": mv.visitIntInsn(NEWARRAY, T_BOOLEAN); break;
            default: mv.visitTypeInsn(ANEWARRAY, internalName(elementType)); break;
        }
    }

    private void emitArrayAccess(ArrayAccessNode node) {
        String arrayType = inferExpressionType(node.array);
        String elementType = arrayType.substring(0, arrayType.length() - 2);
        emitExpression(node.array);
        emitExpressionAs(node.index, "INT");
        mv.visitInsn(arrayLoadOpcode(elementType));
    }

    private void emitFieldAccess(FieldAccessNode node) {
        String ownerType = inferExpressionType(node.object);
        FieldInfo field = fieldInfo(ownerType, node.field);
        emitExpression(node.object);
        mv.visitFieldInsn(GETFIELD, ownerType, field.name, descriptor(field.type));
    }

    private String inferExpressionType(ExpressionNode expression) {
        if (expression instanceof IntegerNode) return "INT";
        if (expression instanceof FloatNode) return "FLOAT";
        if (expression instanceof BooleanNode) return "BOOLEAN";
        if (expression instanceof StringNode) return "STRING";
        if (expression instanceof IdentifierNode) {
            IdentifierNode identifier = (IdentifierNode) expression;
            LocalVar local = resolveLocal(identifier.name);
            if (local != null) return local.type;
            String globalType = globals.get(identifier.name);
            if (globalType != null) return globalType;
            throw new IllegalStateException("Unknown identifier: " + identifier.name);
        }
        if (expression instanceof FunctionCallNode) {
            FunctionCallNode call = (FunctionCallNode) expression;
            return inferFunctionCallType(call);
        }
        if (expression instanceof OperationNode) {
            OperationNode operation = (OperationNode) expression;
            if ("not".equals(operation.operator) || "&&".equals(operation.operator) || "||".equals(operation.operator) || isComparisonOperator(operation.operator)) {
                return "BOOLEAN";
            }
            String leftType = inferExpressionType(operation.left);
            String rightType = inferExpressionType(operation.right);
            if ("+".equals(operation.operator) && ("STRING".equals(leftType) || "STRING".equals(rightType))) {
                return "STRING";
            }
            return numericResultType(leftType, rightType);
        }
        if (expression instanceof ArrayCreationNode) {
            return normalizeType(((ArrayCreationNode) expression).elementType) + "[]";
        }
        if (expression instanceof ArrayAccessNode) {
            String arrayType = inferExpressionType(((ArrayAccessNode) expression).array);
            return arrayType.substring(0, arrayType.length() - 2);
        }
        if (expression instanceof FieldAccessNode) {
            FieldAccessNode fieldAccess = (FieldAccessNode) expression;
            String ownerType = inferExpressionType(fieldAccess.object);
            return fieldInfo(ownerType, fieldAccess.field).type;
        }
        throw new IllegalStateException("Cannot infer type for: " + expression.getClass().getSimpleName());
    }

    private String inferFunctionCallType(FunctionCallNode call) {
        switch (call.name) {
            case "print_INT":
            case "print_FLOAT":
            case "print":
            case "println":
                return "void";
            case "read_INT": return "INT";
            case "read_FLOAT": return "FLOAT";
            case "read_STRING": return "STRING";
            case "read_BOOL": return "BOOLEAN";
            case "str": return "STRING";
            case "floor":
            case "ceil":
            case "length": return "INT";
            default:
                if (collections.containsKey(call.name)) return call.name;
                FunctionInfo function = functions.get(call.name);
                if (function != null) return function.returnType;
                throw new IllegalStateException("Unknown function or collection constructor: " + call.name);
        }
    }

    private int allocateLocal(String name, String type) {
        int index = nextLocal;
        defineLocal(name, type, index);
        nextLocal += localSlots(type);
        return index;
    }

    private void defineLocal(String name, String type, int index) {
        scopes.peek().put(name, new LocalVar(type, index));
    }

    private LocalVar resolveLocal(String name) {
        for (Map<String, LocalVar> scope : scopes) {
            LocalVar local = scope.get(name);
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    private void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    private void popScope() {
        scopes.pop();
    }

    private void emitDefaultValue(String type) {
        switch (normalizeType(type)) {
            case "INT":
            case "BOOLEAN":
                mv.visitInsn(ICONST_0);
                break;
            case "FLOAT":
                mv.visitInsn(FCONST_0);
                break;
            default:
                mv.visitInsn(ACONST_NULL);
                break;
        }
    }

    private String normalizeType(String type) {
        if (type == null) return "void";
        if (type.endsWith("[]")) return normalizeType(type.substring(0, type.length() - 2)) + "[]";
        return "BOOL".equals(type) ? "BOOLEAN" : type;
    }

    private String descriptor(String type) {
        type = normalizeType(type);
        if (type.endsWith("[]")) {
            return "[" + descriptor(type.substring(0, type.length() - 2));
        }
        switch (type) {
            case "INT": return "I";
            case "FLOAT": return "F";
            case "BOOLEAN": return "Z";
            case "STRING": return "Ljava/lang/String;";
            case "void": return "V";
            default: return "L" + internalName(type) + ";";
        }
    }

    private String internalName(String type) {
        if ("STRING".equals(type)) return "java/lang/String";
        return type.replace('.', '/');
    }

    private String functionDescriptor(FunctionNode function) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Object parameterObject : function.getParameters()) {
            VarDeclarationNode parameter = (VarDeclarationNode) parameterObject;
            descriptor.append(descriptor(parameter.getType()));
        }
        descriptor.append(')').append(descriptor(normalizeType(function.getReturnType())));
        return descriptor.toString();
    }

    private String functionDescriptor(FunctionInfo function) {
        StringBuilder descriptor = new StringBuilder("(");
        for (String type : function.parameterTypes) {
            descriptor.append(descriptor(type));
        }
        descriptor.append(')').append(descriptor(function.returnType));
        return descriptor.toString();
    }

    private String collectionConstructorDescriptor(CollectionInfo collection) {
        StringBuilder descriptor = new StringBuilder("(");
        for (FieldInfo field : collection.fields) {
            descriptor.append(descriptor(field.type));
        }
        descriptor.append(")V");
        return descriptor.toString();
    }

    private String printStreamDescriptor(String type) {
        type = normalizeType(type);
        switch (type) {
            case "INT": return "(I)V";
            case "FLOAT": return "(F)V";
            case "BOOLEAN": return "(Z)V";
            case "STRING": return "(Ljava/lang/String;)V";
            default: return "(Ljava/lang/Object;)V";
        }
    }

    private String stringBuilderAppendDescriptor(String type) {
        type = normalizeType(type);
        switch (type) {
            case "INT": return "(I)Ljava/lang/StringBuilder;";
            case "FLOAT": return "(F)Ljava/lang/StringBuilder;";
            case "BOOLEAN": return "(Z)Ljava/lang/StringBuilder;";
            case "STRING": return "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
            default: return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
        }
    }

    private int loadOpcode(String type) {
        switch (normalizeType(type)) {
            case "INT":
            case "BOOLEAN": return ILOAD;
            case "FLOAT": return FLOAD;
            default: return ALOAD;
        }
    }

    private int storeOpcode(String type) {
        switch (normalizeType(type)) {
            case "INT":
            case "BOOLEAN": return ISTORE;
            case "FLOAT": return FSTORE;
            default: return ASTORE;
        }
    }

    private int returnOpcode(String type) {
        switch (normalizeType(type)) {
            case "INT":
            case "BOOLEAN": return IRETURN;
            case "FLOAT": return FRETURN;
            case "void": return RETURN;
            default: return ARETURN;
        }
    }

    private int popOpcode(String type) {
        return localSlots(type) == 2 ? POP2 : POP;
    }

    private int localSlots(String type) {
        return 1; // the language has no long/double values
    }

    private int arrayLoadOpcode(String elementType) {
        switch (normalizeType(elementType)) {
            case "INT": return IALOAD;
            case "FLOAT": return FALOAD;
            case "BOOLEAN": return BALOAD;
            default: return AALOAD;
        }
    }

    private String numericResultType(String left, String right) {
        left = normalizeType(left);
        right = normalizeType(right);
        if ("FLOAT".equals(left) || "FLOAT".equals(right)) return "FLOAT";
        if (isReferenceType(left) || isReferenceType(right)) return left;
        return "INT";
    }

    private boolean isReferenceType(String type) {
        type = normalizeType(type);
        return type.endsWith("[]") || "STRING".equals(type) || collections.containsKey(type);
    }

    private boolean isArithmeticOperator(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op) || "/".equals(op) || "%".equals(op);
    }

    private boolean isComparisonOperator(String op) {
        return "==".equals(op) || "=/=".equals(op) || "!=".equals(op) || "<".equals(op) || ">".equals(op) || "<=".equals(op) || ">=".equals(op);
    }

    private int intJumpOpcode(String op) {
        switch (op) {
            case "==": return IF_ICMPEQ;
            case "=/=":
            case "!=": return IF_ICMPNE;
            case "<": return IF_ICMPLT;
            case ">": return IF_ICMPGT;
            case "<=": return IF_ICMPLE;
            case ">=": return IF_ICMPGE;
            default: throw new IllegalStateException("Unsupported comparison operator: " + op);
        }
    }

    private int referenceJumpOpcode(String op) {
        switch (op) {
            case "==": return IF_ACMPEQ;
            case "=/=":
            case "!=": return IF_ACMPNE;
            default: throw new IllegalStateException("Only == and =/= are supported for reference comparison");
        }
    }

    private int floatJumpOpcode(String op) {
        switch (op) {
            case "==": return IFEQ;
            case "=/=":
            case "!=": return IFNE;
            case "<": return IFLT;
            case ">": return IFGT;
            case "<=": return IFLE;
            case ">=": return IFGE;
            default: throw new IllegalStateException("Unsupported float comparison operator: " + op);
        }
    }

    private FieldInfo fieldInfo(String collectionType, String fieldName) {
        CollectionInfo collection = collections.get(normalizeType(collectionType));
        if (collection == null) {
            throw new IllegalStateException("Not a collection type: " + collectionType);
        }
        for (FieldInfo field : collection.fields) {
            if (field.name.equals(fieldName)) {
                return field;
            }
        }
        throw new IllegalStateException("Unknown field " + fieldName + " in collection " + collectionType);
    }

    private static final class SafeClassWriter extends ClassWriter {
        SafeClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            if (type1.equals(type2)) {
                return type1;
            }
            if (OBJECT.equals(type1) || OBJECT.equals(type2)) {
                return OBJECT;
            }
            return OBJECT;
        }
    }

    private static final class LocalVar {
        final String type;
        final int index;
        LocalVar(String type, int index) {
            this.type = type;
            this.index = index;
        }
    }

    private static final class FieldInfo {
        final String name;
        final String type;
        FieldInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private static final class CollectionInfo {
        final String name;
        final List<FieldInfo> fields = new ArrayList<>();
        CollectionInfo(String name) {
            this.name = name;
        }
    }

    private static final class FunctionInfo {
        final String name;
        final String returnType;
        final FunctionNode node;
        final List<String> parameterTypes = new ArrayList<>();
        FunctionInfo(String name, String returnType, FunctionNode node) {
            this.name = name;
            this.returnType = returnType;
            this.node = node;
        }
    }
}
