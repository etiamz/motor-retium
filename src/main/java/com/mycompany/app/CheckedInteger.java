package com.mycompany.app;

import java.nio.ByteBuffer;

public final class CheckedInteger {
    @SuppressWarnings("serial")
    public static final class OutOfRange extends RuntimeException {
        public final IntegerTy ty;

        public OutOfRange(final IntegerTy ty) {
            this.ty = ty;
        }
    }

    public enum IntegerTy {
        // @formatter:off
        U8(8, false, 0L, 255L),
        U16(16, false, 0L, 65535L),
        U32(32, false, 0L, 4294967295L),
        U64(64, false, 0L, ~0L),
        I8(8, true, -128L, 127L),
        I16(16, true, -32768L, 32767L),
        I32(32, true, Integer.MIN_VALUE, Integer.MAX_VALUE),
        I64(64, true, Long.MIN_VALUE, Long.MAX_VALUE);
        // @formatter:on

        public final int bits;
        public final boolean isSigned;
        public final long min;
        public final long max;

        private IntegerTy(final int bits, final boolean isSigned, final long min, final long max) {
            this.bits = bits;
            this.isSigned = isSigned;
            this.min = min;
            this.max = max;
        }

        public int compare(final long a, final long b) {
            return isSigned ? Long.compare(a, b) : Long.compareUnsigned(a, b);
        }

        public Value min(final long a, final long b) {
            return this.compare(a, b) <= 0 ? new Value(this, a) : new Value(this, b);
        }

        public Value max(final long a, final long b) {
            return this.compare(a, b) >= 0 ? new Value(this, a) : new Value(this, b);
        }

        public Value of(final long a) {
            this.ensure(isSigned ? a >= min && a <= max : Long.compareUnsigned(a, max) <= 0);
            return new Value(this, a);
        }

        public Value zero() {
            return new Value(this, 0L);
        }

        public Value one() {
            return new Value(this, 1L);
        }

        public Value parse(final String s) {
            final var n = Helpers.numeral(this, s);
            long value = 0;
            for (final char c : n.digits.toCharArray()) {
                final int digit = Helpers.decode(this, c, n.radix);
                final long product = this.multiply(value, n.radix).a;
                if (n.isNegative) {
                    value = this.subtract(product, digit).a;
                } else {
                    value = this.add(product, digit).a;
                }
            }
            return new Value(this, value);
        }

        public int toInt(final long a) {
            if (isSigned) {
                this.ensure(a >= Integer.MIN_VALUE && a <= Integer.MAX_VALUE);
                return (int) a;
            }
            this.ensure(Long.compareUnsigned(a, Integer.MAX_VALUE) <= 0);
            return (int) a;
        }

        public String show(final long a) {
            return isSigned ? Long.toString(a) : Long.toUnsignedString(a);
        }

        public Value negate(final long a) {
            return this.subtract(0, a);
        }

        public Value add(final long a, final long b) {
            final boolean overflow = this.compare(b, 0) >= 0 && this.compare(a, max - b) > 0;
            final boolean underflow = this.compare(b, 0) < 0 && this.compare(a, min - b) < 0;
            this.ensure(!overflow && !underflow);
            return new Value(this, a + b);
        }

        public Value subtract(final long a, final long b) {
            final boolean overflow = this.compare(b, 0) < 0 && this.compare(a, max + b) > 0;
            final boolean underflow = this.compare(b, 0) >= 0 && this.compare(a, min + b) < 0;
            this.ensure(!overflow && !underflow);
            return new Value(this, a - b);
        }

        public Value multiply(final long a, final long b) {
            final boolean overflow = (this.compare(a, 0) > 0 && this.compare(b, 0) > 0
                    && this.compare(a, this.divideUnchecked(max, b)) > 0)
                    || (this.compare(a, 0) < 0 && this.compare(b, 0) < 0
                            && this.compare(a, this.divideUnchecked(max, b)) < 0);
            final boolean underflow = (this.compare(a, 0) < 0 && this.compare(b, 0) > 0
                    && this.compare(a, this.divideUnchecked(min, b)) < 0)
                    || (this.compare(a, 0) > 0 && isSigned && this.compare(b, -1L) < 0
                            && this.compare(a, this.divideUnchecked(min, b)) > 0);
            this.ensure(!overflow && !underflow);
            return new Value(this, a * b);
        }

        public Value divide(final long a, final long b) {
            this.ensure(b != 0);
            final boolean overflow = isSigned && a == min && b == -1L;
            this.ensure(!overflow);
            return new Value(this, this.divideUnchecked(a, b));
        }

        public Value remainder(final long a, final long b) {
            this.ensure(b != 0);
            final boolean overflow = isSigned && a == min && b == -1L;
            this.ensure(!overflow);
            return new Value(this, this.remainderUnchecked(a, b));
        }

        public Value not(final long a) {
            return new Value(this, this.normalize(~a));
        }

        public Value ffs(final long a) {
            final long raw = a & this.mask();
            return raw == 0
                    ? new Value(this, 0)
                    : new Value(this, Long.numberOfTrailingZeros(raw) + 1);
        }

