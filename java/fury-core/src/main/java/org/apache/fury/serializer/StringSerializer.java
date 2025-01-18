/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fury.serializer;

import static org.apache.fury.type.TypeUtils.STRING_TYPE;
import static org.apache.fury.util.StringUtils.MULTI_CHARS_NON_ASCII_MASK;
import static org.apache.fury.util.StringUtils.MULTI_CHARS_NON_LATIN_MASK;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.fury.Fury;
import org.apache.fury.annotation.CodegenInvoke;
import org.apache.fury.codegen.Expression;
import org.apache.fury.codegen.Expression.Invoke;
import org.apache.fury.codegen.Expression.StaticInvoke;
import org.apache.fury.memory.LittleEndian;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.Platform;
import org.apache.fury.reflect.ReflectionUtils;
import org.apache.fury.util.MathUtils;
import org.apache.fury.util.Preconditions;
import org.apache.fury.util.StringEncodingUtils;
import org.apache.fury.util.StringUtils;
import org.apache.fury.util.unsafe._JDKAccess;

/**
 * String serializer based on {@link sun.misc.Unsafe} and {@link MethodHandle} for speed.
 *
 * <p>Note that string operations is very common in serialization, and jvm inline and branch
 * elimination is not reliable even in c2 compiler, so we try to inline and avoid checks as we can
 * manually.
 */
@SuppressWarnings("unchecked")
public final class StringSerializer extends ImmutableSerializer<String> {
  private static final boolean STRING_VALUE_FIELD_IS_CHARS;
  private static final boolean STRING_VALUE_FIELD_IS_BYTES;

  private static final byte LATIN1 = 0;
  private static final Byte LATIN1_BOXED = LATIN1;
  private static final byte UTF16 = 1;
  private static final Byte UTF16_BOXED = UTF16;
  private static final byte UTF8 = 2;
  private static final int DEFAULT_BUFFER_SIZE = 1024;

  // Make offset compatible with graalvm native image.
  private static final long STRING_VALUE_FIELD_OFFSET;

  private static class Offset {
    // Make offset compatible with graalvm native image.
    private static final long STRING_CODER_FIELD_OFFSET;

    static {
      try {
        STRING_CODER_FIELD_OFFSET =
            Platform.objectFieldOffset(String.class.getDeclaredField("coder"));
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }
  }

