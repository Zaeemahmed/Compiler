# Person A code generation smoke tests

From the project root, after adding `CodeGenerator.java`, patching `Compiler.java`, and adding ASM to `build.gradle.kts`, run for example:

```bash
./gradlew clean build
./gradlew run --args="tests/codegen/01_print.lang -o tests/codegen/test.class"
java -cp tests/codegen test
```

Repeat with the other `.lang` files and change the target name each time, for example `test2.class`, `test3.class`, etc.
