package com.mycompany.app;

import static com.mycompany.app.CheckedInteger.IntegerTy.*;

import com.mycompany.app.CheckedInteger.IntegerTy;
import java.math.BigInteger;

public final class MyBigInteger {
    @SuppressWarnings("serial")
    public static final class OutOfRange extends RuntimeException {
    }

    private final BigInteger value;

    public MyBigInteger(final BigInteger value) {
        this.value = value;
    }

    public MyBigInteger(final byte[] val) {
        this.value = new BigInteger(val);
    }

    public MyBigInteger(final byte[] val, final int off, final int len) {
        this.value = new BigInteger(val, off, len);
    }

    public MyBigInteger(final int signum, final byte[] magnitude) {
        this.value = new BigInteger(signum, magnitude);
    }

    public MyBigInteger(final int signum, final byte[] magnitude, final int off, final int len) {
        this.value = new BigInteger(signum, magnitude, off, len);
    }

    public BigInteger value() {
        return this.value;
    }

    public int bitLength() {
        return this.value.bitLength();
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
        final var n = Parsing.numeral(s);
        final BigInteger base = BigInteger.valueOf(n.radix);
        BigInteger value = BigInteger.ZERO;
        for (final char c : n.digits.toCharArray()) {
            final BigInteger digit = BigInteger.valueOf(Parsing.decode(c, n.radix));
            if (n.isNegative) {
                value = value.multiply(base).subtract(digit);
            } else {
                value = value.multiply(base).add(digit);
            }
        }
        return new MyBigInteger(value);
    }

    public static MyBigInteger of(final CheckedInteger.Value value) {
        return value.ty().isSigned
                ? new MyBigInteger(value.toByteArray())
                : new MyBigInteger(1, value.toByteArray());
    }

    public CheckedInteger.Value convertTo(final IntegerTy target) {
        if ((!target.isSigned && this.value.signum() < 0)
                || (this.bitLength() > target.bitLength())) {
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
        return new MyBigInteger(1, bytes);
    }

    public MyBigInteger slice(final int start) {
        if (start < 0) {
            throw new IndexOutOfBoundsException();
        }
        return new MyBigInteger(this.value.shiftRight(start));
    }

    public MyBigInteger prependPacked8(final long element) {
        return new MyBigInteger(prependPacked(this.value, U8.of(element)));
    }

    public MyBigInteger prependPacked16(final long element) {
        return new MyBigInteger(prependPacked(this.value, U16.of(element)));
    }

    public MyBigInteger prependPacked32(final long element) {
        return new MyBigInteger(prependPacked(this.value, U32.of(element)));
    }

    public MyBigInteger prependPacked64(final long element) {
        return new MyBigInteger(prependPacked(this.value, U64.of(element)));
    }

    public long readPacked8(final long index) {
        return readPacked(this.value, index, U8);
    }

    public long readPacked16(final long index) {
        return readPacked(this.value, index, U16);
    }

    public long readPacked32(final long index) {
        return readPacked(this.value, index, U32);
    }

    public long readPacked64(final long index) {
        return readPacked(this.value, index, U64);
    }

    public long findPacked8(final long element) {
        return findPacked(this.value, U8.of(element));
    }

    public long findPacked16(final long element) {
        return findPacked(this.value, U16.of(element));
    }

    public long findPacked32(final long element) {
        return findPacked(this.value, U32.of(element));
    }

    public long findPacked64(final long element) {
        return findPacked(this.value, U64.of(element));
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

    private static BigInteger prependPacked(
            final BigInteger array,
            final CheckedInteger.Value element) {
        assert !element.ty().isSigned;
        ensure(array.signum() >= 0);
        final int nbits = element.ty().bits;
        return array.shiftLeft(nbits).or(new BigInteger(1, element.toByteArray()));
    }

    private static long readPacked(final BigInteger array, final long index, final IntegerTy ty) {
        assert !ty.isSigned;
        ensure(array.signum() >= 0);
        final int nbits = ty.bits;
        final int length = Math.ceilDiv(array.bitLength(), nbits);
        ensure(Long.compareUnsigned(index, length) < 0);
        final int start = (int) index * nbits;
        long value = 0;
        for (int i = 0; i < nbits; i++) {
            if (array.testBit(start + i)) {
                value |= 1L << i;
            }
        }
        return value;
    }

    private static long findPacked(final BigInteger array, final CheckedInteger.Value element) {
        assert !element.ty().isSigned;
        ensure(array.signum() >= 0);
        final int nbits = element.ty().bits;
        final BigInteger a = BigInteger.valueOf(element.a());
        // `i += nbits` can overflow, so we use `long` here.
        for (long i = 0; i < array.bitLength(); i += nbits) {
            boolean matches = true;
            for (int j = 0; j < nbits; j++) {
                if (array.testBit((int) i + j) != a.testBit(j)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i / nbits;
            }
        }
        return -1;
    }

    private static class Parsing {
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
