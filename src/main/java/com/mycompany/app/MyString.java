package com.mycompany.app;

import io.vavr.collection.Iterator;
import io.vavr.collection.Vector;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

public final class MyString {
    private final Vector<Byte> data; // an immutable, persistent byte vector

    public MyString(final byte[] data) {
        this(data, 0, data.length);
    }

    public MyString(final byte[] data, final int offset, final int length) {
        if (data == null) {
            throw new IllegalArgumentException("Null byte array");
        }
        if (offset < 0) {
            throw new IllegalArgumentException(String.format("Negative offset %d", offset));
        }
        if (length < 0) {
            throw new IllegalArgumentException(String.format("Negative length %d", length));
        }
        if (offset > data.length) {
            throw new IllegalArgumentException(
                    String.format(
                            "Offset %d exceeds the byte array of length %d",
                            offset,
                            data.length));
        }
        if (length > data.length - offset) {
            throw new IllegalArgumentException(
                    String.format(
                            "Length %d exceeds %d bytes available from offset %d",
                            length,
                            data.length - offset,
                            offset));
        }
        // _O(length)_ instead of _O(data.length)_ due to `Arrays.copyOfRange`.
        this.data = Vector.ofAll(Arrays.copyOfRange(data, offset, offset + length));
    }

    private MyString(final Vector<Byte> data) {
        this.data = data;
    }

    public static MyString ofByte(final int value) {
        return new MyString(new byte[]{(byte) value});
    }

    public static MyString ofAscii(final String s) {
        return new MyString(s.getBytes(StandardCharsets.US_ASCII));
    }

    public long length() {
        return this.data.length();
    }

    // FNV-1a, 64-bit.
    public long hash64() {
        long h = 0xCBF29CE484222325L;
        for (final byte myByte : this.data) {
            h = (h ^ (myByte & 0xFF)) * 0x100000001B3L;
        }
        return h;
    }

    public MyString concat(final MyString other) {
        return new MyString(this.data.appendAll(other.data));
    }

    public MyString prependPacked8(final long element) {
        return PackedHelpers.prepend(this, element, 8);
    }

    public MyString prependPacked16(final long element) {
        return PackedHelpers.prepend(this, element, 16);
    }

    public MyString prependPacked32(final long element) {
        return PackedHelpers.prepend(this, element, 32);
    }

    public MyString prependPacked64(final long element) {
        return PackedHelpers.prepend(this, element, 64);
    }

    public MyString appendPacked8(final long element) {
        return PackedHelpers.append(this, element, 8);
    }

    public MyString appendPacked16(final long element) {
        return PackedHelpers.append(this, element, 16);
    }

    public MyString appendPacked32(final long element) {
        return PackedHelpers.append(this, element, 32);
    }

    public MyString appendPacked64(final long element) {
        return PackedHelpers.append(this, element, 64);
    }

    public MyString updatePacked8(final long index, final long element) {
        return PackedHelpers.update(this, index, element, 8);
    }

    public MyString updatePacked16(final long index, final long element) {
        return PackedHelpers.update(this, index, element, 16);
    }

    public MyString updatePacked32(final long index, final long element) {
        return PackedHelpers.update(this, index, element, 32);
    }

    public MyString updatePacked64(final long index, final long element) {
        return PackedHelpers.update(this, index, element, 64);
    }

    public MyString insertPacked8(final long index, final long element) {
        return PackedHelpers.insert(this, index, element, 8);
    }

    public MyString insertPacked16(final long index, final long element) {
        return PackedHelpers.insert(this, index, element, 16);
    }

    public MyString insertPacked32(final long index, final long element) {
        return PackedHelpers.insert(this, index, element, 32);
    }

    public MyString insertPacked64(final long index, final long element) {
        return PackedHelpers.insert(this, index, element, 64);
    }

    public MyString removePacked8(final long index) {
        return PackedHelpers.remove(this, index, 8);
    }

    public MyString removePacked16(final long index) {
        return PackedHelpers.remove(this, index, 16);
    }

    public MyString removePacked32(final long index) {
        return PackedHelpers.remove(this, index, 32);
    }

    public MyString removePacked64(final long index) {
        return PackedHelpers.remove(this, index, 64);
    }

    public long readPacked8(final long index) {
        return this.at(index);
    }