  static {
    Field valueField = ReflectionUtils.getFieldNullable(String.class, "value");
    // Java8 string
    STRING_VALUE_FIELD_IS_CHARS = valueField != null && valueField.getType() == char[].class;
    // Java11 string
    STRING_VALUE_FIELD_IS_BYTES = valueField != null && valueField.getType() == byte[].class;
    try {
      // Make offset compatible with graalvm native image.
      STRING_VALUE_FIELD_OFFSET =
          Platform.objectFieldOffset(String.class.getDeclaredField("value"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    // String length field for android.
    Preconditions.checkArgument(
        ReflectionUtils.getFieldNullable(String.class, "count") == null,
        "Current jdk not supported");
    Preconditions.checkArgument(
        ReflectionUtils.getFieldNullable(String.class, "offset") == null,
        "Current jdk not supported");
  }

  private final boolean compressString;
  private byte[] byteArray = new byte[DEFAULT_BUFFER_SIZE];
  private int smoothByteArrayLength = DEFAULT_BUFFER_SIZE;

  public StringSerializer(Fury fury) {
    super(fury, String.class, fury.trackingRef() && !fury.isStringRefIgnored());
    compressString = fury.compressString();
  }

  @Override
  public void write(MemoryBuffer buffer, String value) {
    writeJavaString(buffer, value);
  }

  @Override
  public void xwrite(MemoryBuffer buffer, String value) {
    writeJavaString(buffer, value);
  }

  @Override
  public String read(MemoryBuffer buffer) {
    return readJavaString(buffer);
  }

  @Override
  public String xread(MemoryBuffer buffer) {
    return readJavaString(buffer);
  }

  public void writeString(MemoryBuffer buffer, String value) {
    writeJavaString(buffer, value);
  }

  public Expression writeStringExpr(Expression strSerializer, Expression buffer, Expression str) {
    if (STRING_VALUE_FIELD_IS_BYTES) {
      if (compressString) {
        return new Invoke(strSerializer, "writeCompressedBytesString", buffer, str);
      } else {
        return new StaticInvoke(StringSerializer.class, "writeBytesString", buffer, str);
      }
    } else {
      if (!STRING_VALUE_FIELD_IS_CHARS) {
        throw new UnsupportedOperationException();
      }
      if (compressString) {
        return new Invoke(strSerializer, "writeCompressedCharsString", buffer, str);
      } else {
        return new Invoke(strSerializer, "writeCharsString", buffer, str);
      }
    }
  }

  public String readString(MemoryBuffer buffer) {
    return readJavaString(buffer);
  }

  public Expression readStringExpr(Expression strSerializer, Expression buffer) {
    if (STRING_VALUE_FIELD_IS_BYTES) {
      if (compressString) {
        return new Invoke(strSerializer, "readCompressedBytesString", STRING_TYPE, buffer);
      } else {
        return new Invoke(strSerializer, "readBytesString", STRING_TYPE, buffer);
      }
    } else {
      if (!STRING_VALUE_FIELD_IS_CHARS) {
        throw new UnsupportedOperationException();
      }
      if (compressString) {
        return new Invoke(strSerializer, "readCompressedCharsString", STRING_TYPE, buffer);
      } else {
        return new Invoke(strSerializer, "readCharsString", STRING_TYPE, buffer);
      }
    }
  }

  @CodegenInvoke
  public String readBytesString(MemoryBuffer buffer) {
    long header = buffer.readVarUint36Small();
    byte coder = (byte) (header & 0b11);
    int numBytes = (int) (header >>> 2);
    byte[] bytes = readBytesUnCompressedUTF16(buffer, numBytes);
    if (coder != UTF8) {
      return newBytesStringZeroCopy(coder, bytes);
    } else {
      return new String(bytes, 0, numBytes, StandardCharsets.UTF_8);
    }
  }

  @CodegenInvoke
  public String readCharsString(MemoryBuffer buffer) {
    long header = buffer.readVarUint36Small();
    byte coder = (byte) (header & 0b11);
    int numBytes = (int) (header >>> 2);
    char[] chars;
    if (coder == LATIN1) {
      chars = readCharsLatin1(buffer, numBytes);
    } else if (coder == UTF16) {
      chars = readCharsUTF16(buffer, numBytes);
    } else {
      throw new RuntimeException("Unknown coder type " + coder);
    }
    return newCharsStringZeroCopy(chars);
  }

  @CodegenInvoke
  public String readCompressedBytesString(MemoryBuffer buffer) {
    long header = buffer.readVarUint36Small();
    byte coder = (byte) (header & 0b11);
    int numBytes = (int) (header >>> 2);
    if (coder == UTF8) {
      return newBytesStringZeroCopy(UTF16, readBytesUTF8(buffer, numBytes));
    } else if (coder == LATIN1 || coder == UTF16) {
      return newBytesStringZeroCopy(coder, readBytesUnCompressedUTF16(buffer, numBytes));
    } else {
      throw new RuntimeException("Unknown coder type " + coder);
    }
  }

  @CodegenInvoke
  public String readCompressedCharsString(MemoryBuffer buffer) {
    long header = buffer.readVarUint36Small();
    byte coder = (byte) (header & 0b11);
    int numBytes = (int) (header >>> 2);
    char[] chars;
    if (coder == LATIN1) {
      chars = readCharsLatin1(buffer, numBytes);
    } else if (coder == UTF8) {
      chars = readCharsUTF8(buffer, numBytes);
    } else if (coder == UTF16) {
      chars = readCharsUTF16(buffer, numBytes);
    } else {
      throw new RuntimeException("Unknown coder type " + coder);
    }
    return newCharsStringZeroCopy(chars);
  }

  // Invoked by fury JIT
  public void writeJavaString(MemoryBuffer buffer, String value) {
    if (STRING_VALUE_FIELD_IS_BYTES) {
      if (compressString) {
        writeCompressedBytesString(buffer, value);
      } else {
        writeBytesString(buffer, value);
      }
    } else {
      assert STRING_VALUE_FIELD_IS_CHARS;
      if (compressString) {
        writeCompressedCharsString(buffer, value);
      } else {
        writeCharsString(buffer, value);
      }
    }
  }

  // Invoked by fury JIT
  public String readJavaString(MemoryBuffer buffer) {
    if (STRING_VALUE_FIELD_IS_BYTES) {
      if (compressString) {
        return readCompressedBytesString(buffer);
      } else {
        return readBytesString(buffer);
      }
    } else {
      assert STRING_VALUE_FIELD_IS_CHARS;
      if (compressString) {
        return readCompressedCharsString(buffer);
      } else {
        return readCharsString(buffer);
      }
    }
  }

  @CodegenInvoke
  public void writeCompressedBytesString(MemoryBuffer buffer, String value) {
    final byte[] bytes = (byte[]) Platform.getObject(value, STRING_VALUE_FIELD_OFFSET);
    final byte coder = Platform.getByte(value, Offset.STRING_CODER_FIELD_OFFSET);
    if (coder == LATIN1 || bestCoder(bytes) == UTF16) {
      writeBytesString(buffer, coder, bytes);
    } else {
      writeBytesUTF8(buffer, bytes);
    }
  }

  @CodegenInvoke
  public void writeCompressedCharsString(MemoryBuffer buffer, String value) {
    final char[] chars = (char[]) Platform.getObject(value, STRING_VALUE_FIELD_OFFSET);
    final byte coder = bestCoder(chars);
    if (coder == LATIN1) {
      writeCharsLatin1(buffer, chars, chars.length);
    } else if (coder == UTF8) {
      writeCharsUTF8(buffer, chars);
    } else {
      writeCharsUTF16(buffer, chars, chars.length);
    }
  }

  @CodegenInvoke
  public static void writeBytesString(MemoryBuffer buffer, String value) {
    byte[] bytes = (byte[]) Platform.getObject(value, STRING_VALUE_FIELD_OFFSET);
    byte coder = Platform.getByte(value, Offset.STRING_CODER_FIELD_OFFSET);
    writeBytesString(buffer, coder, bytes);
  }

  public static void writeBytesString(MemoryBuffer buffer, byte coder, byte[] bytes) {
    int bytesLen = bytes.length;
    long header = ((long) bytesLen << 2) | coder;
    int writerIndex = buffer.writerIndex();
    // The `ensure` ensure next operations are safe without bound checks,
    // and inner heap buffer doesn't change.
    buffer.ensure(writerIndex + 9 + bytesLen); // 1 byte coder + varint max 8 bytes
    final byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      // Some JDK11 Unsafe.copyMemory will `copyMemoryChecks`, and
      // jvm doesn't eliminate well in some jdk.
      final int targetIndex = buffer._unsafeHeapWriterIndex();
      int arrIndex = targetIndex;
      arrIndex += LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      writerIndex += arrIndex - targetIndex;
      System.arraycopy(bytes, 0, targetArray, arrIndex, bytesLen);
    } else {
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      long offHeapAddress = buffer.getUnsafeAddress();
      Platform.copyMemory(
          bytes, Platform.BYTE_ARRAY_OFFSET, null, offHeapAddress + writerIndex, bytesLen);
    }
    writerIndex += bytesLen;
    buffer._unsafeWriterIndex(writerIndex);
  }

  @CodegenInvoke
  public void writeCharsString(MemoryBuffer buffer, String value) {
    final char[] chars = (char[]) Platform.getObject(value, STRING_VALUE_FIELD_OFFSET);
    if (StringUtils.isLatin(chars)) {
      writeCharsLatin1(buffer, chars, chars.length);
    } else {
      writeCharsUTF16(buffer, chars, chars.length);
    }
  }

  public char[] readCharsLatin1(MemoryBuffer buffer, int numBytes) {
    buffer.checkReadableBytes(numBytes);
    byte[] srcArray = buffer.getHeapMemory();
    char[] chars = new char[numBytes];
    if (srcArray != null) {
      int srcIndex = buffer._unsafeHeapReaderIndex();
      for (int i = 0; i < numBytes; i++) {
        chars[i] = (char) (srcArray[srcIndex++] & 0xff);
      }
      buffer._increaseReaderIndexUnsafe(numBytes);
    } else {
      byte[] tmpArray = getByteArray(numBytes);
      buffer.readBytes(tmpArray, 0, numBytes);
      for (int i = 0; i < numBytes; i++) {
        chars[i] = (char) (tmpArray[i] & 0xff);
      }
    }
    return chars;
  }

  public byte[] readBytesUTF8(MemoryBuffer buffer, int numBytes) {
    int udf8Bytes = buffer.readInt32();
    byte[] bytes = new byte[numBytes];
    buffer.checkReadableBytes(udf8Bytes);
    byte[] srcArray = buffer.getHeapMemory();
    if (srcArray != null) {
      int srcIndex = buffer._unsafeHeapReaderIndex();
      int readLen = StringEncodingUtils.convertUTF8ToUTF16(srcArray, srcIndex, udf8Bytes, bytes);
      if (readLen != numBytes) {
        throw new RuntimeException("Decode UTF8 to UTF16 failed");
      }
      buffer._increaseReaderIndexUnsafe(udf8Bytes);
    } else {
      byte[] tmpArray = getByteArray(udf8Bytes);
      buffer.readBytes(tmpArray, 0, udf8Bytes);
      int readLen = StringEncodingUtils.convertUTF8ToUTF16(tmpArray, 0, udf8Bytes, bytes);
      if (readLen != numBytes) {
        throw new RuntimeException("Decode UTF8 to UTF16 failed");
      }
    }
    return bytes;
  }

  public byte[] readBytesUnCompressedUTF16(MemoryBuffer buffer, int numBytes) {
    buffer.checkReadableBytes(numBytes);
    byte[] bytes;
    byte[] heapMemory = buffer.getHeapMemory();
    if (heapMemory != null) {
      final int arrIndex = buffer._unsafeHeapReaderIndex();
      buffer.increaseReaderIndex(numBytes);
      bytes = new byte[numBytes];
      System.arraycopy(heapMemory, arrIndex, bytes, 0, numBytes);
    } else {
      bytes = buffer.readBytes(numBytes);
    }
    return bytes;
  }

  public char[] readCharsUTF16(MemoryBuffer buffer, int numBytes) {
    char[] chars = new char[numBytes >> 1];
    if (Platform.IS_LITTLE_ENDIAN) {
      // FIXME JDK11 utf16 string uses little-endian order.
      buffer.readChars(chars, Platform.CHAR_ARRAY_OFFSET, numBytes);
    } else {
      buffer.checkReadableBytes(numBytes);
      final byte[] targetArray = buffer.getHeapMemory();
      if (targetArray != null) {
        int charIndex = 0;
        for (int i = buffer._unsafeHeapReaderIndex(), end = i + numBytes; i < end; i += 2) {
          char c =
              (char)
                  ((targetArray[i] & 0xff << StringUTF16.HI_BYTE_SHIFT)
                      | ((targetArray[i + 1] & 0xff) << StringUTF16.LO_BYTE_SHIFT));
          chars[charIndex++] = c;
        }
        buffer._increaseReaderIndexUnsafe(numBytes);
      } else {
        final byte[] tmpArray = getByteArray(numBytes);
        buffer.readBytes(tmpArray, 0, numBytes);
        int charIndex = 0;
        for (int i = 0; i < numBytes; i += 2) {
          char c =
              (char)
                  ((tmpArray[i] & 0xff << StringUTF16.HI_BYTE_SHIFT)
                      | ((tmpArray[i + 1] & 0xff) << StringUTF16.LO_BYTE_SHIFT));
          chars[charIndex++] = c;
        }
      }
    }
    return chars;
  }

  public char[] readCharsUTF8(MemoryBuffer buffer, int numBytes) {
    int udf16Chars = numBytes >> 1;
    int udf8Bytes = buffer.readInt32();
    char[] chars = new char[udf16Chars];
    buffer.checkReadableBytes(udf8Bytes);
    byte[] srcArray = buffer.getHeapMemory();
    if (srcArray != null) {
      int srcIndex = buffer._unsafeHeapReaderIndex();
      int readLen = StringEncodingUtils.convertUTF8ToUTF16(srcArray, srcIndex, udf8Bytes, chars);
      if (readLen != udf16Chars) {
        throw new RuntimeException("Decode UTF8 to UTF16 failed");
      }
      buffer._increaseReaderIndexUnsafe(udf8Bytes);
    } else {
      byte[] tmpArray = getByteArray(udf8Bytes);
      buffer.readBytes(tmpArray, 0, udf8Bytes);
      int readLen = StringEncodingUtils.convertUTF8ToUTF16(tmpArray, 0, udf8Bytes, chars);
      if (readLen != udf16Chars) {
        throw new RuntimeException("Decode UTF8 to UTF16 failed");
      }
    }
    return chars;
  }

  public void writeCharsLatin1(MemoryBuffer buffer, char[] chars, int numBytes) {
    int writerIndex = buffer.writerIndex();
    long header = ((long) numBytes << 2) | LATIN1;
    buffer.ensure(writerIndex + 5 + numBytes);
    byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      final int targetIndex = buffer._unsafeHeapWriterIndex();
      int arrIndex = targetIndex;
      arrIndex += LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      writerIndex += arrIndex - targetIndex;
      for (int i = 0; i < numBytes; i++) {
        targetArray[arrIndex + i] = (byte) chars[i];
      }
    } else {
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      final byte[] tmpArray = getByteArray(numBytes);
      for (int i = 0; i < numBytes; i++) {
        tmpArray[i] = (byte) chars[i];
      }
      buffer.put(writerIndex, tmpArray, 0, numBytes);
    }
    writerIndex += numBytes;
    buffer._unsafeWriterIndex(writerIndex);
  }

  public void writeCharsUTF16(MemoryBuffer buffer, char[] chars, int numChars) {
    int numBytes = MathUtils.doubleExact(numChars);
    int writerIndex = buffer.writerIndex();
    long header = ((long) numBytes << 2) | UTF16;
    buffer.ensure(writerIndex + 5 + numBytes);
    final byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      final int targetIndex = buffer._unsafeHeapWriterIndex();
      int arrIndex = targetIndex;
      arrIndex += LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      writerIndex += arrIndex - targetIndex + numBytes;
      if (Platform.IS_LITTLE_ENDIAN) {
        // FIXME JDK11 utf16 string uses little-endian order.
        Platform.UNSAFE.copyMemory(
            chars,
            Platform.CHAR_ARRAY_OFFSET,
            targetArray,
            Platform.BYTE_ARRAY_OFFSET + arrIndex,
            numBytes);
      } else {
        heapWriteCharsUTF16BE(chars, arrIndex, numBytes, targetArray);
      }
    } else {
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      writerIndex = offHeapWriteCharsUTF16(buffer, chars, writerIndex, numBytes);
    }
    buffer._unsafeWriterIndex(writerIndex);
  }

