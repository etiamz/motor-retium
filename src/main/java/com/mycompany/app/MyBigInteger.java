package com.mycompany.app;

import java.math.BigInteger;

public final class MyBigInteger {
    @SuppressWarnings("serial")
    public static final class OutOfRange extends RuntimeException {
    }

    private final BigInteger value;

    public MyBigInteger(final BigInteger value) {
        this.value = value;
    }

    public BigInteger value() {
        return this.value;
    }

    // FNV-1a, 64-bit.
    public long hash64() {
        long h = 0xCBF29CE484222325L;
        for (final byte b : this.value.toByteArray()) {
            h = (h ^ (b & 0xFF)) * 0x100000001B3L;
        }
        return h;
    }

    public int compareTo(final MyBigInteger other) {
        return this.value.compareTo(other.value);
    }

    public MyBigInteger min(final MyBigInteger other) {
        return new MyBigInteger(this.value.min(other.value));
    }

    public MyBigInteger max(final MyBigInteger other) {
        return new MyBigInteger(this.value.max(other.value));
    }

    public static MyBigInteger zero() {
        return new MyBigInteger(BigInteger.ZERO);
    }

    public static MyBigInteger one() {
        return new MyBigInteger(BigInteger.ONE);
    }

    public static MyBigInteger parse(final String s) {
        final var n = Helpers.numeral(s);
        final BigInteger base = BigInteger.valueOf(n.radix);
        BigInteger value = BigInteger.ZERO;
        for (final char c : n.digits.toCharArray()) {
            final BigInteger digit = BigInteger.valueOf(Helpers.decode(c, n.radix));
            if (n.isNegative) {
                value = value.multiply(base).subtract(digit);
            } else {
                value = value.multiply(base).add(digit);
            }
        }
        return new MyBigInteger(value);
    }

    public static MyBigInteger of(final CheckedInteger.Value value) {
        final long raw = value.a();
        if (value.ty().isSigned || raw >= 0) {
            return new MyBigInteger(BigInteger.valueOf(raw));
        }
        // This is an unsigned integer with a negative two's complement representation; interpret it
        // directly as a byte array.
        final byte[] bytes = new byte[]{ //
                (byte) (raw >> 56), //
                (byte) (raw >> 48), //
                (byte) (raw >> 40), //
                (byte) (raw >> 32), //
                (byte) (raw >> 24), //
                (byte) (raw >> 16), //
                (byte) (raw >> 8), //
                (byte) raw};
        return new MyBigInteger(new BigInteger(1, bytes));
    }

    public CheckedInteger.Value toCheckedInteger(final CheckedInteger.IntegerTy target) {
        final int myBitLength = this.value.bitLength();
        final int targetBitLength = target.isSigned ? target.bits - 1 : target.bits;
        final boolean meNegative = this.value.signum() < 0;
        final boolean signednessFailure = !target.isSigned && meNegative;
        final boolean rangeFailure = myBitLength > targetBitLength;
        if (signednessFailure || rangeFailure) {
            throw new CheckedInteger.OutOfRange(target);
        }
        return target.of(this.value.longValue());
    }

    public String show() {
        return this.value.toString();
    }

    public MyBigInteger negate() {
        return new MyBigInteger(this.value.negate());
    }

    public MyBigInteger signum() {
        return new MyBigInteger(BigInteger.valueOf(this.value.signum()));
    }

    public MyBigInteger abs() {
        return new MyBigInteger(this.value.abs());
    }

    public MyBigInteger add(final MyBigInteger other) {
        return new MyBigInteger(this.value.add(other.value));
    }

    public MyBigInteger subtract(final MyBigInteger other) {
        return new MyBigInteger(this.value.subtract(other.value));
    }

    public MyBigInteger multiply(final MyBigInteger other) {
        return new MyBigInteger(this.value.multiply(other.value));
    }

    public MyBigInteger divide(final MyBigInteger other) {
        ensure(other.value.signum() != 0);
        return new MyBigInteger(this.value.divide(other.value));
    }

    public MyBigInteger remainder(final MyBigInteger other) {
        ensure(other.value.signum() != 0);
        return new MyBigInteger(this.value.remainder(other.value));
    }

    public MyBigInteger not() {
        return new MyBigInteger(this.value.not());
    }

    public boolean at(final int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }
        return this.value.testBit(index);
    }

    public MyBigInteger slice(final int start, final int end) {
        if (start < 0 || start > end) {
            throw new IndexOutOfBoundsException();
        }
        final int width = end - start;
        if (width == 0) {
            return zero();
        }
        final byte[] bytes = new byte[Math.ceilDiv(width, Byte.SIZE)];
        for (int i = 0; i < width; i++) {
            if (this.value.testBit(start + i)) {
                final int j = bytes.length - 1 - i / Byte.SIZE;
                final byte byteMask = (byte) (1 << (i % Byte.SIZE));
                bytes[j] |= byteMask;
            }
        }
        return new MyBigInteger(new BigInteger(1, bytes));
    }

    public MyBigInteger slice(final int start) {
        if (start < 0) {
            throw new IndexOutOfBoundsException();
        }
        return new MyBigInteger(this.value.shiftRight(start));
    }

    public long popcount() {
        return this.value.bitCount();
    }

    public long parity() {
        return this.value.bitCount() % 2;
    }

    public MyBigInteger or(final MyBigInteger other) {
        return new MyBigInteger(this.value.or(other.value));
    }

    public MyBigInteger and(final MyBigInteger other) {
        return new MyBigInteger(this.value.and(other.value));
    }

    public MyBigInteger xor(final MyBigInteger other) {
        return new MyBigInteger(this.value.xor(other.value));
    }

    public MyBigInteger shiftLeft(final MyBigInteger n) {
        final int shift;
        try {
            shift = n.value.intValueExact();
        } catch (final ArithmeticException e) {
            throw new OutOfRange();
        }
        return new MyBigInteger(this.value.shiftLeft(shift));
    }

    public MyBigInteger shiftRight(final MyBigInteger n) {
        final int shift;
        try {
            shift = n.value.intValueExact();
        } catch (final ArithmeticException e) {
            throw new OutOfRange();
        }
        return new MyBigInteger(this.value.shiftRight(shift));
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MyBigInteger m && this.value.equals(m.value);
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }

    private static void ensure(final boolean condition) {
        if (!condition) {
            throw new OutOfRange();
        }
    }

    private static <T> T fail() {
        throw new OutOfRange();
    }

    private static class Helpers {
        private static int decode(final char c, final int radix) {
            if (c >= '0' && c <= '9') {
                return c - '0' < radix ? c - '0' : fail();
            } else if (c >= 'a' && c <= 'z') {
                return c - 'a' + 10 < radix ? c - 'a' + 10 : fail();
            } else if (c >= 'A' && c <= 'Z') {
                return c - 'A' + 10 < radix ? c - 'A' + 10 : fail();
            } else {
                throw new OutOfRange();
            }
        }

        private record Numeral(boolean isNegative, int radix, String digits) {
        }

        private static Numeral numeral(final String s) {
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
            ensure(!digits.isEmpty());
            return new Numeral(negative, radix, digits);
        }
    }
}
