package com.mycompany.app;

import com.mycompany.app.CheckedInteger.IntegerTy;

public final class Primitives {
    public sealed interface Operator permits Not, And, Or, Fix, StrictOp1, StrictOp2 {
        public int arity();
    }

    public record Fix() implements Operator {
        @Override
        public int arity() {
            return 1;
        }
    }

    public record Not() implements Operator {
        @Override
        public int arity() {
            return 1;
        }
    }

    public record And() implements Operator {
        @Override
        public int arity() {
            return 2;
        }
    }

    public record Or() implements Operator {
        @Override
        public int arity() {
            return 2;
        }
    }

    public sealed interface StrictOp1 extends Operator
            permits IntegerOf, BigIntegerOf, StringOf, StringOfCharacter, Negate, IntegerNot, Ffs,
            Clz, Ctz, Clrsb, Popcount, Parity, Strlen, Panic, Memory, Hash {
        public String describe();

        @Override
        public default int arity() {
            return 1;
        }
    }

    public record IntegerOf(IntegerTy target) implements StrictOp1 {
        public String describe() {
            return "casting to " + Primitives.describe(target);
        }
    }

    public record BigIntegerOf() implements StrictOp1 {
        public String describe() {
            return "casting to a big integer";
        }
    }

    public record StringOf() implements StrictOp1 {
        public String describe() {
            return "integer-to-string conversion";
        }
    }

    public record StringOfCharacter() implements StrictOp1 {
        public String describe() {
            return "character-to-string conversion";
        }
    }

    public record Negate() implements StrictOp1 {
        public String describe() {
            return "negation";
        }
    }

    public record IntegerNot() implements StrictOp1 {
        public String describe() {
            return "bitwise negation";
        }
    }

    public record Ffs() implements StrictOp1 {
        public String describe() {
            return "find first set";
        }
    }

    public record Clz() implements StrictOp1 {
        public String describe() {
            return "leading-zero count";
        }
    }

    public record Ctz() implements StrictOp1 {
        public String describe() {
            return "trailing-zero count";
        }
    }

    public record Clrsb() implements StrictOp1 {
        public String describe() {
            return "leading-redundant-sign-bit count";
        }
    }

    public record Popcount() implements StrictOp1 {
        public String describe() {
            return "population count";
        }
    }

    public record Parity() implements StrictOp1 {
        public String describe() {
            return "parity";
        }
    }

    public record Strlen() implements StrictOp1 {
        public String describe() {
            return "length computation";
        }
    }

    public record Panic() implements StrictOp1 {
        public String describe() {
            return "panicking";
        }
    }

    public record Memory() implements StrictOp1 {
        public String describe() {
            return "creation of a memory";
        }
    }

    public record Hash() implements StrictOp1 {
        public String describe() {
            return "hashing";
        }
    }

    public enum StrictOp2 implements Operator {
        // @formatter:off
        ADD("addition"),
        SUBTRACT("subtraction"),
        MULTIPLY("multiplication"),
        DIVIDE("division"),
        REMAINDER("remainder"),
        INTEGER_OR("bitwise disjunction"),
        INTEGER_AND("bitwise conjunction"),
        INTEGER_XOR("bitwise exclusive disjunction"),
        SHIFT_LEFT("left shift"),
        SHIFT_RIGHT("right shift"),
        EQUALS("equals"),
        NOT_EQUALS("not equals"),
        LESS("less-than"),
        LESS_OR_EQUALS("less-than-or-equals"),
        GREATER("greater-than"),
        GREATER_OR_EQUALS("greater-than-or-equals"),
        MIN("minimum"),
        MAX("maximum"),
        CHARACTER_AT("character access"),
        SLICE("slicing"),
        PLUS_PLUS("concatenation"),
        STRCMP("three-way string comparison"),
        STRCHR("character search"),
        STRRCHR("reverse character search"),
        STRSTR("substring search"),
        STRRSTR("reverse substring search"),
        STRSPN("byte-set span"),
        STRCSPN("byte-set complement span"),
        STRPBRK("byte-set search"),
        STRRSPN("reverse byte-set span"),
        STRRCSPN("reverse byte-set complement span"),
        STRRPBRK("reverse byte-set search"),
        STARTSWITH("prefix check"),
        ENDSWITH("suffix check"),
        REMEMBER("remembering");
        // @formatter:on

        private final String description;

        private StrictOp2(final String description) {
            this.description = description;
        }

        public String describe() {
            return description;
        }

        @Override
        public int arity() {
            return 2;
        }

        public boolean isComparison() {
            return switch (this) {
                case EQUALS, NOT_EQUALS, LESS, LESS_OR_EQUALS, GREATER, GREATER_OR_EQUALS -> true;
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, INTEGER_OR, INTEGER_AND,
                        INTEGER_XOR, SHIFT_LEFT, SHIFT_RIGHT, MIN, MAX, CHARACTER_AT, SLICE,
                        PLUS_PLUS, STRCMP, STRCHR, STRRCHR, STRSTR, STRRSTR, STRSPN, STRCSPN,
                        STRPBRK, STRRSPN, STRRCSPN, STRRPBRK, STARTSWITH, ENDSWITH, REMEMBER ->
                    false;
            };
        }
    }

    public static String describe(final IntegerTy ty) {
        return switch (ty) {
            case U8 -> "an unsigned 8-bit integer";
            case U16 -> "an unsigned 16-bit integer";
            case U32 -> "an unsigned 32-bit integer";
            case U64 -> "an unsigned 64-bit integer";
            case I8 -> "a signed 8-bit integer";
            case I16 -> "a signed 16-bit integer";
            case I32 -> "a signed 32-bit integer";
            case I64 -> "a signed 64-bit integer";
        };
    }

    public static String escapeByte(final int b) {
        return switch (b) {
            case '\\' -> "\\\\";
            case '"' -> "\\\"";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case 0x0B -> "\\v";
            default ->
                b >= 0x20 && b < 0x7f ? String.valueOf((char) b) : String.format("\\x%02X", b);
        };
    }
}