    public long readPacked16(final long index) {
        return PackedHelpers.read(this, index, 16);
    }

    public long readPacked32(final long index) {
        return PackedHelpers.read(this, index, 32);
    }

    public long readPacked64(final long index) {
        return PackedHelpers.read(this, index, 64);
    }

    public long findPacked8(final long element) {
        return this.strchr((int) element);
    }

    public long findPacked16(final long element) {
        return PackedHelpers.find(this, element, 16);
    }

    public long findPacked32(final long element) {
        return PackedHelpers.find(this, element, 32);
    }

    public long findPacked64(final long element) {
        return PackedHelpers.find(this, element, 64);
    }

    public long rfindPacked8(final long element) {
        return this.strrchr((int) element);
    }

    public long rfindPacked16(final long element) {
        return PackedHelpers.rfind(this, element, 16);
    }

    public long rfindPacked32(final long element) {
        return PackedHelpers.rfind(this, element, 32);
    }

    public long rfindPacked64(final long element) {
        return PackedHelpers.rfind(this, element, 64);
    }

    public MyString slice(final long start, final long end) {
        if (start < 0 || start > end || end > this.length()) {
            throw new IndexOutOfBoundsException();
        }
        return new MyString(this.data.slice((int) start, (int) end));
    }

    public int at(final long index) {
        if (index < 0 || index >= this.length()) {
            throw new IndexOutOfBoundsException();
        }
        return this.data.get((int) index) & 0xFF;
    }

    public long strchr(final int c) {
        return this.data.indexWhere(myByte -> (myByte & 0xFF) == c);
    }

    public long strrchr(final int c) {
        return this.data.lastIndexWhere(myByte -> (myByte & 0xFF) == c);
    }

    public long strstr(final MyString needle) {
        return this.data.indexOfSlice(needle.data);
    }

    public long strspn(final MyString set) {
        return this.data.segmentLength(set.data.toSet()::contains, 0);
    }

    public long strcspn(final MyString set) {
        return this.data.segmentLength(Predicate.not(set.data.toSet()::contains), 0);
    }

    public long strpbrk(final MyString set) {
        final long i = this.strcspn(set);
        return i == this.length() ? -1 : i;
    }

    public boolean startswith(final MyString prefix) {
        return this.data.startsWith(prefix.data);
    }

    public boolean endswith(final MyString suffix) {
        return this.data.endsWith(suffix.data);
    }

    public int compareTo(final MyString other) {
        final var left = this.data.iterator();
        final var right = other.data.iterator();
        while (left.hasNext() && right.hasNext()) {
            final int result = Byte.compareUnsigned(left.next(), right.next());
            if (result != 0) {
                return result;
            }
        }
        return Long.compare(this.length(), other.length());
    }

    public MyString min(final MyString other) {
        return this.compareTo(other) <= 0 ? this : other;
    }

    public MyString max(final MyString other) {
        return this.compareTo(other) >= 0 ? this : other;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof MyString s && this.data.equals(s.data);
    }

    @Override
    public int hashCode() {
        return this.data.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("\"");
        for (final byte myByte : this.data) {
            builder.append(Primitives.escapeByte(myByte & 0xFF));
        }
        builder.append('"');
        return builder.toString();
    }