  public void writeCharsUTF8(MemoryBuffer buffer, char[] chars) {
    int estimateMaxBytes = chars.length * 3;
    int numBytes = MathUtils.doubleExact(chars.length);
    int writerIndex = buffer.writerIndex();
    long header = ((long) numBytes << 2) | UTF8;
    buffer.ensure(writerIndex + 9 + estimateMaxBytes);
    byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      int targetIndex = buffer._unsafeHeapWriterIndex();
      int arrIndex = targetIndex;
      arrIndex += LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      writerIndex += arrIndex - targetIndex;
      targetIndex = StringEncodingUtils.convertUTF16ToUTF8(chars, targetArray, arrIndex + 4);
      int written = targetIndex - arrIndex - 4;
      buffer._unsafePutInt32(writerIndex, written);
      buffer._unsafeWriterIndex(writerIndex + 4 + written);
    } else {
      final byte[] tmpArray = getByteArray(estimateMaxBytes);
      int written = StringEncodingUtils.convertUTF16ToUTF8(chars, tmpArray, 0);
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      buffer._unsafePutInt32(writerIndex, written);
      writerIndex += 4;
      buffer.put(writerIndex, tmpArray, 0, written);
      buffer._unsafeWriterIndex(writerIndex + written);
    }
  }

  public void writeBytesUTF8(MemoryBuffer buffer, byte[] bytes) {
    int numBytes = bytes.length;
    int estimateMaxBytes = bytes.length / 2 * 3;
    int writerIndex = buffer.writerIndex();
    long header = ((long) numBytes << 2) | UTF8;
    buffer.ensure(writerIndex + 9 + estimateMaxBytes);
    byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      int targetIndex = buffer._unsafeHeapWriterIndex();
      int arrIndex = targetIndex;
      arrIndex += LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      writerIndex += arrIndex - targetIndex;
      targetIndex = StringEncodingUtils.convertUTF16ToUTF8(bytes, targetArray, arrIndex + 4);
      int written = targetIndex - arrIndex - 4;
      buffer._unsafePutInt32(writerIndex, written);
      buffer._unsafeWriterIndex(writerIndex + 4 + written);
    } else {
      final byte[] tmpArray = getByteArray(estimateMaxBytes);
      int written = StringEncodingUtils.convertUTF16ToUTF8(bytes, tmpArray, 0);
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      buffer._unsafePutInt32(writerIndex, written);
      writerIndex += 4;
      buffer.put(writerIndex, tmpArray, 0, written);
      buffer._unsafeWriterIndex(writerIndex + written);
    }
  }

  private static final MethodHandles.Lookup STRING_LOOK_UP =
      _JDKAccess._trustedLookup(String.class);
  private static final BiFunction<char[], Boolean, String> CHARS_STRING_ZERO_COPY_CTR =
      getCharsStringZeroCopyCtr();
  private static final BiFunction<byte[], Byte, String> BYTES_STRING_ZERO_COPY_CTR =
      getBytesStringZeroCopyCtr();
  private static final Function<byte[], String> LATIN_BYTES_STRING_ZERO_COPY_CTR =
      getLatinBytesStringZeroCopyCtr();

  public static String newCharsStringZeroCopy(char[] data) {
    if (!STRING_VALUE_FIELD_IS_CHARS) {
      throw new IllegalStateException("String value isn't char[], current java isn't supported");
    }
    // 25% faster than unsafe put field, only 10% slower than `new String(str)`
    return CHARS_STRING_ZERO_COPY_CTR.apply(data, Boolean.TRUE);
  }

  // coder param first to make inline call args
  // `(buffer.readByte(), buffer.readBytesWithSizeEmbedded())` work.
  public static String newBytesStringZeroCopy(byte coder, byte[] data) {
    if (coder == LATIN1) {
      // 700% faster than unsafe put field in java11, only 10% slower than `new String(str)` for
      // string length 230.
      // 50% faster than unsafe put field in java11 for string length 10.
      if (LATIN_BYTES_STRING_ZERO_COPY_CTR != null) {
        return LATIN_BYTES_STRING_ZERO_COPY_CTR.apply(data);
      } else {
        // JDK17 removed newStringLatin1
        return BYTES_STRING_ZERO_COPY_CTR.apply(data, LATIN1_BOXED);
      }
    } else if (coder == UTF16) {
      // avoid byte box cost.
      return BYTES_STRING_ZERO_COPY_CTR.apply(data, UTF16_BOXED);
    } else {
      // 700% faster than unsafe put field in java11, only 10% slower than `new String(str)` for
      // string length 230.
      // 50% faster than unsafe put field in java11 for string length 10.
      // `invokeExact` must pass exact params with exact types:
      // `(Object) data, coder` will throw WrongMethodTypeException
      return BYTES_STRING_ZERO_COPY_CTR.apply(data, coder);
    }
  }

  private static BiFunction<char[], Boolean, String> getCharsStringZeroCopyCtr() {
    if (!STRING_VALUE_FIELD_IS_CHARS) {
      return null;
    }
    MethodHandle handle = getJavaStringZeroCopyCtrHandle();
    if (handle == null) {
      return null;
    }
    try {
      // Faster than handle.invokeExact(data, boolean)
      CallSite callSite =
          LambdaMetafactory.metafactory(
              STRING_LOOK_UP,
              "apply",
              MethodType.methodType(BiFunction.class),
              handle.type().generic(),
              handle,
              handle.type());
      return (BiFunction) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      return null;
    }
  }

  private static BiFunction<byte[], Byte, String> getBytesStringZeroCopyCtr() {
    if (!STRING_VALUE_FIELD_IS_BYTES) {
      return null;
    }
    MethodHandle handle = getJavaStringZeroCopyCtrHandle();
    if (handle == null) {
      return null;
    }
    // Faster than handle.invokeExact(data, byte)
    try {
      MethodType instantiatedMethodType =
          MethodType.methodType(handle.type().returnType(), new Class[] {byte[].class, Byte.class});
      CallSite callSite =
          LambdaMetafactory.metafactory(
              STRING_LOOK_UP,
              "apply",
              MethodType.methodType(BiFunction.class),
              handle.type().generic(),
              handle,
              instantiatedMethodType);
      return (BiFunction) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      return null;
    }
  }

  private static Function<byte[], String> getLatinBytesStringZeroCopyCtr() {
    if (!STRING_VALUE_FIELD_IS_BYTES) {
      return null;
    }
    if (STRING_LOOK_UP == null) {
      return null;
    }
    try {
      Class<?> clazz = Class.forName("java.lang.StringCoding");
      MethodHandles.Lookup caller = STRING_LOOK_UP.in(clazz);
      // JDK17 removed this method.
      MethodHandle handle =
          caller.findStatic(
              clazz, "newStringLatin1", MethodType.methodType(String.class, byte[].class));
      // Faster than handle.invokeExact(data, byte)
      return _JDKAccess.makeFunction(caller, handle, Function.class);
    } catch (Throwable e) {
      return null;
    }
  }

  private static MethodHandle getJavaStringZeroCopyCtrHandle() {
    Preconditions.checkArgument(Platform.JAVA_VERSION >= 8);
    if (STRING_LOOK_UP == null) {
      return null;
    }
    try {
      if (STRING_VALUE_FIELD_IS_CHARS) {
        return STRING_LOOK_UP.findConstructor(
            String.class, MethodType.methodType(void.class, char[].class, boolean.class));
      } else {
        return STRING_LOOK_UP.findConstructor(
            String.class, MethodType.methodType(void.class, byte[].class, byte.class));
      }
    } catch (Exception e) {
      return null;
    }
  }

  private static void heapWriteCharsUTF16BE(
      char[] chars, int arrIndex, int numBytes, byte[] targetArray) {
    // Write to heap memory then copy is 250% faster than unsafe write to direct memory.
    int charIndex = 0;
    for (int i = arrIndex, end = i + numBytes; i < end; i += 2) {
      char c = chars[charIndex++];
      targetArray[i] = (byte) (c >> StringUTF16.HI_BYTE_SHIFT);
      targetArray[i + 1] = (byte) (c >> StringUTF16.LO_BYTE_SHIFT);
    }
  }

  private int offHeapWriteCharsUTF16(
      MemoryBuffer buffer, char[] chars, int writerIndex, int numBytes) {
    byte[] tmpArray = getByteArray(numBytes);
    int charIndex = 0;
    for (int i = 0; i < numBytes; i += 2) {
      char c = chars[charIndex++];
      tmpArray[i] = (byte) (c >> StringUTF16.HI_BYTE_SHIFT);
      tmpArray[i + 1] = (byte) (c >> StringUTF16.LO_BYTE_SHIFT);
    }
    buffer.put(writerIndex, tmpArray, 0, numBytes);
    writerIndex += numBytes;
    return writerIndex;
  }

  private static byte bestCoder(char[] chars) {
    int numChars = chars.length;
    // sample 64 chars
    int sampleNum = Math.min(64, numChars);
    int vectorizedLen = sampleNum >> 2;
    int vectorizedChars = vectorizedLen << 2;
    int endOffset = Platform.CHAR_ARRAY_OFFSET + (vectorizedChars << 1);
    int asciiCount = 0;
    int latin1Count = 0;
    for (int offset = Platform.CHAR_ARRAY_OFFSET, charOffset = 0;
        offset < endOffset;
        offset += 8, charOffset += 4) {
      long multiChars = Platform.getLong(chars, offset);
      if ((multiChars & MULTI_CHARS_NON_ASCII_MASK) == 0) {
        latin1Count += 4;
        asciiCount += 4;
      } else if ((multiChars & MULTI_CHARS_NON_LATIN_MASK) == 0) {
        latin1Count += 4;
        for (int i = 0; i < 4; ++i) {
          if (chars[charOffset + i] < 0x80) {
            asciiCount++;
          }
        }
      } else {
        for (int i = 0; i < 4; ++i) {
          if (chars[charOffset + i] < 0x80) {
            latin1Count++;
            asciiCount++;
          } else if (chars[charOffset + i] <= 0xFF) {
            latin1Count++;
          }
        }
      }
    }

    for (int i = vectorizedChars; i < sampleNum; i++) {
      if (chars[i] < 0x80) {
        latin1Count++;
        asciiCount++;
      } else if (chars[i] <= 0xFF) {
        latin1Count++;
      }
    }

    if (latin1Count == numChars
        || (latin1Count == sampleNum && StringUtils.isLatin(chars, sampleNum))) {
      return LATIN1;
    } else if (asciiCount >= sampleNum * 0.5) {
      // ascii number > 50%, choose UTF-8
      return UTF8;
    } else {
      return UTF16;
    }
  }

  private static byte bestCoder(byte[] bytes) {
    int numBytes = bytes.length;
    // sample 64 chars
    int sampleNum = Math.min(64 << 1, numBytes);
    int vectorizedLen = sampleNum >> 3;
    int vectorizedBytes = vectorizedLen << 3;
    int endOffset = Platform.BYTE_ARRAY_OFFSET + vectorizedBytes;
    int asciiCount = 0;
    for (int offset = Platform.BYTE_ARRAY_OFFSET, bytesOffset = 0;
        offset < endOffset;
        offset += 8, bytesOffset += 8) {
      long multiChars = Platform.getLong(bytes, offset);
      if ((multiChars & MULTI_CHARS_NON_ASCII_MASK) == 0) {
        asciiCount += 4;
      } else {
        for (int i = 0; i < 8; i += 2) {
          if (Platform.getChar(bytes, offset + i) < 0x80) {
            asciiCount++;
          }
        }
      }
    }
    for (int i = vectorizedBytes; vectorizedBytes < sampleNum; vectorizedBytes += 2) {
      if (Platform.getChar(bytes, Platform.BYTE_ARRAY_OFFSET + i) < 0x80) {
        asciiCount++;
      }
    }
    // ascii number > 50%, choose UTF-8
    if (asciiCount >= sampleNum * 0.5) {
      return UTF8;
    } else {
      return UTF16;
    }
  }

  private byte[] getByteArray(int numElements) {
    byte[] byteArray = this.byteArray;
    if (byteArray.length < numElements) {
      byteArray = new byte[numElements];
      this.byteArray = byteArray;
    }
    if (byteArray.length > DEFAULT_BUFFER_SIZE) {
      smoothByteArrayLength =
          Math.max(((int) (smoothByteArrayLength * 0.9 + numElements * 0.1)), DEFAULT_BUFFER_SIZE);
      if (smoothByteArrayLength <= DEFAULT_BUFFER_SIZE) {
        this.byteArray = new byte[DEFAULT_BUFFER_SIZE];
      }
    }
    return byteArray;
  }
}
