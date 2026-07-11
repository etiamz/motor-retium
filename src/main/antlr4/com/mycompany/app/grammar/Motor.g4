grammar Motor;

program
    : term (WHERE (definition | constructorDeclaration)+)? EOF
    ;

definition
    : REFERENCE SYMBOL* ASSIGN term SEMICOLON
    ;

constructorDeclaration
    : CONSTRUCTOR COLON INTEGER SEMICOLON
    ;

term
    : BACKSLASH SYMBOL+ RIGHT_ARROW term # lambdaTerm
    | LET SYMBOL ASSIGN term SEMICOLON term # letTerm
    | LET EXCLAMATION SYMBOL ASSIGN term SEMICOLON term # strictLetTerm
    | LET CONSTRUCTOR SYMBOL* ASSIGN term SEMICOLON term # destructuringLetTerm
    | IF term THEN term ELSE term # ifThenElseTerm
    | CASE term OF LEFT_BRACE case (SEMICOLON case)* RIGHT_BRACE # caseTerm
    | application # applicationTerm
    ;

case
    : CONSTRUCTOR SYMBOL* (INTEGER_OR term)? RIGHT_ARROW term
    ;

application
    : application atom # applyTerm
    | application EXCLAMATION atom # strictApplyTerm
    | atom # atomTerm
    ;

atom
    : LEFT_PARENTHESIS FIX term RIGHT_PARENTHESIS # fixTerm
    | LEFT_PARENTHESIS term DOT_DOT term RIGHT_PARENTHESIS # rangeTerm
    | LEFT_PARENTHESIS term DOT_DOT RIGHT_PARENTHESIS # rangeFromTerm
    | LEFT_PARENTHESIS DOT_DOT term RIGHT_PARENTHESIS # rangeToTerm
    | LEFT_PARENTHESIS DOT_DOT RIGHT_PARENTHESIS # rangeFullTerm
    | LEFT_PARENTHESIS application op2 application RIGHT_PARENTHESIS # infixTerm
    | LEFT_PARENTHESIS op2 RIGHT_PARENTHESIS # operatorTerm
    | LEFT_PARENTHESIS term RIGHT_PARENTHESIS # groupTerm
    | op1 # op1Term
    | intrinsic # intrinsicTerm
    | CONSTRUCTOR # constructorTerm
    | TRUE # trueTerm
    | FALSE # falseTerm
    | INTEGER COLON INTEGER_TY # integerTerm
    | INTEGER COLON BIGINT_TY # bigIntegerTerm
    | CHARACTER # characterTerm
    | STRING # stringTerm
    | SYMBOL # variableTerm
    | REFERENCE # referenceTerm
    ;

op1
    : NOT
    | INTEGER_TY
    | BIGINT_TY
    | STRING_TY
    | STRING_OF_CHARACTER
    | NEGATE
    | INTEGER_NOT
    ;

op2
    : AND
    | OR
    | ADD
    | SUBTRACT
    | MULTIPLY
    | DIVIDE
    | REMAINDER
    | INTEGER_OR
    | INTEGER_AND
    | INTEGER_XOR
    | SHIFT_LEFT
    | SHIFT_RIGHT
    | EQUALS
    | NOT_EQUALS
    | LESS
    | LESS_OR_EQUALS
    | GREATER
    | GREATER_OR_EQUALS
    | CHARACTER_AT
    | SLICE
    | PLUS_PLUS
    ;

intrinsic
    : intrinsic1
    | intrinsic2
    ;

intrinsic1
    : FFS
    | CLZ
    | CTZ
    | CLRSB
    | POPCOUNT
    | PARITY
    | STRLEN
    | PANIC
    | MEMORY
    | HASH
    ;

intrinsic2
    : MIN
    | MAX
    | STRCMP
    | STRCHR
    | STRRCHR
    | STRSTR
    | STRRSTR
    | STRSPN
    | STRCSPN
    | STRPBRK
    | STRRSPN
    | STRRCSPN
    | STRRPBRK
    | STARTSWITH
    | ENDSWITH
    | REMEMBER
    ;