    public static MyString unescape(final String s) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(s.length());
        int i = 0;
        while (i < s.length()) {
            final var c = CharacterHelpers.unescapeAt(s, i);
            bytes.write(c.value);
            i += c.length;
        }
        return new MyString(bytes.toByteArray());
    }

    public static int unescapeCharacter(final String s) {
        final var c = CharacterHelpers.unescapeAt(s, 0);
        if (c.length != s.length()) {
            throw new IllegalArgumentException();
        }
        return c.value;
    }

    private static class CharacterHelpers {
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
    }

    private static class PackedHelpers {
        private static MyString prepend(
                final MyString packed,
                final long element,
                final int nbits) {
            final int width = nbits / 8;
            final var result = packed.data.prependAll(encode(width, element));
            return new MyString(result);
        }

        private static MyString append(final MyString packed, final long element, final int nbits) {
            final int width = nbits / 8;
            final var result = packed.data.appendAll(encode(width, element));
            return new MyString(result);
        }

        private static MyString update(
                final MyString packed,
                final long index,
                final long element,
                final int nbits) {
            final int width = nbits / 8;
            final boolean misalignment = packed.length() % width != 0;
            final boolean outOfBounds = Long.compareUnsigned(index, packed.length() / width) >= 0;
            if (misalignment || outOfBounds) {
                throw new IndexOutOfBoundsException();
            }
            final int start = (int) index * width;
            var result = packed.data;
            for (int i = 0; i < width; i++) {
                result = result.update(start + i, (byte) (element >>> (i * 8)));
            }
            return new MyString(result);
        }

        private static MyString insert(
                final MyString packed,
                final long index,
                final long element,
                final int nbits) {
            final int width = nbits / 8;
            final boolean misalignment = packed.length() % width != 0;
            final boolean outOfBounds = Long.compareUnsigned(index, packed.length() / width) > 0;
            if (misalignment || outOfBounds) {
                throw new IndexOutOfBoundsException();
            }
            final int start = (int) index * width;
            final var result = packed.data.insertAll(start, encode(width, element));
            return new MyString(result);
        }

        private static MyString remove(final MyString packed, final long index, final int nbits) {
            final int width = nbits / 8;
            final boolean misalignment = packed.length() % width != 0;
            final boolean outOfBounds = Long.compareUnsigned(index, packed.length() / width) >= 0;
            if (misalignment || outOfBounds) {
                throw new IndexOutOfBoundsException();
            }
            final int start = (int) index * width;
            final var before = packed.data.take(start);
            final var after = packed.data.drop(start + width);
            // The argument vector is copied, so make sure we passe the smaller vector.
            final var result = before.size() > after.size()
                    ? before.appendAll(after)
                    : after.prependAll(before);
            return new MyString(result);
        }

        private static long read(final MyString packed, final long index, final int nbits) {
            final int width = nbits / 8;
            final boolean misalignment = packed.length() % width != 0;
            final boolean outOfBounds = Long.compareUnsigned(index, packed.length() / width) >= 0;
            if (misalignment || outOfBounds) {
                throw new IndexOutOfBoundsException();
            }
            final int start = (int) index * width;
            return decode(width, i -> packed.data.get(start + i));
        }

        private static long find(final MyString packed, final long element, final int nbits) {
            final int width = nbits / 8;
            if (packed.length() % width != 0) {
                throw new IndexOutOfBoundsException();
            }
            final Iterator<Byte> it = packed.data.iterator();
            final IntUnaryOperator next = i -> it.next();
            for (int i = 0; i < packed.length() / width; i++) {
                if (decode(width, next) == element) {
                    return i;
                }
            }
            return -1;
        }

        private static long rfind(final MyString packed, final long element, final int nbits) {
            final int width = nbits / 8;
            if (packed.length() % width != 0) {
                throw new IndexOutOfBoundsException();
            }
            final Iterator<Byte> it = packed.data.reverseIterator();
            final IntUnaryOperator next = i -> it.next();
            for (int i = (int) (packed.length() / width) - 1; i >= 0; i--) {
                if (decodeReverse(width, next) == element) {
                    return i;
                }
            }
            return -1;
        }

        // Reads bytes one after another, from `0` to `width - 1`, without re-reading.
        private static long decode(final int width, final IntUnaryOperator byteAt) {
            long value = 0;
            for (int i = 0; i < width; i++) {
                value |= (byteAt.applyAsInt(i) & 0xFFL) << (i * 8);
            }
            return value;
        }

        // Same as `decode`, but reads bytes from `width - 1` to `0`.
        private static long decodeReverse(final int width, final IntUnaryOperator byteAt) {
            long value = 0;
            for (int i = width - 1; i >= 0; i--) {
                value |= (byteAt.applyAsInt(i) & 0xFFL) << (i * 8);
            }
            return value;
        }

        private static List<Byte> encode(final int width, final long element) {
            final byte[] buffer = new byte[width];
            for (int i = 0; i < width; i++) {
                buffer[i] = (byte) (element >>> (i * 8));
            }
            return new ByteList(buffer);
        }

        private static final class ByteList extends AbstractList<Byte> {
            private final byte[] bytes;

            private ByteList(final byte[] bytes) {
                this.bytes = bytes;
            }

            @Override
            public Byte get(final int index) {
                return bytes[index];
            }

            @Override
            public int size() {
                return bytes.length;
            }
        }
    }
}
