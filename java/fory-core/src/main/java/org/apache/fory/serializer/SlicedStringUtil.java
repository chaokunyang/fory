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

package org.apache.fory.serializer;

import org.apache.fory.memory.LittleEndian;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.util.MathUtils;
import org.apache.fory.util.StringEncodingUtils;
import org.apache.fory.util.StringUtils;

final class SlicedStringUtil {
  private static final byte LATIN1 = 0;
  private static final byte UTF16 = 1;
  private static final byte UTF8 = 2;

  private SlicedStringUtil() {}

  static void writeCharsLatin1WithOffset(
      StringSerializer serializer, MemoryBuffer buffer, char[] chars, int offset, int count) {
    int writerIndex = buffer.writerIndex();
    long header = ((long) count << 2) | LATIN1;
    buffer.ensure(writerIndex + 5 + count);
    byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      final int targetIndex = buffer._unsafeHeapWriterIndex();
      int arrIndex = targetIndex;
      arrIndex += LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      writerIndex += arrIndex - targetIndex;
      for (int i = 0; i < count; i++) {
        targetArray[arrIndex + i] = (byte) chars[offset + i];
      }
    } else {
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      final byte[] tmpArray = serializer.getByteArray(count);
      for (int i = 0; i < count; i++) {
        tmpArray[i] = (byte) chars[offset + i];
      }
      buffer.put(writerIndex, tmpArray, 0, count);
    }
    writerIndex += count;
    buffer._unsafeWriterIndex(writerIndex);
  }

  static void writeCharsUTF16WithOffset(
      StringSerializer serializer, MemoryBuffer buffer, char[] chars, int offset, int count) {
    int numBytes = MathUtils.doubleExact(count);
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
            Platform.CHAR_ARRAY_OFFSET + ((long) offset << 1),
            targetArray,
            Platform.BYTE_ARRAY_OFFSET + arrIndex,
            numBytes);
      } else {
        writeCharsUTF16BEToHeap(chars, offset, arrIndex, numBytes, targetArray);
      }
    } else {
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      if (Platform.IS_LITTLE_ENDIAN) {
        writerIndex =
            offHeapWriteCharsUTF16WithOffset(
                serializer, buffer, chars, offset, writerIndex, numBytes);
      } else {
        writerIndex =
            offHeapWriteCharsUTF16BEWithOffset(
                serializer, buffer, chars, offset, writerIndex, numBytes);
      }
    }
    buffer._unsafeWriterIndex(writerIndex);
  }

  static void writeCharsUTF8WithOffset(
      StringSerializer serializer, MemoryBuffer buffer, char[] chars, int offset, int count) {
    int estimateMaxBytes = count * 3;
    int approxNumBytes = (int) (count * 1.5) + 1;
    int writerIndex = buffer.writerIndex();
    buffer.ensure(writerIndex + 9 + estimateMaxBytes);
    byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      int targetIndex = buffer._unsafeHeapWriterIndex();
      int headerPos = targetIndex;
      int arrIndex = targetIndex;
      long header = ((long) approxNumBytes << 2) | UTF8;
      int headerBytesWritten = LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      arrIndex += headerBytesWritten;
      writerIndex += headerBytesWritten;
      targetIndex =
          StringEncodingUtils.convertUTF16ToUTF8(chars, offset, count, targetArray, arrIndex);
      byte stashedByte = targetArray[arrIndex];
      int written = targetIndex - arrIndex;
      header = ((long) written << 2) | UTF8;
      int diff =
          LittleEndian.putVarUint36Small(targetArray, headerPos, header) - headerBytesWritten;
      if (diff != 0) {
        handleWriteCharsUTF8UnalignedHeaderBytes(targetArray, arrIndex, diff, written, stashedByte);
      }
      buffer._unsafeWriterIndex(writerIndex + written + diff);
    } else {
      final byte[] tmpArray = serializer.getByteArray(estimateMaxBytes);
      int written = StringEncodingUtils.convertUTF16ToUTF8(chars, offset, count, tmpArray, 0);
      long header = ((long) written << 2) | UTF8;
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      buffer.put(writerIndex, tmpArray, 0, written);
      buffer._unsafeWriterIndex(writerIndex + written);
    }
  }

  static void writeCharsUTF8PerfOptimizedWithOffset(
      StringSerializer serializer, MemoryBuffer buffer, char[] chars, int offset, int count) {
    int estimateMaxBytes = count * 3;
    int numBytes = MathUtils.doubleExact(count);
    int writerIndex = buffer.writerIndex();
    long header = ((long) numBytes << 2) | UTF8;
    buffer.ensure(writerIndex + 9 + estimateMaxBytes);
    byte[] targetArray = buffer.getHeapMemory();
    if (targetArray != null) {
      int targetIndex = buffer._unsafeHeapWriterIndex();
      int arrIndex = targetIndex;
      arrIndex += LittleEndian.putVarUint36Small(targetArray, arrIndex, header);
      writerIndex += arrIndex - targetIndex;
      targetIndex =
          StringEncodingUtils.convertUTF16ToUTF8(chars, offset, count, targetArray, arrIndex + 4);
      int written = targetIndex - arrIndex - 4;
      buffer._unsafePutInt32(writerIndex, written);
      buffer._unsafeWriterIndex(writerIndex + 4 + written);
    } else {
      final byte[] tmpArray = serializer.getByteArray(estimateMaxBytes);
      int written = StringEncodingUtils.convertUTF16ToUTF8(chars, offset, count, tmpArray, 0);
      writerIndex += buffer._unsafePutVarUint36Small(writerIndex, header);
      buffer._unsafePutInt32(writerIndex, written);
      writerIndex += 4;
      buffer.put(writerIndex, tmpArray, 0, written);
      buffer._unsafeWriterIndex(writerIndex + written);
    }
  }

  static boolean isLatin(char[] chars, int offset, int count) {
    int end = offset + count;
    int vectorizedChars = count & ~3;
    int vectorEnd = offset + vectorizedChars;
    long byteOffset = Platform.CHAR_ARRAY_OFFSET + ((long) offset << 1);
    long endOffset = Platform.CHAR_ARRAY_OFFSET + ((long) vectorEnd << 1);
    for (long off = byteOffset; off < endOffset; off += 8) {
      long multiChars = Platform.getLong(chars, off);
      if ((multiChars & StringUtils.MULTI_CHARS_NON_LATIN_MASK) != 0) {
        return false;
      }
    }
    for (int i = vectorEnd; i < end; i++) {
      if (chars[i] > 0xFF) {
        return false;
      }
    }
    return true;
  }

  static byte bestCoder(char[] chars, int offset, int count) {
    int sampleNum = Math.min(64, count);
    int vectorizedLen = sampleNum >> 2;
    int vectorizedChars = vectorizedLen << 2;
    long byteOffset = Platform.CHAR_ARRAY_OFFSET + ((long) offset << 1);
    long endOffset = byteOffset + ((long) vectorizedChars << 1);
    int asciiCount = 0;
    int latin1Count = 0;
    int charOffset = offset;
    for (long off = byteOffset; off < endOffset; off += 8, charOffset += 4) {
      long multiChars = Platform.getLong(chars, off);
      if ((multiChars & StringUtils.MULTI_CHARS_NON_ASCII_MASK) == 0) {
        latin1Count += 4;
        asciiCount += 4;
      } else if ((multiChars & StringUtils.MULTI_CHARS_NON_LATIN_MASK) == 0) {
        latin1Count += 4;
        for (int i = 0; i < 4; ++i) {
          if (chars[charOffset + i] < 0x80) {
            asciiCount++;
          }
        }
      } else {
        for (int i = 0; i < 4; ++i) {
          char c = chars[charOffset + i];
          if (c < 0x80) {
            latin1Count++;
            asciiCount++;
          } else if (c <= 0xFF) {
            latin1Count++;
          }
        }
      }
    }

    for (int i = vectorizedChars; i < sampleNum; i++) {
      char c = chars[offset + i];
      if (c < 0x80) {
        latin1Count++;
        asciiCount++;
      } else if (c <= 0xFF) {
        latin1Count++;
      }
    }

    if (latin1Count == count || (latin1Count == sampleNum && isLatin(chars, offset, count))) {
      return LATIN1;
    } else if (asciiCount >= sampleNum * 0.5) {
      return UTF8;
    } else {
      return UTF16;
    }
  }

  private static void handleWriteCharsUTF8UnalignedHeaderBytes(
      byte[] targetArray, int arrIndex, int diff, int written, byte stashed) {
    if (diff == 1) {
      System.arraycopy(targetArray, arrIndex + 1, targetArray, arrIndex + 2, written - 1);
      targetArray[arrIndex + 1] = stashed;
    } else {
      System.arraycopy(targetArray, arrIndex, targetArray, arrIndex - 1, written);
    }
  }

  private static void writeCharsUTF16BEToHeap(
      char[] chars, int offset, int arrIndex, int numBytes, byte[] targetArray) {
    int charIndex = offset;
    for (int i = arrIndex, end = i + numBytes; i < end; i += 2) {
      char c = chars[charIndex++];
      targetArray[i] = (byte) c;
      targetArray[i + 1] = (byte) (c >>> 8);
    }
  }

  private static int offHeapWriteCharsUTF16WithOffset(
      StringSerializer serializer,
      MemoryBuffer buffer,
      char[] chars,
      int offset,
      int writerIndex,
      int numBytes) {
    byte[] tmpArray = serializer.getByteArray(numBytes);
    Platform.UNSAFE.copyMemory(
        chars,
        Platform.CHAR_ARRAY_OFFSET + ((long) offset << 1),
        tmpArray,
        Platform.BYTE_ARRAY_OFFSET,
        numBytes);
    buffer.put(writerIndex, tmpArray, 0, numBytes);
    writerIndex += numBytes;
    return writerIndex;
  }

  private static int offHeapWriteCharsUTF16BEWithOffset(
      StringSerializer serializer,
      MemoryBuffer buffer,
      char[] chars,
      int offset,
      int writerIndex,
      int numBytes) {
    byte[] tmpArray = serializer.getByteArray(numBytes);
    int charIndex = offset;
    for (int i = 0; i < numBytes; i += 2) {
      char c = chars[charIndex++];
      tmpArray[i] = (byte) c;
      tmpArray[i + 1] = (byte) (c >>> 8);
    }
    buffer.put(writerIndex, tmpArray, 0, numBytes);
    writerIndex += numBytes;
    return writerIndex;
  }
}
