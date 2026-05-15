# Grammar Report

## 1. Overview

This document provides a comprehensive description of the grammar for our custom programming language, as implemented in the compiler. The language is designed as a statically-typed, imperative language that compiles to JVM bytecode. The grammar defines the lexical rules (tokens) and syntactic rules that govern valid programs in the language.

The grammar is specified using Extended Backus-Naur Form (EBNF) notation, where:
- `|` denotes alternatives
- `[]` denotes optional elements
- `*` denotes zero or more repetitions
- `()` groups elements

The current implementation includes a complete lexer, parser, semantic analyzer, and code generator that successfully handles all rules described below.

## 2. Lexical Rules (Tokens)

The lexical analyzer (lexer) breaks the source code into tokens. These tokens are the basic building blocks that the parser uses to build the abstract syntax tree (AST).

### Keywords

The language defines the following reserved keywords, each serving a specific syntactic role:

| Keyword | Role | Example Usage |
|---------|------|---------------|
| `final` | Immutable variable modifier | `final INT MAX = 100;` |
| `def` | Function declaration | `def INT add(INT a, INT b) { ... }` |
| `if` | Conditional statement | `if (x > 0) { ... }` |
| `else` | Alternative branch of if | `if (cond) { ... } else { ... }` |
| `while` | While loop | `while (i < 10) { ... }` |
| `for` | For-range loop | `for (i; 0 -> 10; i + 1) { ... }` |
| `return` | Return statement | `return result;` |
| `not` | Logical negation | `if (not flag) { ... }` |
| `new` | Object instantiation | `Point p = new Point();` |
| `coll` | Collection (record/struct) type | `coll Point { INT x; INT y; }` |
| `ARRAY` | Array creation keyword | `INT[] arr = INT ARRAY[10];` |
| `true` | Boolean literal | `BOOLEAN flag = true;` |
| `false` | Boolean literal | `BOOLEAN flag = false;` |

### Primitive Types

The language supports the following built-in primitive types:

| Type | Description | Example |
|------|-------------|---------|
| `INT` | 32-bit signed integer | `INT x = 42;` |
| `FLOAT` | 32-bit floating-point number | `FLOAT pi = 3.14;` |
| `STRING` | Text string (in double quotes) | `STRING name = "Alice";` |
| `BOOLEAN` or `BOOL` | Boolean value (true/false) | `BOOLEAN ok = true;` |

### Operators and Punctuation

The language includes various operators for arithmetic, comparison, logical operations, and punctuation for structuring code:

#### Assignment and Comparison Operators
| Token | Meaning | Example |
|-------|---------|---------|
| `=` | Assignment | `x = 5;` |
| `==` | Equal | `if (a == b)` |
| `=/=` | Not equal | `if (a =/= b)` |
| `<` | Less than | `if (a < b)` |
| `<=` | Less or equal | `if (a <= b)` |
| `>` | Greater than | `if (a > b)` |
| `>=` | Greater or equal | `if (a >= b)` |

#### Logical Operators
| Token | Meaning | Example |
|-------|---------|---------|
| `&&` | Logical AND | `if (a && b)` |
| `\|\|` | Logical OR | `if (a \|\| b)` |
| `not` | Logical NOT | `if (not flag)` |

#### Arithmetic Operators
| Token | Meaning | Example |
|-------|---------|---------|
| `+` | Addition | `x + y` |
| `-` | Subtraction/Negation | `x - y` or `-x` |
| `*` | Multiplication | `x * y` |
| `/` | Division | `x / y` |
| `%` | Modulo | `x % y` |

#### Punctuation and Structural Tokens
| Token | Meaning | Example |
|-------|---------|---------|
| `(` `)` | Parentheses (grouping, function calls) | `func(a, b)` |
| `{` `}` | Braces (block statements) | `if (cond) { stmt; }` |
| `[` `]` | Brackets (array access) | `arr[0]` |
| `.` | Dot (member access) | `obj.field` |
| `;` | Semicolon (statement terminator) | `x = 5;` |
| `,` | Comma (parameter separator) | `func(a, b, c)` |
| `->` | Range operator (for loops) | `for (i; 0 -> 10; i + 1)` |

### Literals and Identifiers

#### Literals
- **Integer**: Sequences of digits, e.g., `42`, `0`, `123`
- **Float**: Digits with decimal point, e.g., `3.14`, `0.5`, `123.456`
- **Boolean**: `true` or `false`
- **String**: Text enclosed in double quotes, e.g., `"Hello, World!"`

