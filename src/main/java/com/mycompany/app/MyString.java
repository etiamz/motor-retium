package com.mycompany.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MyString {
    private final byte[] data;
    private final int offset;
    private final int length;

    public MyString(final byte[] data) {
        this(data, 0, data.length);
    }

    public MyString(final byte[] data, final int offset, final int length) {
        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    public static MyString ofByte(final int value) {
        return new MyString(new byte[]{(byte) value});
    }

    public static MyString ofAscii(final String s) {
        return new MyString(s.getBytes(StandardCharsets.US_ASCII));
    }

    public int length() {
        return this.length;
    }

    // FNV-1a, 64-bit.
    public long hash64() {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < this.length; i++) {
            h = (h ^ (this.data[this.offset + i] & 0xFF)) * 0x100000001B3L;
        }
        return h;
    }

    public MyString concat(final MyString other) {
        final byte[] buffer = new byte[this.length + other.length];
        System.arraycopy(this.data, this.offset, buffer, 0, this.length);
        System.arraycopy(other.data, other.offset, buffer, this.length, other.length);
        return new MyString(buffer);
    }

    public MyString prependPacked8(final long element) {
        return Helpers.prependPacked(this, element, 8);
    }

    public MyString prependPacked16(final long element) {
        return Helpers.prependPacked(this, element, 16);
    }

    public MyString prependPacked32(final long element) {
        return Helpers.prependPacked(this, element, 32);
    }

    public MyString prependPacked64(final long element) {
        return Helpers.prependPacked(this, element, 64);
    }

    public MyString appendPacked8(final long element) {
        return Helpers.appendPacked(this, element, 8);
    }

    public MyString appendPacked16(final long element) {
        return Helpers.appendPacked(this, element, 16);
    }

    public MyString appendPacked32(final long element) {
        return Helpers.appendPacked(this, element, 32);
    }

    public MyString appendPacked64(final long element) {
        return Helpers.appendPacked(this, element, 64);
    }

    public long readPacked8(final long index) {
        return Helpers.readPacked(this, index, 8);
    }

    public long readPacked16(final long index) {
        return Helpers.readPacked(this, index, 16);
    }

    public long readPacked32(final long index) {
        return Helpers.readPacked(this, index, 32);
    }

    public long readPacked64(final long index) {
        return Helpers.readPacked(this, index, 64);
    }

    public long findPacked8(final long element) {
        return Helpers.findPacked(this, element, 8);
    }

    public long findPacked16(final long element) {
        return Helpers.findPacked(this, element, 16);
    }

    public long findPacked32(final long element) {
        return Helpers.findPacked(this, element, 32);
    }

    public long findPacked64(final long element) {
        return Helpers.findPacked(this, element, 64);
    }

    public MyString slice(final int start, final int end) {
        if (start < 0 || start > end || end > this.length) {
            throw new IndexOutOfBoundsException();
        }
        return new MyString(this.data, this.offset + start, end - start);
    }

    public int at(final int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException();
        }
        return this.data[this.offset + index] & 0xFF;
    }

    public int strchr(final int c) {
        for (int i = 0; i < this.length; i++) {
            if ((this.data[this.offset + i] & 0xFF) == c) {
                return i;
            }
        }
        return -1;
    }

    public int strrchr(final int c) {
        for (int i = this.length - 1; i >= 0; i--) {
            if ((this.data[this.offset + i] & 0xFF) == c) {
                return i;
            }
        }
        return -1;
    }

    public int strstr(final MyString needle) {
        for (int i = 0; i < this.length - needle.length + 1; i++) {
            if (Arrays.equals(
                    this.data,
                    this.offset + i,
                    this.offset + i + needle.length,
                    needle.data,
                    needle.offset,
                    needle.offset + needle.length)) {
                return i;
            }
        }
        return -1;
    }

    public int strspn(final MyString set) {
        final boolean[] table = Helpers.membership(set);
        for (int i = 0; i < this.length; i++) {
            if (!table[this.data[this.offset + i] & 0xFF]) {
                return i;
            }
        }
        return this.length;
    }

    public int strcspn(final MyString set) {
        final boolean[] table = Helpers.membership(set);
        for (int i = 0; i < this.length; i++) {
            if (table[this.data[this.offset + i] & 0xFF]) {
                return i;
            }
        }
        return this.length;
    }

    public int strpbrk(final MyString set) {
        final int i = this.strcspn(set);
        return i == this.length ? -1 : i;
    }

    public boolean startswith(final MyString prefix) {
        return prefix.length <= this.length && Arrays.equals(
                this.data,
                this.offset,
                this.offset + prefix.length,
                prefix.data,
                prefix.offset,
                prefix.offset + prefix.length);
    }

    public boolean endswith(final MyString suffix) {
        return suffix.length <= this.length && Arrays.equals(
                this.data,
                this.offset + this.length - suffix.length,
                this.offset + this.length,
                suffix.data,
                suffix.offset,
                suffix.offset + suffix.length);
    }

    public int compareTo(final MyString other) {
        return Arrays.compareUnsigned(
                this.data,
                this.offset,
                this.offset + this.length,
                other.data,
                other.offset,
                other.offset + other.length);
    }

    public MyString min(final MyString other) {
        return this.compareTo(other) <= 0 ? this : other;
    }

    public MyString max(final MyString other) {
        return this.compareTo(other) >= 0 ? this : other;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MyString s && Arrays.equals(
                this.data,
                this.offset,
                this.offset + this.length,
                s.data,
                s.offset,
                s.offset + s.length);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(Arrays.copyOfRange(data, offset, offset + length));
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < this.length; i++) {
            builder.append(Primitives.escapeByte(this.at(i)));
        }
        builder.append('"');
        return builder.toString();
    }

    public static MyString unescape(final String s) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(s.length());
        int i = 0;
        while (i < s.length()) {
            final var c = Helpers.unescapeAt(s, i);
            bytes.write(c.value);
            i += c.length;
        }
        return new MyString(bytes.toByteArray());
    }

    public static int unescapeCharacter(final String s) {
        final var c = Helpers.unescapeAt(s, 0);
        if (c.length != s.length()) {
            throw new IllegalArgumentException();
        }
        return c.value;
    }

    private static class Helpers {
        private record UnescapedCharacter(int value, int length) {
        }

        private static UnescapedCharacter unescapeAt(final String s, final int i) {
            final char c = s.charAt(i);
            if (c != '\\') {
                if (c > 0x7F) {
                    throw new IllegalArgumentException(); // non-ASCII
                }
                return new UnescapedCharacter(c, 1);
            }
            final char escape = s.charAt(i + 1);
            return switch (escape) {
                case 'f' -> new UnescapedCharacter(0x0C, 2);
                case 'n' -> new UnescapedCharacter(0x0A, 2);
                case 'r' -> new UnescapedCharacter(0x0D, 2);
                case 't' -> new UnescapedCharacter(0x09, 2);
                case 'v' -> new UnescapedCharacter(0x0B, 2);
                case '\\', '\'', '"' -> new UnescapedCharacter(escape, 2);
                case 'x' -> {
                    final char high = s.charAt(i + 2);
                    final char low = s.charAt(i + 3);
                    final int value = (hexDigit(high) << 4) | hexDigit(low);
                    yield new UnescapedCharacter(value, 4);
                }
                default -> throw new IllegalArgumentException();
            };
        }

        private static int hexDigit(final char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            } else if (c >= 'a' && c <= 'f') {
                return c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                return c - 'A' + 10;
            } else {
                throw new IllegalArgumentException();
            }
        }

        private static boolean[] membership(final MyString set) {
            final boolean[] table = new boolean[256];
            for (int i = 0; i < set.length; i++) {
                table[set.data[set.offset + i] & 0xFF] = true;
            }
            return table;
        }

        private static MyString prependPacked(
                final MyString packed,
                final long element,
                final int nbits) {
            final int width = nbits / 8;
            final byte[] buffer = new byte[width + packed.length];
            writeAt(buffer, 0, element, nbits);
            System.arraycopy(packed.data, packed.offset, buffer, width, packed.length);
            return new MyString(buffer);
        }

        private static MyString appendPacked(
                final MyString packed,
                final long element,
                final int nbits) {
            final int width = nbits / 8;
            final byte[] buffer = new byte[packed.length + width];
            System.arraycopy(packed.data, packed.offset, buffer, 0, packed.length);
            writeAt(buffer, packed.length, element, nbits);
            return new MyString(buffer);
        }

        private static long readPacked(final MyString packed, final long index, final int nbits) {
            final int width = nbits / 8;
            final boolean misalignment = packed.length % width != 0;
            final boolean outOfBounds = Long.compareUnsigned(index, packed.length / width) >= 0;
            if (misalignment || outOfBounds) {
                throw new IndexOutOfBoundsException();
            }
            final int start = packed.offset + (int) index * width;
            return readAt(packed.data, start, nbits);
        }

        private static long findPacked(final MyString packed, final long element, final int nbits) {
            final int width = nbits / 8;
            final boolean misalignment = packed.length % width != 0;
            if (misalignment) {
                throw new IndexOutOfBoundsException();
            }
            for (int i = 0; i < packed.length / width; i++) {
                final int start = packed.offset + i * width;
                final long value = readAt(packed.data, start, nbits);
                if (value == element) {
                    return i;
                }
            }
            return -1;
        }

        private static long readAt(final byte[] buffer, final int start, final int nbits) {
            final int width = nbits / 8;
            long value = 0;
            for (int i = 0; i < width; i++) {
                final long b = buffer[start + i] & 0xFFL;
                value |= b << (i * 8);
            }
            return value;
        }

        private static void writeAt(
                final byte[] buffer,
                final int start,
                final long element,
                final int nbits) {
            final int width = nbits / 8;
            for (int i = 0; i < width; i++) {
                final byte b = (byte) (element >>> (i * 8));
                buffer[start + i] = b;
            }
        }
    }
}