// Punctuation.
LEFT_PARENTHESIS : '(' ;
RIGHT_PARENTHESIS : ')' ;
LEFT_BRACE : '{' ;
RIGHT_BRACE : '}' ;
BACKSLASH : '\\' ;
COMMA : ',' ;
RIGHT_ARROW : '->' ;
ASSIGN : ':=' ;
DOT_DOT : '..' ;
COLON : ':' ;
SEMICOLON : ';' ;
EXCLAMATION : '!' ;

// Logical operators.
NOT : 'not' ;
AND : '&&' ;
OR : '||' ;

// Mixed-type operators.
STRING_OF_CHARACTER : '#' ;
NEGATE : 'negate' ;
INTEGER_NOT : '~' ;
FFS : '$ffs' ;
CLZ : '$clz' ;
CTZ : '$ctz' ;
CLRSB : '$clrsb' ;
POPCOUNT : '$popcount' ;
PARITY : '$parity' ;
ADD : '+' ;
SUBTRACT : '-' ;
MULTIPLY : '*' ;
DIVIDE : '/' ;
REMAINDER : '%' ;
INTEGER_OR : '|' ;
INTEGER_AND : '&' ;
INTEGER_XOR : '^' ;
SHIFT_LEFT : '<<' ;
SHIFT_RIGHT : '>>' ;
EQUALS : '=' ;
NOT_EQUALS : '!=' ;
LESS : '<' ;
LESS_OR_EQUALS : '<=' ;
GREATER : '>' ;
GREATER_OR_EQUALS : '>=' ;
MIN : '$min' ;
MAX : '$max' ;

// String operators.
STRLEN : '$strlen' ;
PANIC : '$panic' ;
CHARACTER_AT : '@' ;
SLICE : '@@' ;
PLUS_PLUS : '++' ;
STRCMP : '$strcmp' ;
STRCHR : '$strchr' ;
STRRCHR : '$strrchr' ;
STRSTR : '$strstr' ;
STRRSTR : '$strrstr' ;
STRSPN : '$strspn' ;
STRCSPN : '$strcspn' ;
STRPBRK : '$strpbrk' ;
STRRSPN : '$strrspn' ;
STRRCSPN : '$strrcspn' ;
STRRPBRK : '$strrpbrk' ;
STARTSWITH : '$startswith' ;
ENDSWITH : '$endswith' ;

// Memory operators.
MEMORY : '$memory' ;
HASH : '$hash' ;
REMEMBER : '$remember' ;

// Keywords.
IF : 'if' ; THEN : 'then' ; ELSE : 'else' ;
CASE : 'case' ; OF : 'of' ;
FIX : 'fix' ;
LET : 'let' ;
WHERE : 'where' ;

// Types.
INTEGER_TY : IntegerTy ;
BIGINT_TY : 'bigint' ;
STRING_TY : 'string' ;

// Literals.
TRUE : 'true' ;
FALSE : 'false' ;
INTEGER : '-'? UnsignedInteger ;
// Permit onely printable ASCII code points in characters and strings.
CHARACTER : '\'' ( Escape | ~[\u{0}-\u{1F}'\\\u{7F}-\u{10FFFF}] ) '\'' ;
STRING : '"' ( Escape | ~[\u{0}-\u{1F}"\\\u{7F}-\u{10FFFF}] )* '"' ;

// Identifiers.
SYMBOL : ('_' | [a-z]) [a-zA-Z0-9]* '\''* ;
REFERENCE : '&' '_'? [a-zA-Z] [a-zA-Z0-9]* '\''* ;
CONSTRUCTOR : [A-Z] [a-zA-Z0-9]* '\''* ;

// Skipped.
COMMENT : '//' ~[\r\n]* -> skip ;
WHITESPACE : [ \t\r\n]+ -> skip ;

fragment IntegerTy : 'u8' | 'u16' | 'u32' | 'u64' | 'i8' | 'i16' | 'i32' | 'i64' ;

fragment UnsignedInteger : Binary | Octal | Hexadecimal | Decimal ;
fragment Binary : ('0b' | '0B') [01]+ ;
fragment Octal : ('0o' | '0O') [0-7]+ ;
fragment Hexadecimal : ('0x' | '0X') Hex+ ;
fragment Decimal : [0-9]+ ;
fragment Hex : [0-9a-fA-F] ;

fragment Escape : '\\' ( [fnrtv\\'"] | 'x' Hex Hex ) ;