        public Value clz(final long a) {
            final long raw = a & this.mask();
            this.ensure(raw != 0);
            return new Value(this, Long.numberOfLeadingZeros(raw) - (64 - bits));
        }

        public Value ctz(final long a) {
            final long raw = a & this.mask();
            this.ensure(raw != 0);
            return new Value(this, Long.numberOfTrailingZeros(raw));
        }

        public Value clrsb(final long a) {
            final long raw = a & this.mask();
            final long msb = (raw >>> (bits - 1)) & 1;
            long count = 0;
            for (int i = bits - 2; i >= 0; i--) {
                if (((raw >>> i) & 1) != msb) {
                    break;
                }
                count++;
            }
            return new Value(this, count);
        }

        public Value popcount(final long a) {
            return new Value(this, Long.bitCount(a & this.mask()));
        }

        public Value parity(final long a) {
            return new Value(this, Long.bitCount(a & this.mask()) % 2);
        }

        public Value or(final long a, final long b) {
            return new Value(this, a | b);
        }

        public Value and(final long a, final long b) {
            return new Value(this, a & b);
        }

        public Value xor(final long a, final long b) {
            return new Value(this, a ^ b);
        }

        public Value shiftLeft(final long a, final long b) {
            if (isSigned) {
                this.ensure(b >= 0 && b < bits);
            } else {
                this.ensure(Long.compareUnsigned(b, bits) < 0);
            }
            return new Value(this, this.normalize(a << b));
        }

        public Value shiftRight(final long a, final long b) {
            if (isSigned) {
                this.ensure(b >= 0 && b < bits);
                return new Value(this, a >> b);
            }
            this.ensure(Long.compareUnsigned(b, bits) < 0);
            return new Value(this, a >>> b);
        }

        private long divideUnchecked(final long a, final long b) {
            return isSigned ? a / b : Long.divideUnsigned(a, b);
        }

        private long remainderUnchecked(final long a, final long b) {
            return isSigned ? a % b : Long.remainderUnsigned(a, b);
        }

        private long normalize(final long a) {
            if (isSigned) {
                final int shift = 64 - bits;
                return (a << shift) >> shift;
            }
            return a & this.mask();
        }

        private long mask() {
            return -1L >>> (64 - bits);
        }

        private void ensure(final boolean condition) {
            if (!condition) {
                throw new OutOfRange(this);
            }
        }

        private <T> T fail() {
            throw new OutOfRange(this);
        }
    }

    public record Value(IntegerTy ty, long a) {
        public String show() {
            return ty.show(a);
        }

        // FNV-1a, 64-bit.
        public long hash64() {
            final byte[] bytes = ByteBuffer.allocate(8).putLong(this.a).array();
            long h = 0xCBF29CE484222325L;
            for (final byte b : bytes) {
                h = (h ^ (b & 0xFF)) * 0x100000001B3L;
            }
            return h;
        }

        public int toInt() {
            return ty.toInt(a);
        }

        public Value negate() {
            return ty.negate(a);
        }

        public Value not() {
            return ty.not(a);
        }

        public Value ffs() {
            return ty.ffs(a);
        }

        public Value clz() {
            return ty.clz(a);
        }

        public Value ctz() {
            return ty.ctz(a);
        }

        public Value clrsb() {
            return ty.clrsb(a);
        }

        public Value popcount() {
            return ty.popcount(a);
        }

        public Value parity() {
            return ty.parity(a);
        }

        public Value convertTo(final IntegerTy target) {
            final boolean fitsLowerBound = !ty.isSigned || a >= target.min;
            final boolean fitsUpperBound = ty.isSigned && target.isSigned
                    ? a <= target.max
                    : Long.compareUnsigned(a, target.max) <= 0;
            target.ensure(fitsLowerBound && fitsUpperBound);
            return new Value(target, a);
        }
    }

    private static class Helpers {
        private static int decode(final IntegerTy ty, final char c, final int radix) {
            if (c >= '0' && c <= '9') {
                return c - '0' < radix ? c - '0' : ty.fail();
            } else if (c >= 'a' && c <= 'z') {
                return c - 'a' + 10 < radix ? c - 'a' + 10 : ty.fail();
            } else if (c >= 'A' && c <= 'Z') {
                return c - 'A' + 10 < radix ? c - 'A' + 10 : ty.fail();
            } else {
                throw new OutOfRange(ty);
            }
        }

        private record Numeral(boolean isNegative, int radix, String digits) {
        }

        private static Numeral numeral(final IntegerTy ty, final String s) {
            final boolean negative = s.startsWith("-");
            final String body = negative ? s.substring(1) : s;
            final int radix, offset;
            if (body.startsWith("0b") || body.startsWith("0B")) {
                radix = 2;
                offset = 2;
            } else if (body.startsWith("0o") || body.startsWith("0O")) {
                radix = 8;
                offset = 2;
            } else if (body.startsWith("0x") || body.startsWith("0X")) {
                radix = 16;
                offset = 2;
            } else {
                radix = 10;
                offset = 0;
            }
            final String digits = body.substring(offset);
            ty.ensure(!digits.isEmpty());
            return new Numeral(negative, radix, digits);
        }
    }
}
