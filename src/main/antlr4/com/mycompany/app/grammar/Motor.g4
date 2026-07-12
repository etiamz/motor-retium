grammar Motor;

program
    : constructorDeclaration* definition+ EOF
    ;

constructorDeclaration
    : CONSTRUCTOR ':' INTEGER ';'
    ;

definition
    : SYMBOL SYMBOL* '=' term ';'
    ;

term
    : '\\' SYMBOL+ '->' term # lambdaTerm
    | 'let' SYMBOL '=' term 'in' term # letTerm
    | 'let' '!' SYMBOL '=' term 'in' term # strictLetTerm
    | 'let' CONSTRUCTOR SYMBOL* '=' term 'in' term # destructuringLetTerm
    | 'if' term 'then' term 'else' term # ifThenElseTerm
    | 'case' term 'of' '{' case (';' case)* '}' # caseTerm
    | application '$' term # nonStrictApplyTerm
    | application '$!' term # strictApplyTerm
    | left=application? '..' right=application? # rangeTerm
    | left=application? '..=' right=application # inclusiveRangeTerm
    | application # applicationTerm
    ;

case
    : CONSTRUCTOR SYMBOL* ('|' term (',' term)*)? '->' term
    ;

application
    : application atom # applyTerm
    | atom # atomTerm
    ;

atom
    : '(' application op2 application ')' # infixTerm
    | '(' op2 ')' # operatorTerm
    | '(' term ')' # groupTerm
    | op1 # op1Term
    | intrinsic # intrinsicTerm
    | CONSTRUCTOR # constructorTerm
    | TRUE # trueTerm
    | FALSE # falseTerm
    | INTEGER_LITERAL # integerTerm
    | BIG_INTEGER_LITERAL # bigIntegerTerm
    | CHARACTER # characterTerm
    | STRING # stringTerm
    | SYMBOL # variableTerm
    ;

op1
    : 'fix' | 'not' | 'negate' | INTEGER_TY | BIG_INTEGER_TY | STRING_TY | '#'
    ;

op2
    : '&&' | '||' | '+' | '-' | '*' | '/' | '%' | '|' | '&' | '^' | '<<' | '>>' | '==' | '!=' | '<' | '<=' | '>' | '>=' | '@' | '@@' | '++'
    ;

intrinsic
    : intrinsic1
    | intrinsic2
    ;

intrinsic1
    : '$ffs' | '$clz' | '$ctz' | '$clrsb' | '$popcount' | '$parity' | '$strlen' | '$panic' | '$memory' | '$hash'
    ;

intrinsic2
    : '$min' | '$max' | '$strcmp' | '$strchr' | '$strrchr' | '$strstr' | '$strrstr' | '$strspn' | '$strcspn' | '$strpbrk' | '$strrspn' | '$strrcspn' | '$strrpbrk' | '$startswith' | '$endswith' | '$remember'
    ;

// Types.
INTEGER_TY : IntegerTy ;
BIG_INTEGER_TY : 'bigint' ;
STRING_TY : 'string' ;

// Literals.
TRUE : 'true' ;
FALSE : 'false' ;
INTEGER_LITERAL : '-'? UnsignedInteger IntegerTy ;
BIG_INTEGER_LITERAL : '-'? UnsignedInteger 'bigint' ;
INTEGER : '-'? UnsignedInteger ;
// Permit onely printable ASCII code points in characters and strings.
CHARACTER : '\'' ( Escape | ~[\u{0}-\u{1F}'\\\u{7F}-\u{10FFFF}] ) '\'' ;
STRING : '"' ( Escape | ~[\u{0}-\u{1F}"\\\u{7F}-\u{10FFFF}] )* '"' ;

// Identifiers.
SYMBOL : ('_' | [a-z]) [a-zA-Z0-9_']* ;
CONSTRUCTOR : [A-Z] [a-zA-Z0-9_']* ;

// Skipped.
COMMENT : '--' ~[\r\n]* -> skip ;
WHITESPACE : [ \t\r\n]+ -> skip ;

fragment IntegerTy : 'u8' | 'u16' | 'u32' | 'u64' | 'i8' | 'i16' | 'i32' | 'i64' ;

fragment UnsignedInteger : Binary | Octal | Hexadecimal | Decimal ;
fragment Binary : ('0b' | '0B') [01]+ ;
fragment Octal : ('0o' | '0O') [0-7]+ ;
fragment Hexadecimal : ('0x' | '0X') Hex+ ;
fragment Decimal : [0-9]+ ;
fragment Hex : [0-9a-fA-F] ;

fragment Escape : '\\' ( [fnrtv\\'"] | 'x' Hex Hex ) ;
