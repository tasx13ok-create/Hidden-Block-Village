package village.dungeonguard;

public final class PackedBlockStates {
    private final int length;
    private byte[] bytes;
    private int indexBytes;
    private int highWater;

    public PackedBlockStates(int length) {
        this(length, 1, new byte[PackedBlockStates.checkedByteLength(length, 1)], 0);
    }

    private PackedBlockStates(int length, int indexBytes, byte[] bytes, int highWater) {
        this.length = length;
        this.indexBytes = indexBytes;
        this.bytes = bytes;
        this.highWater = highWater;
    }

    public int length() {
        return this.length;
    }

    public int indexBytes() {
        return this.indexBytes;
    }

    public int storageBytes() {
        return this.bytes.length;
    }

    public int get(int index) {
        this.checkIndex(index);
        return PackedBlockStates.read(this.bytes, index * this.indexBytes, this.indexBytes);
    }

    public void set(int index, int value) {
        this.checkIndex(index);
        if (value < 0) {
            throw new IllegalArgumentException("Negative palette index");
        }
        int required = PackedBlockStates.widthFor(value);
        if (required > this.indexBytes) {
            this.widen(required);
        }
        PackedBlockStates.write(this.bytes, index * this.indexBytes, this.indexBytes, value);
        this.highWater = Math.max(this.highWater, index + 1);
    }

    public byte[] toByteArray() {
        return (byte[])this.bytes.clone();
    }

    byte[] bytesForSerialization() {
        return this.bytes;
    }

    public static PackedBlockStates fromBytes(int length, int indexBytes, byte[] bytes, int paletteSize) {
        return PackedBlockStates.decode(length, indexBytes, bytes, paletteSize, true);
    }

    static PackedBlockStates takeBytes(int length, int indexBytes, byte[] bytes, int paletteSize) {
        return PackedBlockStates.decode(length, indexBytes, bytes, paletteSize, false);
    }

    private static PackedBlockStates decode(int length, int indexBytes, byte[] bytes, int paletteSize, boolean copy) {
        int expected = PackedBlockStates.checkedByteLength(length, indexBytes);
        if (bytes == null || bytes.length != expected) {
            throw new IllegalArgumentException("Packed block byte length differs from dimensions");
        }
        PackedBlockStates.requirePalette(paletteSize);
        PackedBlockStates result = new PackedBlockStates(length, indexBytes, copy ? (byte[])bytes.clone() : bytes, length);
        result.validatePalette(paletteSize);
        return result;
    }

    public static PackedBlockStates fromLegacy(int[] values, int paletteSize) {
        if (values == null) {
            throw new IllegalArgumentException("Missing legacy block indexes");
        }
        PackedBlockStates.requirePalette(paletteSize);
        int maximum = 0;
        for (int value : values) {
            if (value < 0 || value >= paletteSize) {
                throw new IllegalArgumentException("Invalid legacy palette index");
            }
            maximum = Math.max(maximum, value);
        }
        int width = PackedBlockStates.widthFor(maximum);
        byte[] data = new byte[PackedBlockStates.checkedByteLength(values.length, width)];
        for (int i = 0; i < values.length; ++i) {
            PackedBlockStates.write(data, i * width, width, values[i]);
        }
        return new PackedBlockStates(values.length, width, data, values.length);
    }

    public void validatePalette(int paletteSize) {
        PackedBlockStates.requirePalette(paletteSize);
        for (int offset = 0; offset < this.bytes.length; offset += this.indexBytes) {
            int value = PackedBlockStates.read(this.bytes, offset, this.indexBytes);
            if (value >= 0 && value < paletteSize) continue;
            throw new IllegalArgumentException("Invalid packed palette index");
        }
    }

    private void widen(int width) {
        byte[] expanded = new byte[PackedBlockStates.checkedByteLength(this.length, width)];
        for (int i = 0; i < this.highWater; ++i) {
            PackedBlockStates.write(expanded, i * width, width, PackedBlockStates.read(this.bytes, i * this.indexBytes, this.indexBytes));
        }
        this.bytes = expanded;
        this.indexBytes = width;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("Block index outside packed array");
        }
    }

    private static int checkedByteLength(int length, int indexBytes) {
        if (length < 0 || indexBytes != 1 && indexBytes != 2 && indexBytes != 4) {
            throw new IllegalArgumentException("Packed block length/width is invalid");
        }
        long byteLength = (long)length * (long)indexBytes;
        if (byteLength > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Packed block storage exceeds supported array size");
        }
        return (int)byteLength;
    }

    private static void requirePalette(int paletteSize) {
        if (paletteSize <= 0) {
            throw new IllegalArgumentException("Palette must contain at least one block state");
        }
    }

    private static int widthFor(int value) {
        return value <= 255 ? 1 : (value <= 65535 ? 2 : 4);
    }

    private static int read(byte[] data, int offset, int width) {
        int value = data[offset] & 0xFF;
        for (int n = 1; n < width; ++n) {
            value = value << 8 | data[offset + n] & 0xFF;
        }
        return value;
    }

    private static void write(byte[] data, int offset, int width, int value) {
        for (int n = width - 1; n >= 0; --n) {
            data[offset + n] = (byte)value;
            value >>>= 8;
        }
    }
}