#### Identifiers
- Start with lowercase letter or underscore: `[a-z_]`
- Followed by letters, digits, or underscores: `[a-zA-Z0-9_]*`
- Examples: `variable`, `myFunction`, `_private`, `x1`

## 3. Grammar Rules

### 3.1 Program

```
Program = Statement*
```

A program consists of zero or more statements. This is the root of the grammar - every valid program is a sequence of statements.

**Example:**
```
INT x = 5;
println(x);
```

### 3.2 Statements

```
Statement =   VarDeclaration ";"
            | Assignment ";"
            | IfStmt
            | WhileStmt
            | ForStmt
            | ReturnStmt ";"
            | FunctionDecl
            | CollectionDecl
            | Expr ";"
```

Statements are the executable units of the program. Each statement ends with a semicolon except for control structures (if, while, for) and declarations.

#### Variable Declaration
```
VarDeclaration = ["final"] Type Identifier ["=" Expr]
```

Declares a variable with optional `final` modifier and optional initialization.

**Examples:**
```
INT x;              // Declaration without initialization
INT y = 10;         // Declaration with initialization
final INT MAX = 100; // Final variable
```

#### Assignment
```
Assignment = Identifier "=" Expr
```

Assigns the value of an expression to a variable.

**Example:**
```
x = x + 1;
```

#### If Statement
```
IfStmt = "if" "(" Expr ")" "{" Statement* "}" ["else" "{" Statement* "}"]
```

Conditional execution with optional else branch.

**Example:**
```
if (x > 0) {
    println("Positive");
} else {
    println("Non-positive");
}
```

#### While Statement
```
WhileStmt = "while" "(" Expr ")" "{" Statement* "}"
```

Repeated execution while condition is true.

**Example:**
```
while (i < 10) {
    i = i + 1;
}
```

#### For Statement
```
ForStmt = "for" "(" Identifier ";" Expr "->" Expr ";" Expr ")" "{" Statement* "}"
```

Range-based for loop with initialization, range, and increment expressions.

**Example:**
```
for (i; 0 -> 10; i + 1) {
    println(i);
}
```

#### Return Statement
```
ReturnStmt = "return" [Expr]
```

Returns control flow, optionally with a value.

**Examples:**
```
return;        // Return from void function
return result; // Return value
```

#### Function Declaration
```
FunctionDecl = "def" Identifier "(" [ParamList] ")" "{" Statement* "}"
             | "def" Type Identifier "(" [ParamList] ")" "{" Statement* "}"
```

Declares a function with optional return type (void if omitted).

**Examples:**
```
def main() {           // Void function
    println("Hello");
}

def INT add(INT a, INT b) {  // Typed function
    return a + b;
}
```

#### Collection Declaration
```
CollectionDecl = "coll" Identifier "{" (VarDeclaration ";")* "}"
```

Defines a user-defined type (record/struct) with fields.

**Example:**
```
coll Point {
    INT x;
    INT y;
}
```

### 3.3 Types

```
Type = "INT" | "FLOAT" | "STRING" | "BOOLEAN" | "BOOL" | Identifier | Type "[]"
```

Types can be primitive types, user-defined collection types (identifiers), or arrays.

**Examples:**
```
INT          // Primitive integer
FLOAT        // Primitive float
STRING       // Primitive string
BOOLEAN      // Primitive boolean
Point        // User-defined type
INT[]        // Array of integers
STRING[]     // Array of strings
Point[]      // Array of points
```

**Note:** Multi-dimensional arrays (e.g., `INT[][]`) are not currently supported in the parser implementation.

### 3.4 Expressions

Expressions evaluate to values and can be used in assignments, function calls, conditions, etc. The grammar is ordered by operator precedence (highest to lowest):

```
Expression = Expr ("+" | "-" | "*" | "/" | "%" | "==" | "=/=" | "&&" | "||" | "<" | ">" | "<=" | ">=") Expr
           | "-" Expr
           | "not" Expr
           | "(" Expr ")"
           | Literal
           | Identifier
           | Identifier "(" [ArgList] ")"
           | Expr "[" Expr "]"
           | Expr "." Identifier
           | Type "ARRAY" "[" Expr "]"
           | NewExpr
```

#### Operator Precedence (from highest to lowest):
1. **Unary operators**: `-` (negation), `not` (logical not)
2. **Multiplicative**: `*`, `/`, `%`
3. **Additive**: `+`, `-`
4. **Relational**: `<`, `>`, `<=`, `>=`
5. **Equality**: `==`, `=/=`
6. **Logical AND**: `&&`
7. **Logical OR**: `||`

