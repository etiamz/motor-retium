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
    : term op=('@' | '@@') term # indexingTerm
    | term op=('*' | '/' | '%') term # multiplicativeTerm
    | term op=('+' | '-') term # additiveTerm
    | term op=('<<' | '>>') term # shiftTerm
    | term op='&' term # strictAndTerm
    | term op='^' term # strictXorTerm
    | term op='|' term # strictOrTerm
    | <assoc=right> term op='++' term # concatenationTerm
    | term op=('==' | '!=' | '<' | '<=' | '>' | '>=') term # comparisonTerm
    | term op='&&' term # conjunctionTerm
    | term op='||' term # disjunctionTerm
    | term '..' term # rangeTerm
    | term '..=' term # inclusiveRangeTerm
    | term '..' # rangeFromTerm
    | '..' term # rangeToTerm
    | '..=' term # inclusiveRangeToTerm
    | '..' # rangeFullTerm
    | <assoc=right> term op=('$' | '$!') term # applyOpTerm
    | '\\' SYMBOL+ '->' term # lambdaTerm
    | 'let' SYMBOL '=' term 'in' term # letTerm
    | 'let' '!' SYMBOL '=' term 'in' term # strictLetTerm
    | 'let' CONSTRUCTOR SYMBOL* '=' term 'in' term # patternLetTerm
    | 'if' term 'then' term 'else' term # ifThenElseTerm
    | 'case' term 'of' '{' case (';' case)* '}' # caseTerm
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
    : '(' op2 ')' # operatorTerm
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
    : 'fix' | 'not' | 'negate'
    ;

op2
    : '&&' | '||' | '+' | '-' | '*' | '/' | '%' | '|' | '&' | '^' | '<<' | '>>' | '==' | '!=' | '<' | '<=' | '>' | '>=' | '@' | '@@' | '++' | '$' | '$!'
    ;

intrinsic
    : intrinsic1
    | intrinsic2
    ;

intrinsic1
    : '$show' | '$chr' | '$ffs' | '$clz' | '$ctz' | '$clrsb' | '$popcount' | '$parity' | '$strlen' | '$panic' | '$hash' | '$memory'
    ;

intrinsic2
    : '$min' | '$max' | '$oftype' | '$strcmp' | '$strchr' | '$strrchr' | '$strstr' | '$strrstr' | '$strspn' | '$strcspn' | '$strpbrk' | '$strrspn' | '$strrcspn' | '$strrpbrk' | '$startswith' | '$endswith' | '$remember'
    ;

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
