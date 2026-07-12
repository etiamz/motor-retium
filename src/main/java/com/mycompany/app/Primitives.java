package com.mycompany.app;

import com.mycompany.app.CheckedInteger.IntegerTy;

public final class Primitives {
    public sealed interface Operator
            permits Not, And, Or, Fix, Apply, StrictApply, StrictOp1, StrictOp2 {
        public int arity();
    }

    public record Apply() implements Operator {
        @Override
        public int arity() {
            return 2;
        }
    }

    public record StrictApply() implements Operator {
        @Override
        public int arity() {
            return 2;
        }
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

    public enum StrictOp1 implements Operator {
        // @formatter:off
        STRING_OF("integer-to-string conversion"),
        STRING_OF_CHARACTER("character-to-string conversion"),
        NEGATE("negation"),
        FFS("find first set"),
        CLZ("leading-zero count"),
        CTZ("trailing-zero count"),
        CLRSB("leading-redundant-sign-bit count"),
        POPCOUNT("population count"),
        PARITY("parity"),
        STRLEN("length computation"),
        PANIC("panicking"),
        HASH("hashing"),
        MEMORY("creation of a memory");
        // @formatter:on

        private final String description;

        private StrictOp1(final String description) {
            this.description = description;
        }

        public String describe() {
            return description;
        }

        @Override
        public int arity() {
            return 1;
        }
    }

    public enum StrictOp2 implements Operator {
        // @formatter:off
        ADD("addition"),
        SUBTRACT("subtraction"),
        MULTIPLY("multiplication"),
        DIVIDE("division"),
        REMAINDER("remainder"),
        STRICT_OR("strict disjunction"),
        STRICT_AND("strict conjunction"),
        STRICT_XOR("strict exclusive disjunction"),
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
        OFTYPE("type conversion"),
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
                case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, STRICT_OR, STRICT_AND, STRICT_XOR,
                        SHIFT_LEFT, SHIFT_RIGHT, MIN, MAX, OFTYPE, CHARACTER_AT, SLICE, PLUS_PLUS,
                        STRCMP, STRCHR, STRRCHR, STRSTR, STRRSTR, STRSPN, STRCSPN, STRPBRK, STRRSPN,
                        STRRCSPN, STRRPBRK, STARTSWITH, ENDSWITH, REMEMBER ->
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