#### Expression Types:

**Binary Operations:**
```
a + b      // Addition
x * y      // Multiplication
p == q     // Equality
a && b     // Logical AND
```

**Unary Operations:**
```
- x        // Negation
not flag   // Logical NOT
```

**Parenthesized Expressions:**
```
(a + b) * c  // Grouping for precedence
```

**Literals:**
```
42         // Integer
3.14       // Float
"Hello"    // String
true       // Boolean
```

**Variable References:**
```
variable   // Simple identifier
```

**Function Calls:**
```
func()           // No arguments
add(a, b)        // With arguments
println("Hi")    // Built-in function
```

**Array Access:**
```
arr[0]      // Access first element
arr[i + 1]  // Computed index
```

**Field Access:**
```
point.x     // Access field of collection
obj.field   // Access field of object
```

**Array Creation:**
```
INT ARRAY[10]     // Create array of 10 integers
STRING ARRAY[5]   // Create array of 5 strings
```

**Object Instantiation:**
```
new Point()       // Create new instance of Point collection
```

#### Argument Lists
```
ArgList = Expr ("," Expr)*
```

Comma-separated list of expressions for function calls.

**Example:**
```
func(a, b + c, "string")
```

## 4. Summary of Known Limitations

While the current grammar and implementation are quite comprehensive, there are some features that are recognized in the grammar but not fully implemented or have limitations:

### 4.1 Array Limitations
- **Multi-dimensional arrays**: Types like `INT[][]` are not supported
- **Array literals**: Cannot initialize arrays with `{1, 2, 3}` syntax
- **Dynamic arrays**: Arrays must be sized at creation time

### 4.2 Operator Limitations
- **Compound assignment**: `+=`, `-=`, `*=`, `/=` are not supported
- **Increment/decrement**: `++`, `--` operators are not supported
- **Ternary operator**: `condition ? true_value : false_value` is not supported

### 4.3 Control Flow Limitations
- **Break and continue**: No `break` or `continue` statements in loops
- **Switch statements**: No switch-case constructs
- **Do-while loops**: Only while loops are supported

### 4.4 Type System Limitations
- **Generic types**: No parametric polymorphism
- **Type aliases**: Cannot create type aliases
- **Union types**: No sum types or unions

### 4.5 Advanced Features Not Implemented
- **Exception handling**: No try-catch blocks
- **Modules/packages**: No namespace system
- **Inheritance**: Collections cannot inherit from other collections
- **Methods in collections**: Cannot define methods within collection declarations
- **Lambda expressions**: No anonymous functions
- **String operations**: Limited string manipulation beyond concatenation

## 5. Implementation Notes

### 5.1 Parser Implementation
The parser uses recursive descent with 2-token lookahead to handle the grammar. It builds an Abstract Syntax Tree (AST) that represents the program structure.

### 5.2 Semantic Analysis
The semantic analyzer performs:
- Type checking for all expressions
- Scope validation for variables and functions
- Collection field validation
- Function signature checking

### 5.3 Code Generation
The code generator uses the ASM library to emit JVM bytecode, supporting:
- All primitive types and operations
- Collection types as separate classes
- Function calls and returns
- Control flow structures
- Array operations

### 5.4 Built-in Functions
The language provides several built-in functions:
- `println(any)`: Print with newline
- `read_INT()`, `read_FLOAT()`, `read_STRING()`, `read_BOOL()`: Input functions
- `length(array)`: Get array length

## 6. Examples

### 6.1 Complete Program
```
coll Person {
    STRING name;
    INT age;
}

def INT main() {
    Person p = Person("Alice", 30);
    println("Name: " + p.name);
    println("Age: " + p.age);
    return 0;
}
```

### 6.2 Array Usage
```
def main() {
    INT[] numbers = INT ARRAY[5];
    INT i = 0;
    
    while (i < length(numbers)) {
        numbers[i] = i * 2;
        i = i + 1;
    }
    
    println(numbers[2]);  // Prints 4
}
```

### 6.3 Control Flow
```
def BOOLEAN isEven(INT n) {
    if (n % 2 == 0) {
        return true;
    } else {
        return false;
    }
}

def main() {
    INT x = 10;
    for (i; 1 -> x; i + 1) {
        if (isEven(i)) {
            println(i + " is even");
        }
    }
}
```

This grammar provides a solid foundation for a programming language with imperative features, user-defined types, and array support, while maintaining simplicity in the implementation.