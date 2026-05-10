/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.fory.memory;

import static org.apache.fory.util.Preconditions.checkArgument;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.fory.annotation.CodegenInvoke;
import org.apache.fory.io.AbstractStreamReader;
import org.apache.fory.io.ForyStreamReader;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.UnsafeOps;
import sun.misc.Unsafe;

/**
 * A class for operations on memory managed by Fory. The buffer may be backed by heap memory (byte
 * array) or by off-heap memory. Note that the buffer can auto grow on write operations and change
 * into a heap buffer when growing.
 *
 * <p>This is a byte buffer similar class with more features:
 *
 * <ul>
 *   <li>read/write data into a chunk of direct memory.
 *   <li>additional binary compare, swap, and copy methods.
 *   <li>little-endian access.
 *   <li>independent read/write index.
 *   <li>varint int/long encoding.
 *   <li>aligned int/long encoding.
 * </ul>
 *
 * <p>Note that JVM heap buffers use this class directly so that hot methods stay monomorphic and
 * can be inlined by the just-in-time compiler. Android heap buffers use a private subclass for
 * heap-only operations, keeping Android-safe code out of ordinary JVM hot paths.
 *
 * <p>TODO(chaokunyang) Let grow/readerIndex/writerIndex handled in this class and Make immutable
 * part as separate class, and use composition in this class. In this way, all fields can be final
 * and access will be much faster.
 *
 * <p>Warning: The instance of this class should not be hold on graalvm build time, the heap unsafe
 * offset are not correct in runtime since graalvm will change array base offset.
 *
 * <p>Note(chaokunyang): Buffer operations are very common, and jvm inline and branch elimination is
 * not reliable even in c2 compiler, so we try to inline and avoid checks as we can manually. jvm
 * jit may stop inline for some reasons: NodeCountInliningCutoff,
 * DesiredMethodLimit,MaxRecursiveInlineLevel,FreqInlineSize,MaxInlineSize
 */
public class MemoryBuffer {
  public static final int BUFFER_GROW_STEP_THRESHOLD = 100 * 1024 * 1024;
  private static final Unsafe UNSAFE = AndroidSupport.IS_ANDROID ? null : UnsafeOps.UNSAFE;
  private static final boolean LITTLE_ENDIAN = NativeByteOrder.IS_LITTLE_ENDIAN;
  // Avoid copyMemory call overhead for small JVM int-array reads; larger arrays use bulk copy.
  private static final int SMALL_INT_ARRAY_DIRECT_READ_THRESHOLD = 32;

  private static final class DirectBufferAccess {
    private static final long BUFFER_ADDRESS_FIELD_OFFSET;

    static {
      try {
        Field addressField = Buffer.class.getDeclaredField("address");
        BUFFER_ADDRESS_FIELD_OFFSET = UnsafeOps.objectFieldOffset(addressField);
        checkArgument(BUFFER_ADDRESS_FIELD_OFFSET != 0);
      } catch (NoSuchFieldException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static long directBufferAddress(ByteBuffer buffer) {
    checkArgument(buffer.isDirect(), "Can't get address of a non-direct ByteBuffer.");
    try {
      return UnsafeOps.getLong(buffer, DirectBufferAccess.BUFFER_ADDRESS_FIELD_OFFSET);
    } catch (Throwable t) {
      throw new Error("Could not access direct byte buffer address field.", t);
    }
  }

  // Global allocator instance that can be customized
  private static volatile MemoryAllocator globalAllocator = new DefaultMemoryAllocator();

  // If the data in on the heap, `heapMemory` will be non-null, and its' the object relative to
  // which we access the memory.
  // If we have this buffer, we must never void this reference, or the memory buffer will point
  // to undefined addresses outside the heap and may in out-of-order execution cases cause
  // buffer faults.
  private byte[] heapMemory;
  private int heapOffset;
  // If the data is off the heap, `offHeapBuffer` will be non-null, and it's the direct byte buffer
  // that allocated on the off-heap memory.
  // This memory buffer holds a reference to that buffer, so as long as this memory buffer lives,
  // the memory will not be released.
  private ByteBuffer offHeapBuffer;
  // The readable/writeable range is [address, addressLimit).
  // If the data in on the heap, this is the relative offset to the `heapMemory` byte array.
  // If the data is off the heap, this is the absolute memory address.
  private long address;
  // The address one byte after the last addressable byte, i.e. `address + size` while the
  // buffer is not disposed.
  private long addressLimit;
  // The size in bytes of the memory buffer.
  private int size;
  private int readerIndex;
  private int writerIndex;
  private final ForyStreamReader streamReader;

  /**
   * Creates a new memory buffer that represents the memory of the byte array.
   *
   * @param buffer The byte array whose memory is represented by this memory buffer.
   * @param offset The offset of the sub array to be used; must be non-negative and no larger than
   *     <tt>array.length</tt>.
   * @param length buffer size
   */
  private MemoryBuffer(byte[] buffer, int offset, int length) {
    this(buffer, offset, length, null);
  }

  /**
   * Creates a new memory buffer that represents the memory of the byte array.
   *
   * @param buffer The byte array whose memory is represented by this memory buffer.
   * @param offset The offset of the sub array to be used; must be non-negative and no larger than
   *     <tt>array.length</tt>.
   * @param length buffer size
   * @param streamReader a reader for reading from a stream.
   */
  private MemoryBuffer(byte[] buffer, int offset, int length, ForyStreamReader streamReader) {
    checkArgument(offset >= 0 && length >= 0);
    if (offset + length > buffer.length) {
      throw new IllegalArgumentException(
          String.format("%d exceeds buffer size %d", offset + length, buffer.length));
    }
    initHeapBuffer(buffer, offset, length);
    if (streamReader != null) {
      this.streamReader = streamReader;
    } else {
      this.streamReader = new BoundChecker();
    }
  }

  /**
   * Creates a new memory buffer that represents the native memory at the absolute address given by
   * the pointer.
   *
   * @param offHeapAddress The address of the memory represented by this memory buffer.
   * @param size The size of this memory buffer.
   * @param offHeapBuffer The byte buffer whose memory is represented by this memory buffer which
   *     may be null if the memory is not allocated by `DirectByteBuffer`. Hold this buffer to avoid
   *     the memory being released.
   */
  private MemoryBuffer(long offHeapAddress, int size, ByteBuffer offHeapBuffer) {
    this(offHeapAddress, size, offHeapBuffer, null);
  }

  /**
   * Creates a new memory buffer that represents the native memory at the absolute address given by
   * the pointer.
   *
   * @param offHeapAddress The address of the memory represented by this memory buffer.
   * @param size The size of this memory buffer.
   * @param offHeapBuffer The byte buffer whose memory is represented by this memory buffer which
   *     may be null if the memory is not allocated by `DirectByteBuffer`. Hold this buffer to avoid
   *     the memory being released.
   * @param streamReader a reader for reading from a stream.
   */
  private MemoryBuffer(
      long offHeapAddress, int size, ByteBuffer offHeapBuffer, ForyStreamReader streamReader) {
    initOffHeapBuffer(offHeapAddress, size, offHeapBuffer);
    if (streamReader != null) {
      this.streamReader = streamReader;
    } else {
      this.streamReader = new BoundChecker();
    }
  }

  private void initOffHeapBuffer(long offHeapAddress, int size, ByteBuffer offHeapBuffer) {
    this.offHeapBuffer = offHeapBuffer;
    if (offHeapAddress <= 0) {
      throw new IllegalArgumentException("negative pointer or size");
    }
    if (offHeapAddress >= Long.MAX_VALUE - Integer.MAX_VALUE) {
      // this is necessary to make sure the collapsed checks are safe against numeric overflows
      throw new IllegalArgumentException(
          "Buffer initialized with too large address: "
              + offHeapAddress
              + " ; Max allowed address is "
              + (Long.MAX_VALUE - Integer.MAX_VALUE - 1));
    }

    this.heapMemory = null;
    this.address = offHeapAddress;
    this.addressLimit = this.address + size;
    this.size = size;
  }

  public void initByteBuffer(ByteBuffer buffer, int size) {
    if (buffer.isDirect()) {
      if (AndroidSupport.IS_ANDROID) {
        throw new UnsupportedOperationException(
            "Direct ByteBuffer memory is not supported on Android");
      }
      initOffHeapBuffer(directBufferAddress(buffer), size, buffer);
    } else if (buffer.hasArray()) {
      initHeapBuffer(buffer.array(), buffer.arrayOffset(), size);
    } else {
      throw new IllegalArgumentException("ByteBuffer must be direct or expose an array");
    }
  }

  private class BoundChecker extends AbstractStreamReader {
    @Override
    public int fillBuffer(int minFillSize) {
      throw new IndexOutOfBoundsException(
          String.format(
              "readerIndex(%d) + length(%d) exceeds size(%d): %s",
              readerIndex, minFillSize, size, this));
    }

    @Override
    public MemoryBuffer getBuffer() {
      return MemoryBuffer.this;
    }
  }

  public void initHeapBuffer(byte[] buffer, int offset, int length) {
    if (buffer == null) {
      throw new NullPointerException("buffer");
    }
    this.heapMemory = buffer;
    this.heapOffset = offset;
    final long startPos = AndroidSupport.IS_ANDROID ? offset : UnsafeOps.BYTE_ARRAY_OFFSET + offset;
    this.address = startPos;
    this.size = length;
    this.addressLimit = startPos + length;
  }

  // ------------------------------------------------------------------------
  // Memory buffer Operations
  // ------------------------------------------------------------------------

  /**
   * Gets the size of the memory buffer, in bytes.
   *
   * @return The size of the memory buffer.
   */
  public int size() {
    return size;
  }

  public void increaseSize(int diff) {
    this.addressLimit = address + (size += diff);
  }

  /**
   * Checks whether this memory buffer is backed by off-heap memory.
   *
   * @return <tt>true</tt>, if the memory buffer is backed by off-heap memory, <tt>false</tt> if it
   *     is backed by heap memory.
   */
  public boolean isOffHeap() {
    return heapMemory == null;
  }

  /**
   * Returns <tt>true</tt>, if the memory buffer is backed by heap memory and memory buffer can
   * write to the whole memory region of underlying byte array.
   */
  public boolean isHeapFullyWriteable() {
    return heapMemory != null && heapOffset == 0;
  }

  /**
   * Get the heap byte array object.
   *
   * @return Return non-null if the memory is on the heap, and return null, if the memory if off the
   *     heap.
   */
  public byte[] getHeapMemory() {
    return heapMemory;
  }

  /**
   * Gets the buffer that owns the memory of this memory buffer.
   *
   * @return The byte buffer that owns the memory of this memory buffer.
   */
  public ByteBuffer getOffHeapBuffer() {
    if (offHeapBuffer != null) {
      return offHeapBuffer;
    } else {
      throw new IllegalStateException("Memory buffer does not represent off heap ByteBuffer");
    }
  }

  /**
   * Returns the byte array of on-heap memory buffers.
   *
   * @return underlying byte array
   * @throws IllegalStateException if the memory buffer does not represent on-heap memory
   */
  public byte[] getArray() {
    if (heapMemory != null) {
      return heapMemory;
    } else {
      throw new IllegalStateException("Memory buffer does not represent heap memory");
    }
  }

  // ------------------------------------------------------------------------
  //                    Random Access get() and put() methods
  // ------------------------------------------------------------------------

  private void checkPosition(long index, long pos, long length) {
    if (BoundsChecking.BOUNDS_CHECKING_ENABLED) {
      if (index < 0 || pos > addressLimit - length) {
        throwOOBException();
      }
    }
  }

  private int androidHeapIndex(int index, int length) {
    checkAndroidHeapBuffer();
    if (BoundsChecking.BOUNDS_CHECKING_ENABLED) {
      if (index < 0 || index > size - length) {
        throwOOBException();
      }
    }
    return heapOffset + index;
  }

  private int androidHeapIndexUnchecked(int index) {
    checkAndroidHeapBuffer();
    return heapOffset + index;
  }

  private static byte[] androidHeapMemory(MemoryBuffer buffer) {
    return buffer.heapMemory;
  }

  private static int androidHeapIndex(MemoryBuffer buffer, int index, int length) {
    return buffer.androidHeapIndex(index, length);
  }

  private static int androidHeapIndexUnchecked(MemoryBuffer buffer, int index) {
    return buffer.androidHeapIndexUnchecked(index);
  }

  private static int androidWriterIndex(MemoryBuffer buffer) {
    return buffer.writerIndex;
  }

  private static void androidWriterIndex(MemoryBuffer buffer, int writerIndex) {
    buffer.writerIndex = writerIndex;
  }

  private static int androidSize(MemoryBuffer buffer) {
    return buffer.size;
  }

  private static int androidHeapOffset(MemoryBuffer buffer) {
    return buffer.heapOffset;
  }

  private static void throwOOBExceptionForRange(MemoryBuffer buffer, int offset, int length) {
    buffer.throwOOBExceptionForRange(offset, length);
  }

  private void checkAndroidHeapBuffer() {
    if (heapMemory == null) {
      throw new UnsupportedOperationException("Direct memory access is not supported on Android");
    }
  }

  public void get(int index, byte[] dst) {
    get(index, dst, 0, dst.length);
  }

  public void get(int index, byte[] dst, int offset, int length) {
    final byte[] heapMemory = this.heapMemory;
    if (heapMemory != null) {
      // System.arraycopy faster for some jdk than Unsafe.
      System.arraycopy(heapMemory, heapOffset + index, dst, offset, length);
    } else {
      final long pos = address + index;
      if ((index
              | offset
              | length
              | (offset + length)
              | (dst.length - (offset + length))
              | addressLimit - length - pos)
          < 0) {
        throwOOBException();
      }
      UnsafeOps.copyMemory(null, pos, dst, UnsafeOps.BYTE_ARRAY_OFFSET + offset, length);
    }
  }

  public void get(int offset, ByteBuffer target, int numBytes) {
    if ((offset | numBytes | (offset + numBytes)) < 0) {
      throwOOBException();
    }
    if (target.remaining() < numBytes) {
      throwOOBException();
    }
    if (target.isReadOnly()) {
      throw new IllegalArgumentException("read only buffer");
    }
    final int targetPos = target.position();
    if (AndroidSupport.IS_ANDROID) {
      checkAndroidHeapBuffer();
      if (offset <= size - numBytes) {
        target.put(heapMemory, heapOffset + offset, numBytes);
      } else {
        throwOOBException();
      }
    } else if (target.isDirect()) {
      final long targetAddr = directBufferAddress(target) + targetPos;
      final long sourceAddr = address + offset;
      if (sourceAddr <= addressLimit - numBytes) {
        UnsafeOps.copyMemory(heapMemory, sourceAddr, null, targetAddr, numBytes);
      } else {
        throwOOBException();
      }
    } else {
      assert target.hasArray();
      get(offset, target.array(), targetPos + target.arrayOffset(), numBytes);
    }
    if (target.position() == targetPos) {
      ByteBufferUtil.position(target, targetPos + numBytes);
    }
  }

  public void put(int offset, ByteBuffer source, int numBytes) {
    final int remaining = source.remaining();
    if ((offset | numBytes | (offset + numBytes) | (remaining - numBytes)) < 0) {
      throwOOBException();
    }
    final int sourcePos = source.position();
    if (AndroidSupport.IS_ANDROID) {
      checkAndroidHeapBuffer();
      if (offset <= size - numBytes) {
        source.get(heapMemory, heapOffset + offset, numBytes);
      } else {
        throwOOBException();
      }
    } else if (source.isDirect()) {
      final long sourceAddr = directBufferAddress(source) + sourcePos;
      final long targetAddr = address + offset;
      if (targetAddr <= addressLimit - numBytes) {
        UnsafeOps.copyMemory(null, sourceAddr, heapMemory, targetAddr, numBytes);
      } else {
        throwOOBException();
      }
    } else {
      assert source.hasArray();
      put(offset, source.array(), sourcePos + source.arrayOffset(), numBytes);
    }
    if (source.position() == sourcePos) {
      ByteBufferUtil.position(source, sourcePos + numBytes);
    }
  }

  public void put(int index, byte[] src) {
    put(index, src, 0, src.length);
  }

  public void put(int index, byte[] src, int offset, int length) {
    final byte[] heapMemory = this.heapMemory;
    if (heapMemory != null) {
      // System.arraycopy faster for some jdk than Unsafe.
      System.arraycopy(src, offset, heapMemory, heapOffset + index, length);
    } else {
      final long pos = address + index;
      // check the byte array offset and length
      if ((index
              | offset
              | length
              | (offset + length)
              | (src.length - (offset + length))
              | addressLimit - length - pos)
          < 0) {
        throwOOBException();
      }
      final long arrayAddress = UnsafeOps.BYTE_ARRAY_OFFSET + offset;
      UnsafeOps.copyMemory(src, arrayAddress, null, pos, length);
    }
  }

  public byte getByte(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getByte(heapMemory, androidHeapIndex(index, 1));
    }
    final long pos = address + index;
    checkPosition(index, pos, 1);
    return UNSAFE.getByte(heapMemory, pos);
  }

  // CHECKSTYLE.OFF:MethodName
  public byte _unsafeGetByte(int index) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getByte(heapMemory, androidHeapIndexUnchecked(index));
    }
    return UNSAFE.getByte(heapMemory, address + index);
  }

  public void putByte(int index, int b) {
    final long pos = address + index;
    checkPosition(index, pos, 1);
    UNSAFE.putByte(heapMemory, pos, (byte) b);
  }

  public void putByte(int index, byte b) {
    final long pos = address + index;
    checkPosition(index, pos, 1);
    UNSAFE.putByte(heapMemory, pos, b);
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutByte(int index, byte b) {
    // CHECKSTYLE.ON:MethodName
    UNSAFE.putByte(heapMemory, address + index, b);
  }

  public boolean getBoolean(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getBoolean(heapMemory, androidHeapIndex(index, 1));
    }
    final long pos = address + index;
    checkPosition(index, pos, 1);
    return UNSAFE.getByte(heapMemory, pos) != 0;
  }

  // CHECKSTYLE.OFF:MethodName
  public boolean _unsafeGetBoolean(int index) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getBoolean(heapMemory, androidHeapIndexUnchecked(index));
    }
    return UNSAFE.getByte(heapMemory, address + index) != 0;
  }

  public void putBoolean(int index, boolean value) {
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putBoolean(heapMemory, androidHeapIndexUnchecked(index), value);
      return;
    }
    UNSAFE.putByte(heapMemory, address + index, (value ? (byte) 1 : (byte) 0));
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutBoolean(int index, boolean value) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putBoolean(heapMemory, androidHeapIndexUnchecked(index), value);
      return;
    }
    UNSAFE.putByte(heapMemory, address + index, (value ? (byte) 1 : (byte) 0));
  }

  public char getChar(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return (char) MemoryOps.getInt16(heapMemory, androidHeapIndex(index, 2));
    }
    final long pos = address + index;
    checkPosition(index, pos, 2);
    char c = UNSAFE.getChar(heapMemory, pos);
    return LITTLE_ENDIAN ? c : Character.reverseBytes(c);
  }

  // CHECKSTYLE.OFF:MethodName
  public char _unsafeGetChar(int index) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return (char) MemoryOps.getInt16(heapMemory, androidHeapIndexUnchecked(index));
    }
    char c = UNSAFE.getChar(heapMemory, address + index);
    return LITTLE_ENDIAN ? c : Character.reverseBytes(c);
  }

  public void putChar(int index, char value) {
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putInt16(heapMemory, androidHeapIndex(index, 2), (short) value);
      return;
    }
    final long pos = address + index;
    checkPosition(index, pos, 2);
    if (!LITTLE_ENDIAN) {
      value = Character.reverseBytes(value);
    }
    UNSAFE.putChar(heapMemory, pos, value);
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutChar(int index, char value) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putInt16(heapMemory, androidHeapIndexUnchecked(index), (short) value);
      return;
    }
    if (!LITTLE_ENDIAN) {
      value = Character.reverseBytes(value);
    }
    UNSAFE.putChar(heapMemory, address + index, value);
  }

  public short getInt16(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt16(heapMemory, androidHeapIndex(index, 2));
    }
    final long pos = address + index;
    checkPosition(index, pos, 2);
    short v = UNSAFE.getShort(heapMemory, pos);
    return LITTLE_ENDIAN ? v : Short.reverseBytes(v);
  }

  public void putInt16(int index, short value) {
    final long pos = address + index;
    checkPosition(index, pos, 2);
    if (!LITTLE_ENDIAN) {
      value = Short.reverseBytes(value);
    }
    UNSAFE.putShort(heapMemory, pos, value);
  }

  // CHECKSTYLE.OFF:MethodName
  public short _unsafeGetInt16(int index) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt16(heapMemory, androidHeapIndexUnchecked(index));
    }
    short v = UNSAFE.getShort(heapMemory, address + index);
    return LITTLE_ENDIAN ? v : Short.reverseBytes(v);
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutInt16(int index, short value) {
    // CHECKSTYLE.ON:MethodName
    if (!LITTLE_ENDIAN) {
      value = Short.reverseBytes(value);
    }
    UNSAFE.putShort(heapMemory, address + index, value);
  }

  public int getInt32(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt32(heapMemory, androidHeapIndex(index, 4));
    }
    final long pos = address + index;
    checkPosition(index, pos, 4);
    int v = UNSAFE.getInt(heapMemory, pos);
    return LITTLE_ENDIAN ? v : Integer.reverseBytes(v);
  }

  public void putInt32(int index, int value) {
    final long pos = address + index;
    checkPosition(index, pos, 4);
    if (!LITTLE_ENDIAN) {
      value = Integer.reverseBytes(value);
    }
    UNSAFE.putInt(heapMemory, pos, value);
  }

  // CHECKSTYLE.OFF:MethodName
  public int _unsafeGetInt32(int index) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt32(heapMemory, androidHeapIndexUnchecked(index));
    }
    int v = UNSAFE.getInt(heapMemory, address + index);
    return LITTLE_ENDIAN ? v : Integer.reverseBytes(v);
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutInt32(int index, int value) {
    // CHECKSTYLE.ON:MethodName
    if (!LITTLE_ENDIAN) {
      value = Integer.reverseBytes(value);
    }
    UNSAFE.putInt(heapMemory, address + index, value);
  }

  public long getInt64(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt64(heapMemory, androidHeapIndex(index, 8));
    }
    final long pos = address + index;
    checkPosition(index, pos, 8);
    long v = UNSAFE.getLong(heapMemory, pos);
    return LITTLE_ENDIAN ? v : Long.reverseBytes(v);
  }

  public void putInt64(int index, long value) {
    final long pos = address + index;
    checkPosition(index, pos, 8);
    if (!LITTLE_ENDIAN) {
      value = Long.reverseBytes(value);
    }
    UNSAFE.putLong(heapMemory, pos, value);
  }

  // CHECKSTYLE.OFF:MethodName
  public long _unsafeGetInt64(int index) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt64(heapMemory, androidHeapIndexUnchecked(index));
    }
    long v = UNSAFE.getLong(heapMemory, address + index);
    return LITTLE_ENDIAN ? v : Long.reverseBytes(v);
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutInt64(int index, long value) {
    // CHECKSTYLE.ON:MethodName
    if (!LITTLE_ENDIAN) {
      value = Long.reverseBytes(value);
    }
    UNSAFE.putLong(heapMemory, address + index, value);
  }

  public float getFloat32(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat32(heapMemory, androidHeapIndex(index, 4));
    }
    final long pos = address + index;
    checkPosition(index, pos, 8);
    int v = UNSAFE.getInt(heapMemory, pos);
    if (!LITTLE_ENDIAN) {
      v = Integer.reverseBytes(v);
    }
    return Float.intBitsToFloat(v);
  }

  // CHECKSTYLE.OFF:MethodName
  public float _unsafeGetFloat32(int index) {
    // CHECKSTYLE.ON:MethodName
    return Float.intBitsToFloat(_unsafeGetInt32(index));
  }

  public void putFloat32(int index, float value) {
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putFloat32(heapMemory, androidHeapIndex(index, 4), value);
      return;
    }
    final long pos = address + index;
    checkPosition(index, pos, 4);
    int v = Float.floatToRawIntBits(value);
    if (!LITTLE_ENDIAN) {
      v = Integer.reverseBytes(v);
    }
    UNSAFE.putInt(heapMemory, pos, v);
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutFloat32(int index, float value) {
    // CHECKSTYLE.ON:MethodName
    _unsafePutInt32(index, Float.floatToRawIntBits(value));
  }

  public double getFloat64(int index) {
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat64(heapMemory, androidHeapIndex(index, 8));
    }
    final long pos = address + index;
    checkPosition(index, pos, 8);
    long v = UNSAFE.getLong(heapMemory, pos);
    if (!LITTLE_ENDIAN) {
      v = Long.reverseBytes(v);
    }
    return Double.longBitsToDouble(v);
  }

  // CHECKSTYLE.OFF:MethodName
  public double _unsafeGetFloat64(int index) {
    // CHECKSTYLE.ON:MethodName
    return Double.longBitsToDouble(_unsafeGetInt64(index));
  }

  public void putFloat64(int index, double value) {
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putFloat64(heapMemory, androidHeapIndex(index, 8), value);
      return;
    }
    final long pos = address + index;
    checkPosition(index, pos, 8);
    long v = Double.doubleToRawLongBits(value);
    if (!LITTLE_ENDIAN) {
      v = Long.reverseBytes(v);
    }
    UNSAFE.putLong(heapMemory, pos, v);
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafePutFloat64(int index, double value) {
    // CHECKSTYLE.ON:MethodName
    _unsafePutInt64(index, Double.doubleToRawLongBits(value));
  }

  // Check should be done outside to avoid this method got into the critical path.
  private void throwOOBException() {
    throw new IndexOutOfBoundsException(
        String.format("size: %d, address %s, addressLimit %d", size, address, addressLimit));
  }

  // -------------------------------------------------------------------------
  //                          Write Methods
  // -------------------------------------------------------------------------

  /** Returns the {@code writerIndex} of this buffer. */
  public int writerIndex() {
    return writerIndex;
  }

  /**
   * Sets the {@code writerIndex} of this buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code writerIndex} is less than {@code 0}
   *     or greater than {@code this.size}
   */
  public void writerIndex(int writerIndex) {
    if (writerIndex < 0 || writerIndex > size) {
      throwOOBExceptionForWriteIndex(writerIndex);
    }
    this.writerIndex = writerIndex;
  }

  private void throwOOBExceptionForWriteIndex(int writerIndex) {
    throw new IndexOutOfBoundsException(
        String.format(
            "writerIndex: %d (expected: 0 <= writerIndex <= size(%d))", writerIndex, size));
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafeWriterIndex(int writerIndex) {
    // CHECKSTYLE.ON:MethodName
    this.writerIndex = writerIndex;
  }

  /** Returns heap index for writer index if buffer is a heap buffer. */
  // CHECKSTYLE.OFF:MethodName
  public int _unsafeHeapWriterIndex() {
    // CHECKSTYLE.ON:MethodName
    return writerIndex + heapOffset;
  }

  // CHECKSTYLE.OFF:MethodName
  public void _increaseWriterIndexUnsafe(int diff) {
    // CHECKSTYLE.ON:MethodName
    this.writerIndex = writerIndex + diff;
  }

  /** Increase writer index and grow buffer if needed. */
  public void increaseWriterIndex(int diff) {
    int writerIdx = writerIndex + diff;
    ensure(writerIdx);
    this.writerIndex = writerIdx;
  }

  public void writeBoolean(boolean value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 1;
    ensure(newIdx);
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, (byte) (value ? 1 : 0));
    writerIndex = newIdx;
  }

  // CHECKSTYLE.OFF:MethodName
  public void _unsafeWriteByte(byte value) {
    // CHECKSTYLE.ON:MethodName
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 1;
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, value);
    writerIndex = newIdx;
  }

  public void writeUInt8(int value) {
    writeByte((byte) value);
  }

  public void writeByte(byte value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 1;
    ensure(newIdx);
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, value);
    writerIndex = newIdx;
  }

  public void writeByte(int value) {
    writeByte((byte) value);
  }

  public void writeChar(char value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 2;
    ensure(newIdx);
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putInt16(heapMemory, androidHeapIndexUnchecked(writerIdx), (short) value);
      writerIndex = newIdx;
      return;
    }
    final long pos = address + writerIdx;
    if (!LITTLE_ENDIAN) {
      value = Character.reverseBytes(value);
    }
    UNSAFE.putChar(heapMemory, pos, value);
    writerIndex = newIdx;
  }

  public void writeInt16(short value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 2;
    ensure(newIdx);
    if (!LITTLE_ENDIAN) {
      value = Short.reverseBytes(value);
    }
    UNSAFE.putShort(heapMemory, address + writerIdx, value);
    writerIndex = newIdx;
  }

  public void writeInt32(int value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 4;
    ensure(newIdx);
    if (!LITTLE_ENDIAN) {
      value = Integer.reverseBytes(value);
    }
    UNSAFE.putInt(heapMemory, address + writerIdx, value);
    writerIndex = newIdx;
  }

  public void writeInt64(long value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 8;
    ensure(newIdx);
    if (!LITTLE_ENDIAN) {
      value = Long.reverseBytes(value);
    }
    UNSAFE.putLong(heapMemory, address + writerIdx, value);
    writerIndex = newIdx;
  }

  public void writeFloat32(float value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 4;
    ensure(newIdx);
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putFloat32(heapMemory, androidHeapIndexUnchecked(writerIdx), value);
      writerIndex = newIdx;
      return;
    }
    int v = Float.floatToRawIntBits(value);
    if (!LITTLE_ENDIAN) {
      v = Integer.reverseBytes(v);
    }
    UNSAFE.putInt(heapMemory, address + writerIdx, v);
    writerIndex = newIdx;
  }

  public void writeFloat64(double value) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + 8;
    ensure(newIdx);
    if (AndroidSupport.IS_ANDROID) {
      MemoryOps.putFloat64(heapMemory, androidHeapIndexUnchecked(writerIdx), value);
      writerIndex = newIdx;
      return;
    }
    long v = Double.doubleToRawLongBits(value);
    if (!LITTLE_ENDIAN) {
      v = Long.reverseBytes(v);
    }
    UNSAFE.putLong(heapMemory, address + writerIdx, v);
    writerIndex = newIdx;
  }

  /**
   * Write int using variable length encoding. If the value is positive, use {@link #writeVarUInt32}
   * to save one bit.
   */
  public int writeVarInt32(int v) {
    ensure(writerIndex + 8);
    // Zigzag encoding: maps negative values to positive values
    // This works entirely in int without conversion to long
    int varintBytes = _unsafePutVarUInt32(writerIndex, (v << 1) ^ (v >> 31));
    writerIndex += varintBytes;
    return varintBytes;
  }

  /**
   * For implementation efficiency, this method needs at most 8 bytes for writing 5 bytes using long
   * to avoid using two memory operations.
   */
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public int _unsafeWriteVarInt32(int v) {
    // CHECKSTYLE.ON:MethodName
    // Zigzag encoding ensures negatives close to zero are encoded in few bytes
    int varintBytes = _unsafePutVarUInt32(writerIndex, (v << 1) ^ (v >> 31));
    writerIndex += varintBytes;
    return varintBytes;
  }

  /**
   * Writes a 1-5 byte int.
   *
   * @return The number of bytes written.
   */
  public int writeVarUInt32(int v) {
    // ensure at least 8 bytes are writable at once, so jvm-jit
    // generated code is smaller. Otherwise, the reference writer fast path
    // may be `callee is too large`/`already compiled into a big method`
    ensure(writerIndex + 8);
    int varintBytes = _unsafePutVarUInt32(writerIndex, v);
    writerIndex += varintBytes;
    return varintBytes;
  }

  /**
   * For implementation efficiency, this method needs at most 8 bytes for writing 5 bytes using long
   * to avoid using two memory operations.
   */
  // CHECKSTYLE.OFF:MethodName
  public int _unsafeWriteVarUInt32(int v) {
    // CHECKSTYLE.ON:MethodName
    int varintBytes = _unsafePutVarUInt32(writerIndex, v);
    writerIndex += varintBytes;
    return varintBytes;
  }

  /**
   * Fast method for write an unsigned varint which is mostly a small value in 7 bits value in [0,
   * 127). When the value is equal or greater than 127, the write will be a little slower.
   */
  public int writeVarUInt32Small7(int value) {
    ensure(writerIndex + 8);
    if (value >>> 7 == 0) {
      UNSAFE.putByte(heapMemory, address + writerIndex++, (byte) value);
      return 1;
    }
    return continueWriteVarUInt32Small7(value);
  }

  private int continueWriteVarUInt32Small7(int value) {
    int encoded = (value & 0x7F);
    encoded |= (((value & 0x3f80) << 1) | 0x80);
    int writerIdx = writerIndex;
    if (value >>> 14 == 0) {
      _unsafePutInt32(writerIdx, encoded);
      writerIndex += 2;
      return 2;
    }
    int diff = continuePutVarUInt32(writerIdx, encoded, value);
    writerIndex += diff;
    return diff;
  }

  /**
   * Writes an unsigned 32-bit varint at the given index using int operations. Caller must ensure
   * there are at least 8 bytes available for writing. This method avoids int-to-long conversion
   * overhead for the common cases (1-4 bytes).
   *
   * @param index the position to write at
   * @param value the unsigned 32-bit value (high bit may be set)
   * @return the number of bytes written (1-5)
   */
  // CHECKSTYLE.OFF:MethodName
  public int _unsafePutVarUInt32(int index, int value) {
    // CHECKSTYLE.ON:MethodName
    int encoded = (value & 0x7F);
    if (value >>> 7 == 0) {
      UNSAFE.putByte(heapMemory, address + index, (byte) value);
      return 1;
    }
    // bit 8 `set` indicates have next data bytes.
    // 0x3f80: 0b1111111 << 7
    encoded |= (((value & 0x3f80) << 1) | 0x80);
    if (value >>> 14 == 0) {
      _unsafePutInt32(index, encoded);
      return 2;
    }
    return continuePutVarUInt32(index, encoded, value);
  }

  private int continuePutVarUInt32(int index, int encoded, int value) {
    // 0x1fc000: 0b1111111 << 14
    encoded |= (((value & 0x1fc000) << 2) | 0x8000);
    if (value >>> 21 == 0) {
      _unsafePutInt32(index, encoded);
      return 3;
    }
    // 0xfe00000: 0b1111111 << 21
    encoded |= ((value & 0xfe00000) << 3) | 0x800000;
    if (value >>> 28 == 0) {
      _unsafePutInt32(index, encoded);
      return 4;
    }
    // 5-byte case: bits 28-31 go to the 5th byte
    // Need long for the final write to include the 5th byte
    long encodedLong = Integer.toUnsignedLong(encoded) | 0x80000000L;
    encodedLong |= (long) (value >>> 28) << 32;
    _unsafePutInt64(index, encodedLong);
    return 5;
  }

  /**
   * Caller must ensure there must be at least 8 bytes for writing, otherwise the crash may occur.
   * Don't pass int value to avoid sign extension.
   */
  // CHECKSTYLE.OFF:MethodName
  public int _unsafePutVarUint36Small(int index, long value) {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.putVarUint36Small(heapMemory, androidHeapIndexUnchecked(index), value);
    }
    long encoded = (value & 0x7F);
    if (value >>> 7 == 0) {
      UNSAFE.putByte(heapMemory, address + index, (byte) value);
      return 1;
    }
    // bit 8 `set` indicates have next data bytes.
    // 0x3f80: 0b1111111 << 7
    encoded |= (((value & 0x3f80) << 1) | 0x80);
    if (value >>> 14 == 0) {
      _unsafePutInt32(index, (int) encoded);
      return 2;
    }
    return continuePutVarInt36(index, encoded, value);
  }

  private int continuePutVarInt36(int index, long encoded, long value) {
    // 0x1fc000: 0b1111111 << 14
    encoded |= (((value & 0x1fc000) << 2) | 0x8000);
    if (value >>> 21 == 0) {
      _unsafePutInt32(index, (int) encoded);
      return 3;
    }
    // 0xfe00000: 0b1111111 << 21
    encoded |= ((value & 0xfe00000) << 3) | 0x800000;
    if (value >>> 28 == 0) {
      _unsafePutInt32(index, (int) encoded);
      return 4;
    }
    // 0xff0000000: 0b11111111 << 28. Note eight `1` here instead of seven.
    encoded |= ((value & 0xff0000000L) << 4) | 0x80000000L;
    _unsafePutInt64(index, encoded);
    return 5;
  }

  /**
   * Writes a 1-9 byte int, padding necessary bytes to align `writerIndex` to 4-byte.
   *
   * @return The number of bytes written.
   */
  public int writeVarUInt32Aligned(int value) {
    if (AndroidSupport.IS_ANDROID) {
      return writeVarUInt32AlignedAndroid(value);
    }
    // Mask first 6 bits,
    // bit 7 `unset` indicates have next padding bytes,
    // bit 8 `set` indicates have next data bytes.
    if (value >>> 6 == 0) {
      return writeVarUInt32Aligned1(value);
    }
    if (value >>> 12 == 0) { // 2 byte data
      return writeVarUInt32Aligned2(value);
    }
    if (value >>> 18 == 0) { // 3 byte data
      return writeVarUInt32Aligned3(value);
    }
    if (value >>> 24 == 0) { // 4 byte data
      return writeVarUInt32Aligned4(value);
    }
    if (value >>> 30 == 0) { // 5 byte data
      return writeVarUInt32Aligned5(value);
    }
    // 6 byte data
    return writeVarUInt32Aligned6(value);
  }

  private int writeVarUInt32AlignedAndroid(int value) {
    final int writerIdx = writerIndex;
    ensure(writerIdx + 10);
    final byte[] heapMemory = this.heapMemory;
    int heapIdx = androidHeapIndexUnchecked(writerIdx);
    int dataBytes = 1;
    int shifted = value >>> 6;
    while (shifted != 0) {
      dataBytes++;
      shifted >>>= 6;
    }
    for (int i = 0; i < dataBytes; i++) {
      int b = (value >>> (i * 6)) & 0x3F;
      if (i != dataBytes - 1) {
        b |= 0x80;
      }
      MemoryOps.putByte(heapMemory, heapIdx + i, (byte) b);
    }
    int paddingBytes = (4 - ((writerIdx + dataBytes) & 0x3)) & 0x3;
    int bytesWritten = dataBytes + paddingBytes;
    if (paddingBytes == 0) {
      MemoryOps.putByte(
          heapMemory,
          heapIdx + dataBytes - 1,
          (byte) (MemoryOps.getByte(heapMemory, heapIdx + dataBytes - 1) | 0x40));
    } else {
      for (int i = 0; i < paddingBytes - 1; i++) {
        MemoryOps.putByte(heapMemory, heapIdx + dataBytes + i, (byte) 0);
      }
      MemoryOps.putByte(heapMemory, heapIdx + bytesWritten - 1, (byte) 0x40);
    }
    writerIndex = writerIdx + bytesWritten;
    return bytesWritten;
  }

  private int writeVarUInt32Aligned1(int value) {
    final int writerIdx = writerIndex;
    int numPaddingBytes = 4 - writerIdx % 4;
    ensure(writerIdx + 5); // 1 byte + 4 bytes(zero out), padding range in (zero out)
    int first = (value & 0x3F);
    final long pos = address + writerIdx;
    if (numPaddingBytes == 1) {
      // bit 7 `set` indicates not have padding bytes.
      // bit 8 `set` indicates have next data bytes.
      UNSAFE.putByte(heapMemory, pos, (byte) (first | 0x40));
      writerIndex = (writerIdx + 1);
      return 1;
    } else {
      UNSAFE.putByte(heapMemory, pos, (byte) first);
      // zero out 4 bytes, so that `bit 7` value can be trusted.
      UNSAFE.putInt(heapMemory, pos + 1, 0);
      UNSAFE.putByte(heapMemory, pos + numPaddingBytes - 1, (byte) (0x40));
      writerIndex = writerIdx + numPaddingBytes;
      return numPaddingBytes;
    }
  }

  private int writeVarUInt32Aligned2(int value) {
    final int writerIdx = writerIndex;
    int numPaddingBytes = 4 - writerIdx % 4;
    ensure(writerIdx + 6); // 2 byte + 4 bytes(zero out), padding range in (zero out)
    int first = (value & 0x3F);
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, (byte) (first | 0x80));
    if (numPaddingBytes == 2) {
      // bit 7 `set` indicates not have padding bytes.
      // bit 8 `set` indicates have next data bytes.
      UNSAFE.putByte(heapMemory, pos + 1, (byte) ((value >>> 6) | 0x40));
      writerIndex = writerIdx + 2;
      return 2;
    } else {
      UNSAFE.putByte(heapMemory, pos + 1, (byte) (value >>> 6));
      // zero out 4 bytes, so that `bit 7` value can be trusted.
      UNSAFE.putInt(heapMemory, pos + 2, 0);
      if (numPaddingBytes > 2) {
        UNSAFE.putByte(heapMemory, pos + numPaddingBytes - 1, (byte) (0x40));
        writerIndex = writerIdx + numPaddingBytes;
        return numPaddingBytes;
      } else {
        UNSAFE.putByte(heapMemory, pos + 4, (byte) (0x40));
        writerIndex = writerIdx + numPaddingBytes + 4;
        return numPaddingBytes + 4;
      }
    }
  }

  private int writeVarUInt32Aligned3(int value) {
    final int writerIdx = writerIndex;
    int numPaddingBytes = 4 - writerIdx % 4;
    ensure(writerIdx + 7); // 3 byte + 4 bytes(zero out), padding range in (zero out)
    int first = (value & 0x3F);
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, (byte) (first | 0x80));
    UNSAFE.putByte(heapMemory, pos + 1, (byte) ((value >>> 6) | 0x80));
    if (numPaddingBytes == 3) {
      // bit 7 `set` indicates not have padding bytes.
      // bit 8 `set` indicates have next data bytes.
      UNSAFE.putByte(heapMemory, pos + 2, (byte) ((value >>> 12) | 0x40));
      writerIndex = writerIdx + 3;
      return 3;
    } else {
      UNSAFE.putByte(heapMemory, pos + 2, (byte) (value >>> 12));
      // zero out 4 bytes, so that `bit 7` value can be trusted.
      UNSAFE.putInt(heapMemory, pos + 3, 0);
      if (numPaddingBytes == 4) {
        UNSAFE.putByte(heapMemory, pos + numPaddingBytes - 1, (byte) (0x40));
        writerIndex = writerIdx + numPaddingBytes;
        return numPaddingBytes;
      } else {
        UNSAFE.putByte(heapMemory, pos + numPaddingBytes + 3, (byte) (0x40));
        writerIndex = writerIdx + numPaddingBytes + 4;
        return numPaddingBytes + 4;
      }
    }
  }

  private int writeVarUInt32Aligned4(int value) {
    final int writerIdx = writerIndex;
    int numPaddingBytes = 4 - writerIdx % 4;
    ensure(writerIdx + 8); // 4 byte + 4 bytes(zero out), padding range in (zero out)
    int first = (value & 0x3F);
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, (byte) (first | 0x80));
    UNSAFE.putByte(heapMemory, pos + 1, (byte) (value >>> 6 | 0x80));
    UNSAFE.putByte(heapMemory, pos + 2, (byte) (value >>> 12 | 0x80));
    if (numPaddingBytes == 4) {
      // bit 7 `set` indicates not have padding bytes.
      // bit 8 `set` indicates have next data bytes.
      UNSAFE.putByte(heapMemory, pos + 3, (byte) ((value >>> 18) | 0x40));
      writerIndex = writerIdx + 4;
      return 4;
    } else {
      UNSAFE.putByte(heapMemory, pos + 3, (byte) (value >>> 18));
      // zero out 4 bytes, so that `bit 7` value can be trusted.
      UNSAFE.putInt(heapMemory, pos + 4, 0);
      UNSAFE.putByte(heapMemory, pos + numPaddingBytes + 3, (byte) (0x40));
      writerIndex = writerIdx + numPaddingBytes + 4;
      return numPaddingBytes + 4;
    }
  }

  private int writeVarUInt32Aligned5(int value) {
    final int writerIdx = writerIndex;
    int numPaddingBytes = 4 - writerIdx % 4;
    ensure(writerIdx + 9); // 5 byte + 4 bytes(zero out), padding range in (zero out)
    int first = (value & 0x3F);
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, (byte) (first | 0x80));
    UNSAFE.putByte(heapMemory, pos + 1, (byte) (value >>> 6 | 0x80));
    UNSAFE.putByte(heapMemory, pos + 2, (byte) (value >>> 12 | 0x80));
    UNSAFE.putByte(heapMemory, pos + 3, (byte) (value >>> 18 | 0x80));
    if (numPaddingBytes == 1) {
      // bit 7 `set` indicates not have padding bytes.
      // bit 8 `set` indicates have next data bytes.
      UNSAFE.putByte(heapMemory, pos + 4, (byte) ((value >>> 24) | 0x40));
      writerIndex = writerIdx + 5;
      return 5;
    } else {
      UNSAFE.putByte(heapMemory, pos + 4, (byte) (value >>> 24));
      // zero out 4 bytes, so that `bit 7` value can be trusted.
      UNSAFE.putInt(heapMemory, pos + 5, 0);
      UNSAFE.putByte(heapMemory, pos + numPaddingBytes + 3, (byte) (0x40));
      writerIndex = writerIdx + numPaddingBytes + 4;
      return numPaddingBytes + 4;
    }
  }

  private int writeVarUInt32Aligned6(int value) {
    final int writerIdx = writerIndex;
    int numPaddingBytes = 4 - writerIdx % 4;
    ensure(writerIdx + 10); // 6 byte + 4 bytes(zero out), padding range in (zero out)
    int first = (value & 0x3F);
    final long pos = address + writerIdx;
    UNSAFE.putByte(heapMemory, pos, (byte) (first | 0x80));
    UNSAFE.putByte(heapMemory, pos + 1, (byte) (value >>> 6 | 0x80));
    UNSAFE.putByte(heapMemory, pos + 2, (byte) (value >>> 12 | 0x80));
    UNSAFE.putByte(heapMemory, pos + 3, (byte) (value >>> 18 | 0x80));
    UNSAFE.putByte(heapMemory, pos + 4, (byte) (value >>> 24 | 0x80));
    if (numPaddingBytes == 2) {
      // bit 7 `set` indicates not have padding bytes.
      // bit 8 `set` indicates have next data bytes.
      UNSAFE.putByte(heapMemory, pos + 5, (byte) ((value >>> 30) | 0x40));
      writerIndex = writerIdx + 6;
      return 6;
    } else {
      UNSAFE.putByte(heapMemory, pos + 5, (byte) (value >>> 30));
      // zero out 4 bytes, so that `bit 7` value can be trusted.
      UNSAFE.putInt(heapMemory, pos + 6, 0);
      if (numPaddingBytes == 1) {
        UNSAFE.putByte(heapMemory, pos + 8, (byte) (0x40));
        writerIndex = writerIdx + 9;
        return 9;
      } else {
        UNSAFE.putByte(heapMemory, pos + numPaddingBytes + 3, (byte) (0x40));
        writerIndex = writerIdx + numPaddingBytes + 4;
        return numPaddingBytes + 4;
      }
    }
  }

  /**
   * Write long using variable length encoding. If the value is positive, use {@link
   * #writeVarUInt64} to save one bit.
   */
  public int writeVarInt64(long value) {
    ensure(writerIndex + 9);
    return _unsafeWriteVarUInt64((value << 1) ^ (value >> 63));
  }

  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public int _unsafeWriteVarInt64(long value) {
    // CHECKSTYLE.ON:MethodName
    return _unsafeWriteVarUInt64((value << 1) ^ (value >> 63));
  }

  public int writeVarUInt64(long value) {
    // Var long encoding algorithm is based kryo UnsafeMemoryOutput.writeVarInt64.
    // var long are written using little endian byte order.
    ensure(writerIndex + 9);
    return _unsafeWriteVarUInt64(value);
  }

  // CHECKSTYLE.OFF:MethodName
  @CodegenInvoke
  public int _unsafeWriteVarUInt64(long value) {
    // CHECKSTYLE.ON:MethodName
    final int writerIndex = this.writerIndex;
    if (AndroidSupport.IS_ANDROID) {
      int varintBytes =
          MemoryOps.putVarUInt64(heapMemory, androidHeapIndexUnchecked(writerIndex), value);
      this.writerIndex = writerIndex + varintBytes;
      return varintBytes;
    }
    int varInt;
    varInt = (int) (value & 0x7F);
    if (value >>> 7 == 0) {
      UNSAFE.putByte(heapMemory, address + writerIndex, (byte) varInt);
      this.writerIndex = writerIndex + 1;
      return 1;
    }
    varInt |= (int) (((value & 0x3f80) << 1) | 0x80);
    if (value >>> 14 == 0) {
      _unsafePutInt32(writerIndex, varInt);
      this.writerIndex = writerIndex + 2;
      return 2;
    }
    varInt |= (int) (((value & 0x1fc000) << 2) | 0x8000);
    if (value >>> 21 == 0) {
      _unsafePutInt32(writerIndex, varInt);
      this.writerIndex = writerIndex + 3;
      return 3;
    }
    varInt |= (int) (((value & 0xfe00000) << 3) | 0x800000);
    if (value >>> 28 == 0) {
      _unsafePutInt32(writerIndex, varInt);
      this.writerIndex = writerIndex + 4;
      return 4;
    }
    long varLong = (varInt & 0xFFFFFFFFL);
    varLong |= ((value & 0x7f0000000L) << 4) | 0x80000000L;
    if (value >>> 35 == 0) {
      _unsafePutInt64(writerIndex, varLong);
      this.writerIndex = writerIndex + 5;
      return 5;
    }
    varLong |= ((value & 0x3f800000000L) << 5) | 0x8000000000L;
    if (value >>> 42 == 0) {
      _unsafePutInt64(writerIndex, varLong);
      this.writerIndex = writerIndex + 6;
      return 6;
    }
    varLong |= ((value & 0x1fc0000000000L) << 6) | 0x800000000000L;
    if (value >>> 49 == 0) {
      _unsafePutInt64(writerIndex, varLong);
      this.writerIndex = writerIndex + 7;
      return 7;
    }
    varLong |= ((value & 0xfe000000000000L) << 7) | 0x80000000000000L;
    value >>>= 56;
    if (value == 0) {
      _unsafePutInt64(writerIndex, varLong);
      this.writerIndex = writerIndex + 8;
      return 8;
    }
    _unsafePutInt64(writerIndex, varLong | 0x8000000000000000L);
    UNSAFE.putByte(heapMemory, address + writerIndex + 8, (byte) (value & 0xFF));
    this.writerIndex = writerIndex + 9;
    return 9;
  }

  /**
   * Write signed long using fory Tagged(Small long as int) encoding. If long is in [0xc0000000,
   * 0x3fffffff], encode as 4 bytes int: {@code | little-endian: ((int) value) << 1 |}; Otherwise
   * write as 9 bytes: {@code | 0b1 | little-endian 8bytes long |}.
   */
  public int writeTaggedInt64(long value) {
    ensure(writerIndex + 9);
    return _unsafeWriteTaggedInt64(value);
  }

  /**
   * Write unsigned long using fory Tagged(Small long as int) encoding. If long is in [0,
   * 0x7fffffff], encode as 4 bytes int: {@code | little-endian: ((int) value) << 1 |}; Otherwise
   * write as 9 bytes: {@code | 0b1 | little-endian 8bytes long |}.
   */
  public int writeTaggedUInt64(long value) {
    ensure(writerIndex + 9);
    return _unsafeWriteTaggedUInt64(value);
  }

  /** Write unsigned long using fory Tagged(Small Long as Int) encoding. */
  // CHECKSTYLE.OFF:MethodName
  public int _unsafeWriteTaggedUInt64(long value) {
    // CHECKSTYLE.ON:MethodName
    final int writerIndex = this.writerIndex;
    if (AndroidSupport.IS_ANDROID) {
      final int heapIndex = androidHeapIndexUnchecked(writerIndex);
      if (value >= 0 && value <= Integer.MAX_VALUE) {
        MemoryOps.putInt32(heapMemory, heapIndex, ((int) value) << 1);
        this.writerIndex = writerIndex + 4;
        return 4;
      } else {
        MemoryOps.putByte(heapMemory, heapIndex, BIG_LONG_FLAG);
        MemoryOps.putInt64(heapMemory, heapIndex + 1, value);
        this.writerIndex = writerIndex + 9;
        return 9;
      }
    }
    final long pos = address + writerIndex;
    final byte[] heapMemory = this.heapMemory;
    if (value >= 0 && value <= Integer.MAX_VALUE) {
      int v = ((int) value) << 1; // bit 0 unset, means int.
      if (!LITTLE_ENDIAN) {
        v = Integer.reverseBytes(v);
      }
      UNSAFE.putInt(heapMemory, pos, v);
      this.writerIndex = writerIndex + 4;
      return 4;
    } else {
      UNSAFE.putByte(heapMemory, pos, BIG_LONG_FLAG);
      if (!LITTLE_ENDIAN) {
        value = Long.reverseBytes(value);
      }
      UNSAFE.putLong(heapMemory, pos + 1, value);
      this.writerIndex = writerIndex + 9;
      return 9;
    }
  }

  private static final long HALF_MAX_INT_VALUE = Integer.MAX_VALUE / 2;
  private static final long HALF_MIN_INT_VALUE = Integer.MIN_VALUE / 2;
  private static final byte BIG_LONG_FLAG = 0b1; // bit 0 set, means big long.

  /** Write long using fory Tagged(Small Long as Int) encoding. */
  // CHECKSTYLE.OFF:MethodName
  public int _unsafeWriteTaggedInt64(long value) {
    // CHECKSTYLE.ON:MethodName
    final int writerIndex = this.writerIndex;
    if (AndroidSupport.IS_ANDROID) {
      final int heapIndex = androidHeapIndexUnchecked(writerIndex);
      if (value >= HALF_MIN_INT_VALUE && value <= HALF_MAX_INT_VALUE) {
        MemoryOps.putInt32(heapMemory, heapIndex, ((int) value) << 1);
        this.writerIndex = writerIndex + 4;
        return 4;
      } else {
        MemoryOps.putByte(heapMemory, heapIndex, BIG_LONG_FLAG);
        MemoryOps.putInt64(heapMemory, heapIndex + 1, value);
        this.writerIndex = writerIndex + 9;
        return 9;
      }
    }
    final long pos = address + writerIndex;
    final byte[] heapMemory = this.heapMemory;
    if (value >= HALF_MIN_INT_VALUE && value <= HALF_MAX_INT_VALUE) {
      // write:
      // 00xxx -> 0xxx
      // 11xxx -> 1xxx
      // read:
      // 0xxx -> 00xxx
      // 1xxx -> 11xxx
      int v = ((int) value) << 1; // bit 0 unset, means int.
      if (!LITTLE_ENDIAN) {
        v = Integer.reverseBytes(v);
      }
      UNSAFE.putInt(heapMemory, pos, v);
      this.writerIndex = writerIndex + 4;
      return 4;
    } else {
      UNSAFE.putByte(heapMemory, pos, BIG_LONG_FLAG);
      if (!LITTLE_ENDIAN) {
        value = Long.reverseBytes(value);
      }
      UNSAFE.putLong(heapMemory, pos + 1, value);
      this.writerIndex = writerIndex + 9;
      return 9;
    }
  }

  public void writeBytes(byte[] bytes) {
    writeBytes(bytes, 0, bytes.length);
  }

  public void writeBytes(byte[] bytes, int offset, int length) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + length;
    ensure(newIdx);
    put(writerIdx, bytes, offset, length);
    writerIndex = newIdx;
  }

  public void write(ByteBuffer source) {
    write(source, source.remaining());
  }

  public void write(ByteBuffer source, int numBytes) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + numBytes;
    ensure(newIdx);
    put(writerIdx, source, numBytes);
    writerIndex = newIdx;
  }

  public void writeBytesWithSize(byte[] values) {
    writeVarUInt32Small7(values.length);
    writeBytes(values, 0, values.length);
  }

  public void writeBooleansWithSize(boolean[] values) {
    writeVarUInt32Small7(values.length);
    writeBooleans(values, 0, values.length);
  }

  public void writeBooleans(boolean[] values) {
    writeBooleans(values, 0, values.length);
  }

  public void writeBooleans(boolean[] values, int offset, int numElements) {
    if (AndroidSupport.IS_ANDROID) {
      int writerIdx = writerIndex;
      int newIdx = writerIdx + numElements;
      ensure(newIdx);
      MemoryOps.writeBooleans(
          heapMemory, androidHeapIndexUnchecked(writerIdx), values, offset, numElements);
      writerIndex = newIdx;
      return;
    }
    writeJvmPrimitiveArrayPayload(values, UnsafeOps.BOOLEAN_ARRAY_OFFSET + offset, numElements);
  }

  public void writeCharsWithSize(char[] values) {
    int numBytes = Math.multiplyExact(values.length, 2);
    writeVarUInt32Small7(numBytes);
    writeChars(values, 0, values.length);
  }

  public void writeChars(char[] values) {
    writeChars(values, 0, values.length);
  }

  public void writeChars(char[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 2);
    if (AndroidSupport.IS_ANDROID) {
      int writerIdx = writerIndex;
      int newIdx = writerIdx + numBytes;
      ensure(newIdx);
      MemoryOps.writeChars(
          heapMemory, androidHeapIndexUnchecked(writerIdx), values, offset, numElements);
      writerIndex = newIdx;
      return;
    }
    writeJvmPrimitiveArrayPayload(
        values, UnsafeOps.CHAR_ARRAY_OFFSET + ((long) offset << 1), numBytes);
  }

  public void writeShortsWithSize(short[] values) {
    int numBytes = Math.multiplyExact(values.length, 2);
    writeVarUInt32Small7(numBytes);
    writeShorts(values, 0, values.length);
  }

  public void writeShorts(short[] values) {
    writeShorts(values, 0, values.length);
  }

  public void writeShorts(short[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 2);
    if (AndroidSupport.IS_ANDROID) {
      int writerIdx = writerIndex;
      int newIdx = writerIdx + numBytes;
      ensure(newIdx);
      MemoryOps.writeShorts(
          heapMemory, androidHeapIndexUnchecked(writerIdx), values, offset, numElements);
      writerIndex = newIdx;
      return;
    }
    writeJvmPrimitiveArrayPayload(
        values, UnsafeOps.SHORT_ARRAY_OFFSET + ((long) offset << 1), numBytes);
  }

  public void writeIntsWithSize(int[] values) {
    int numBytes = Math.multiplyExact(values.length, 4);
    writeVarUInt32Small7(numBytes);
    writeInts(values, 0, values.length);
  }

  public void writeInts(int[] values) {
    writeInts(values, 0, values.length);
  }

  public void writeInts(int[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 4);
    if (AndroidSupport.IS_ANDROID) {
      int writerIdx = writerIndex;
      int newIdx = writerIdx + numBytes;
      ensure(newIdx);
      MemoryOps.writeInts(
          heapMemory, androidHeapIndexUnchecked(writerIdx), values, offset, numElements);
      writerIndex = newIdx;
      return;
    }
    writeJvmPrimitiveArrayPayload(
        values, UnsafeOps.INT_ARRAY_OFFSET + ((long) offset << 2), numBytes);
  }

  public void writeLongsWithSize(long[] values) {
    int numBytes = Math.multiplyExact(values.length, 8);
    writeVarUInt32Small7(numBytes);
    writeLongs(values, 0, values.length);
  }

  public void writeLongs(long[] values) {
    writeLongs(values, 0, values.length);
  }

  public void writeLongs(long[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 8);
    if (AndroidSupport.IS_ANDROID) {
      int writerIdx = writerIndex;
      int newIdx = writerIdx + numBytes;
      ensure(newIdx);
      MemoryOps.writeLongs(
          heapMemory, androidHeapIndexUnchecked(writerIdx), values, offset, numElements);
      writerIndex = newIdx;
      return;
    }
    writeJvmPrimitiveArrayPayload(
        values, UnsafeOps.LONG_ARRAY_OFFSET + ((long) offset << 3), numBytes);
  }

  public void writeFloatsWithSize(float[] values) {
    int numBytes = Math.multiplyExact(values.length, 4);
    writeVarUInt32Small7(numBytes);
    writeFloats(values, 0, values.length);
  }

  public void writeFloats(float[] values) {
    writeFloats(values, 0, values.length);
  }

  public void writeFloats(float[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 4);
    if (AndroidSupport.IS_ANDROID) {
      int writerIdx = writerIndex;
      int newIdx = writerIdx + numBytes;
      ensure(newIdx);
      MemoryOps.writeFloats(
          heapMemory, androidHeapIndexUnchecked(writerIdx), values, offset, numElements);
      writerIndex = newIdx;
      return;
    }
    writeJvmPrimitiveArrayPayload(
        values, UnsafeOps.FLOAT_ARRAY_OFFSET + ((long) offset << 2), numBytes);
  }

  public void writeDoublesWithSize(double[] values) {
    int numBytes = Math.multiplyExact(values.length, 8);
    writeVarUInt32Small7(numBytes);
    writeDoubles(values, 0, values.length);
  }

  public void writeDoubles(double[] values) {
    writeDoubles(values, 0, values.length);
  }

  public void writeDoubles(double[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 8);
    if (AndroidSupport.IS_ANDROID) {
      int writerIdx = writerIndex;
      int newIdx = writerIdx + numBytes;
      ensure(newIdx);
      MemoryOps.writeDoubles(
          heapMemory, androidHeapIndexUnchecked(writerIdx), values, offset, numElements);
      writerIndex = newIdx;
      return;
    }
    writeJvmPrimitiveArrayPayload(
        values, UnsafeOps.DOUBLE_ARRAY_OFFSET + ((long) offset << 3), numBytes);
  }

  private void writeJvmPrimitiveArrayPayload(Object arr, long offset, int numBytes) {
    final int writerIdx = writerIndex;
    final int newIdx = writerIdx + numBytes;
    ensure(newIdx);
    UNSAFE.copyMemory(arr, offset, heapMemory, address + writerIdx, numBytes);
    writerIndex = newIdx;
  }

  /** For off-heap buffer, this will make a heap buffer internally. */
  public void grow(int neededSize) {
    int length = writerIndex + neededSize;
    if (length > size) {
      globalAllocator.grow(this, length);
    }
  }

  /** For off-heap buffer, this will make a heap buffer internally. */
  public void ensure(int length) {
    if (length > size) {
      globalAllocator.grow(this, length);
    }
  }

  // -------------------------------------------------------------------------
  //                          Read Methods
  // -------------------------------------------------------------------------

  // Check should be done outside to avoid this method got into the critical path.
  private void throwIndexOOBExceptionForRead() {
    throw new IndexOutOfBoundsException(
        String.format(
            "readerIndex: %d (expected: 0 <= readerIndex <= size(%d))", readerIndex, size));
  }

  // Check should be done outside to avoid this method got into the critical path.
  private void throwIndexOOBExceptionForRead(int length) {
    throw new IndexOutOfBoundsException(
        String.format(
            "readerIndex: %d (expected: 0 <= readerIndex <= size(%d)), length %d",
            readerIndex, size, length));
  }

  /** Returns the {@code readerIndex} of this buffer. */
  public int readerIndex() {
    return readerIndex;
  }

  /**
   * Sets the {@code readerIndex} of this buffer.
   *
   * @throws IndexOutOfBoundsException if the specified {@code readerIndex} is less than {@code 0}
   *     or greater than {@code this.size}
   */
  public void readerIndex(int readerIndex) {
    if (readerIndex < 0) {
      throwIndexOOBExceptionForRead();
    } else if (readerIndex > size) {
      // in this case, diff must be greater than 0.
      streamReader.fillBuffer(readerIndex - size);
    }
    this.readerIndex = readerIndex;
  }

  /** Returns array index for reader index if buffer is a heap buffer. */
  // CHECKSTYLE.OFF:MethodName
  public int _unsafeHeapReaderIndex() {
    // CHECKSTYLE.ON:MethodName
    return readerIndex + heapOffset;
  }

  // CHECKSTYLE.OFF:MethodName
  public void _increaseReaderIndexUnsafe(int diff) {
    // CHECKSTYLE.ON:MethodName
    readerIndex += diff;
  }

  public void increaseReaderIndex(int diff) {
    int readerIdx = readerIndex;
    readerIndex = readerIdx += diff;
    if (readerIdx < 0) {
      throwIndexOOBExceptionForRead();
    } else if (readerIdx > size) {
      // in this case, diff must be greater than 0.
      streamReader.fillBuffer(readerIdx - size);
    }
  }

  public int remaining() {
    return size - readerIndex;
  }

  public boolean readBoolean() {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    if (readerIdx > size - 1) {
      streamReader.fillBuffer(1);
    }
    readerIndex = readerIdx + 1;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getBoolean(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return UNSAFE.getByte(heapMemory, address + readerIdx) != 0;
  }

  public int readUInt8() {
    int readerIdx = readerIndex;
    if (readerIdx > size - 1) {
      streamReader.fillBuffer(1);
    }
    readerIndex = readerIdx + 1;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getByte(heapMemory, androidHeapIndexUnchecked(readerIdx)) & 0b11111111;
    }
    int v = UNSAFE.getByte(heapMemory, address + readerIdx);
    v &= 0b11111111;
    return v;
  }

  public int readUnsignedByte() {
    return readUInt8();
  }

  public byte readByte() {
    int readerIdx = readerIndex;
    if (readerIdx > size - 1) {
      streamReader.fillBuffer(1);
    }
    readerIndex = readerIdx + 1;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getByte(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return UNSAFE.getByte(heapMemory, address + readerIdx);
  }

  public char readChar() {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 2) {
      streamReader.fillBuffer(2 - remaining);
    }
    readerIndex = readerIdx + 2;
    if (AndroidSupport.IS_ANDROID) {
      return (char) MemoryOps.getInt16(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    char c = UNSAFE.getChar(heapMemory, address + readerIdx);
    return LITTLE_ENDIAN ? c : Character.reverseBytes(c);
  }

  public short readInt16() {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 2) {
      streamReader.fillBuffer(2 - remaining);
    }
    readerIndex = readerIdx + 2;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt16(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    short v = UNSAFE.getShort(heapMemory, address + readerIdx);
    return LITTLE_ENDIAN ? v : Short.reverseBytes(v);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public short _readInt16OnLE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 2) {
      streamReader.fillBuffer(2 - remaining);
    }
    readerIndex = readerIdx + 2;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt16(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return UNSAFE.getShort(heapMemory, address + readerIdx);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public short _readInt16OnBE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 2) {
      streamReader.fillBuffer(2 - remaining);
    }
    readerIndex = readerIdx + 2;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt16(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return Short.reverseBytes(UNSAFE.getShort(heapMemory, address + readerIdx));
  }

  public int readInt32() {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 4) {
      streamReader.fillBuffer(4 - remaining);
    }
    readerIndex = readerIdx + 4;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt32(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    int v = UNSAFE.getInt(heapMemory, address + readerIdx);
    return LITTLE_ENDIAN ? v : Integer.reverseBytes(v);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public int _readInt32OnLE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 4) {
      streamReader.fillBuffer(4 - remaining);
    }
    readerIndex = readerIdx + 4;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt32(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return UNSAFE.getInt(heapMemory, address + readerIdx);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public int _readInt32OnBE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 4) {
      streamReader.fillBuffer(4 - remaining);
    }
    readerIndex = readerIdx + 4;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt32(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return Integer.reverseBytes(UNSAFE.getInt(heapMemory, address + readerIdx));
  }

  public long readInt64() {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 8) {
      streamReader.fillBuffer(8 - remaining);
    }
    readerIndex = readerIdx + 8;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt64(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    long v = UNSAFE.getLong(heapMemory, address + readerIdx);
    return LITTLE_ENDIAN ? v : Long.reverseBytes(v);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readInt64OnLE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 8) {
      streamReader.fillBuffer(8 - remaining);
    }
    readerIndex = readerIdx + 8;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt64(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return UNSAFE.getLong(heapMemory, address + readerIdx);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readInt64OnBE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 8) {
      streamReader.fillBuffer(8 - remaining);
    }
    readerIndex = readerIdx + 8;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getInt64(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return Long.reverseBytes(UNSAFE.getLong(heapMemory, address + readerIdx));
  }

  /** Read signed fory Tagged(Small Long as Int) encoded long. */
  public long readTaggedInt64() {
    if (LITTLE_ENDIAN) {
      return _readTaggedInt64OnLE();
    } else {
      return _readTaggedInt64OnBE();
    }
  }

  /** Read unsigned fory Tagged(Small Long as Int) encoded long. */
  public long readTaggedUInt64() {
    if (LITTLE_ENDIAN) {
      return _readTaggedUInt64OnLE();
    } else {
      return _readTaggedUInt64OnBE();
    }
  }

  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readTaggedUInt64OnLE() {
    // CHECKSTYLE.ON:MethodName
    final int readIdx = readerIndex;
    int diff = size - readIdx;
    if (diff < 4) {
      streamReader.fillBuffer(4 - diff);
    }
    if (AndroidSupport.IS_ANDROID) {
      int heapIndex = androidHeapIndexUnchecked(readIdx);
      int i = MemoryOps.getInt32(heapMemory, heapIndex);
      if ((i & 0b1) != 0b1) {
        readerIndex = readIdx + 4;
        return i >>> 1;
      }
      diff = size - readIdx;
      if (diff < 9) {
        streamReader.fillBuffer(9 - diff);
      }
      readerIndex = readIdx + 9;
      heapIndex = androidHeapIndexUnchecked(readIdx);
      return MemoryOps.getInt64(heapMemory, heapIndex + 1);
    }
    int i = UNSAFE.getInt(heapMemory, address + readIdx);
    if ((i & 0b1) != 0b1) {
      readerIndex = readIdx + 4;
      return i >>> 1; // unsigned right shift
    }
    diff = size - readIdx;
    if (diff < 9) {
      streamReader.fillBuffer(9 - diff);
    }
    readerIndex = readIdx + 9;
    return UNSAFE.getLong(heapMemory, address + readIdx + 1);
  }

  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readTaggedUInt64OnBE() {
    // CHECKSTYLE.ON:MethodName
    final int readIdx = readerIndex;
    int diff = size - readIdx;
    if (diff < 4) {
      streamReader.fillBuffer(4 - diff);
    }
    if (AndroidSupport.IS_ANDROID) {
      int heapIndex = androidHeapIndexUnchecked(readIdx);
      int i = MemoryOps.getInt32(heapMemory, heapIndex);
      if ((i & 0b1) != 0b1) {
        readerIndex = readIdx + 4;
        return i >>> 1;
      }
      diff = size - readIdx;
      if (diff < 9) {
        streamReader.fillBuffer(9 - diff);
      }
      readerIndex = readIdx + 9;
      heapIndex = androidHeapIndexUnchecked(readIdx);
      return MemoryOps.getInt64(heapMemory, heapIndex + 1);
    }
    int i = Integer.reverseBytes(UNSAFE.getInt(heapMemory, address + readIdx));
    if ((i & 0b1) != 0b1) {
      readerIndex = readIdx + 4;
      return i >>> 1; // unsigned right shift
    }
    diff = size - readIdx;
    if (diff < 9) {
      streamReader.fillBuffer(9 - diff);
    }
    readerIndex = readIdx + 9;
    return Long.reverseBytes(UNSAFE.getLong(heapMemory, address + readIdx + 1));
  }

  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readTaggedInt64OnLE() {
    // CHECKSTYLE.ON:MethodName
    // Duplicate and manual inline for performance.
    // noinspection Duplicates
    final int readIdx = readerIndex;
    int diff = size - readIdx;
    if (diff < 4) {
      streamReader.fillBuffer(4 - diff);
    }
    if (AndroidSupport.IS_ANDROID) {
      int heapIndex = androidHeapIndexUnchecked(readIdx);
      int i = MemoryOps.getInt32(heapMemory, heapIndex);
      if ((i & 0b1) != 0b1) {
        readerIndex = readIdx + 4;
        return i >> 1;
      }
      diff = size - readIdx;
      if (diff < 9) {
        streamReader.fillBuffer(9 - diff);
      }
      readerIndex = readIdx + 9;
      heapIndex = androidHeapIndexUnchecked(readIdx);
      return MemoryOps.getInt64(heapMemory, heapIndex + 1);
    }
    int i = UNSAFE.getInt(heapMemory, address + readIdx);
    if ((i & 0b1) != 0b1) {
      readerIndex = readIdx + 4;
      return i >> 1;
    }
    diff = size - readIdx;
    if (diff < 9) {
      streamReader.fillBuffer(9 - diff);
    }
    readerIndex = readIdx + 9;
    return UNSAFE.getLong(heapMemory, address + readIdx + 1);
  }

  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readTaggedInt64OnBE() {
    // CHECKSTYLE.ON:MethodName
    // noinspection Duplicates
    final int readIdx = readerIndex;
    int diff = size - readIdx;
    if (diff < 4) {
      streamReader.fillBuffer(4 - diff);
    }
    if (AndroidSupport.IS_ANDROID) {
      int heapIndex = androidHeapIndexUnchecked(readIdx);
      int i = MemoryOps.getInt32(heapMemory, heapIndex);
      if ((i & 0b1) != 0b1) {
        readerIndex = readIdx + 4;
        return i >> 1;
      }
      diff = size - readIdx;
      if (diff < 9) {
        streamReader.fillBuffer(9 - diff);
      }
      readerIndex = readIdx + 9;
      heapIndex = androidHeapIndexUnchecked(readIdx);
      return MemoryOps.getInt64(heapMemory, heapIndex + 1);
    }
    int i = Integer.reverseBytes(UNSAFE.getInt(heapMemory, address + readIdx));
    if ((i & 0b1) != 0b1) {
      readerIndex = readIdx + 4;
      return i >> 1;
    }
    diff = size - readIdx;
    if (diff < 9) {
      streamReader.fillBuffer(9 - diff);
    }
    readerIndex = readIdx + 9;
    return Long.reverseBytes(UNSAFE.getLong(heapMemory, address + readIdx + 1));
  }

  public float readFloat32() {
    // noinspection Duplicates
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 4) {
      streamReader.fillBuffer(4 - remaining);
    }
    readerIndex = readerIdx + 4;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat32(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    int v = UNSAFE.getInt(heapMemory, address + readerIdx);
    if (!LITTLE_ENDIAN) {
      v = Integer.reverseBytes(v);
    }
    return Float.intBitsToFloat(v);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public float _readFloat32OnLE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 4) {
      streamReader.fillBuffer(4 - remaining);
    }
    readerIndex = readerIdx + 4;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat32(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return Float.intBitsToFloat(UNSAFE.getInt(heapMemory, address + readerIdx));
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public float _readFloat32OnBE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 4) {
      streamReader.fillBuffer(4 - remaining);
    }
    readerIndex = readerIdx + 4;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat32(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return Float.intBitsToFloat(
        Integer.reverseBytes(UNSAFE.getInt(heapMemory, address + readerIdx)));
  }

  public double readFloat64() {
    // noinspection Duplicates
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 8) {
      streamReader.fillBuffer(8 - remaining);
    }
    readerIndex = readerIdx + 8;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat64(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    long v = UNSAFE.getLong(heapMemory, address + readerIdx);
    if (!LITTLE_ENDIAN) {
      v = Long.reverseBytes(v);
    }
    return Double.longBitsToDouble(v);
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public double _readFloat64OnLE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 8) {
      streamReader.fillBuffer(8 - remaining);
    }
    readerIndex = readerIdx + 8;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat64(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return Double.longBitsToDouble(UNSAFE.getLong(heapMemory, address + readerIdx));
  }

  // Reduce method body for better inline in the caller.
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public double _readFloat64OnBE() {
    // CHECKSTYLE.ON:MethodName
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining < 8) {
      streamReader.fillBuffer(8 - remaining);
    }
    readerIndex = readerIdx + 8;
    if (AndroidSupport.IS_ANDROID) {
      return MemoryOps.getFloat64(heapMemory, androidHeapIndexUnchecked(readerIdx));
    }
    return Double.longBitsToDouble(
        Long.reverseBytes(UNSAFE.getLong(heapMemory, address + readerIdx)));
  }

  /** Reads the 1-5 byte int part of a varint. */
  @CodegenInvoke
  public int readVarInt32() {
    if (AndroidSupport.IS_ANDROID) {
      return readVarInt32Android();
    }
    if (LITTLE_ENDIAN) {
      return _readVarInt32OnLE();
    } else {
      return _readVarInt32OnBE();
    }
  }

  private int readVarInt32Android() {
    int result = readVarUInt32Android();
    return (result >>> 1) ^ -(result & 1);
  }

  private int readVarUInt32Android() {
    int readIdx = readerIndex;
    int result;
    if (size - readIdx < 5) {
      result = readVarUInt32Slow();
    } else {
      int heapIndex = androidHeapIndexUnchecked(readIdx);
      result = MemoryOps.readVarUInt32(heapMemory, heapIndex);
      readerIndex = readIdx + MemoryOps.varUInt32Bytes(heapMemory, heapIndex);
    }
    return result;
  }

  /** Reads the 1-5 byte as a varint on a little endian machine. */
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public int _readVarInt32OnLE() {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return readVarInt32Android();
    }
    // noinspection Duplicates
    int readIdx = readerIndex;
    int result;
    if (size - readIdx < 5) {
      result = readVarUInt32Slow();
    } else {
      long address = this.address;
      // | 1bit + 7bits | 1bit + 7bits | 1bit + 7bits | 1bit + 7bits |
      int fourByteValue = UNSAFE.getInt(heapMemory, address + readIdx);
      // Duplicate and manual inline for performance.
      // noinspection Duplicates
      readIdx++;
      result = fourByteValue & 0x7F;
      if ((fourByteValue & 0x80) != 0) {
        readIdx++;
        // 0x3f80: 0b1111111 << 7
        result |= (fourByteValue >>> 1) & 0x3f80;
        // 0x8000: 0b1 << 15
        if ((fourByteValue & 0x8000) != 0) {
          readIdx++;
          // 0x1fc000: 0b1111111 << 14
          result |= (fourByteValue >>> 2) & 0x1fc000;
          // 0x800000: 0b1 << 23
          if ((fourByteValue & 0x800000) != 0) {
            readIdx++;
            // 0xfe00000: 0b1111111 << 21
            result |= (fourByteValue >>> 3) & 0xfe00000;
            if ((fourByteValue & 0x80000000) != 0) {
              int fifthByte = UNSAFE.getByte(heapMemory, address + readIdx++) & 0xFF;
              if ((fifthByte & 0xF0) != 0) {
                throwMalformedVarUInt32(fifthByte);
              }
              result |= fifthByte << 28;
            }
          }
        }
      }
      readerIndex = readIdx;
    }
    return (result >>> 1) ^ -(result & 1);
  }

  /** Reads the 1-5 byte as a varint on a big endian machine. */
  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public int _readVarInt32OnBE() {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return readVarInt32Android();
    }
    // noinspection Duplicates
    int readIdx = readerIndex;
    int result;
    if (size - readIdx < 5) {
      result = readVarUInt32Slow();
    } else {
      long address = this.address;
      int fourByteValue = Integer.reverseBytes(UNSAFE.getInt(heapMemory, address + readIdx));
      // Duplicate and manual inline for performance.
      // noinspection Duplicates
      readIdx++;
      result = fourByteValue & 0x7F;
      if ((fourByteValue & 0x80) != 0) {
        readIdx++;
        // 0x3f80: 0b1111111 << 7
        result |= (fourByteValue >>> 1) & 0x3f80;
        // 0x8000: 0b1 << 15
        if ((fourByteValue & 0x8000) != 0) {
          readIdx++;
          // 0x1fc000: 0b1111111 << 14
          result |= (fourByteValue >>> 2) & 0x1fc000;
          // 0x800000: 0b1 << 23
          if ((fourByteValue & 0x800000) != 0) {
            readIdx++;
            // 0xfe00000: 0b1111111 << 21
            result |= (fourByteValue >>> 3) & 0xfe00000;
            if ((fourByteValue & 0x80000000) != 0) {
              int fifthByte = UNSAFE.getByte(heapMemory, address + readIdx++) & 0xFF;
              if ((fifthByte & 0xF0) != 0) {
                throwMalformedVarUInt32(fifthByte);
              }
              result |= fifthByte << 28;
            }
          }
        }
      }
      readerIndex = readIdx;
    }
    return (result >>> 1) ^ -(result & 1);
  }

  public long readVarUint36Small() {
    if (AndroidSupport.IS_ANDROID) {
      return readVarUint36SmallAndroid();
    }
    // Duplicate and manual inline for performance.
    // noinspection Duplicates
    int readIdx = readerIndex;
    if (size - readIdx >= 9) {
      long bulkValue = _unsafeGetInt64(readIdx++);
      // noinspection Duplicates
      long result = bulkValue & 0x7F;
      if ((bulkValue & 0x80) != 0) {
        readIdx++;
        // 0x3f80: 0b1111111 << 7
        result |= (bulkValue >>> 1) & 0x3f80;
        // 0x8000: 0b1 << 15
        if ((bulkValue & 0x8000) != 0) {
          return continueReadVarInt36(readIdx, bulkValue, result);
        }
      }
      readerIndex = readIdx;
      return result;
    } else {
      return readVarUint36Slow();
    }
  }

  private long readVarUint36SmallAndroid() {
    int readIdx = readerIndex;
    if (size - readIdx < 5) {
      return readVarUint36Slow();
    }
    int heapIndex = androidHeapIndexUnchecked(readIdx);
    long result = MemoryOps.readVarUint36Small(heapMemory, heapIndex);
    readerIndex = readIdx + MemoryOps.varUint36SmallBytes(heapMemory, heapIndex);
    return result;
  }

  private long continueReadVarInt36(int readIdx, long bulkValue, long result) {
    readIdx++;
    // 0x1fc000: 0b1111111 << 14
    result |= (bulkValue >>> 2) & 0x1fc000;
    // 0x800000: 0b1 << 23
    if ((bulkValue & 0x800000) != 0) {
      readIdx++;
      // 0xfe00000: 0b1111111 << 21
      result |= (bulkValue >>> 3) & 0xfe00000;
      if ((bulkValue & 0x80000000L) != 0) {
        readIdx++;
        // 0xff0000000: 0b11111111 << 28
        result |= (bulkValue >>> 4) & 0xff0000000L;
      }
    }
    readerIndex = readIdx;
    return result;
  }

  private long readVarUint36Slow() {
    long b = readByte();
    long result = b & 0x7F;
    // Note:
    //  Loop are not used here to improve performance.
    //  We manually unroll the loop for better performance.
    // noinspection Duplicates
    if ((b & 0x80) != 0) {
      b = readByte();
      result |= (b & 0x7F) << 7;
      if ((b & 0x80) != 0) {
        b = readByte();
        result |= (b & 0x7F) << 14;
        if ((b & 0x80) != 0) {
          b = readByte();
          result |= (b & 0x7F) << 21;
          if ((b & 0x80) != 0) {
            b = readByte();
            result |= (b & 0xFF) << 28;
          }
        }
      }
    }
    return result;
  }

  private int readVarUInt32Slow() {
    int b = readByte() & 0xFF;
    int result = b & 0x7F;
    // Note:
    //  Loop are not used here to improve performance.
    //  We manually unroll the loop for better performance.
    // noinspection Duplicates
    if ((b & 0x80) != 0) {
      b = readByte() & 0xFF;
      result |= (b & 0x7F) << 7;
      if ((b & 0x80) != 0) {
        b = readByte() & 0xFF;
        result |= (b & 0x7F) << 14;
        if ((b & 0x80) != 0) {
          b = readByte() & 0xFF;
          result |= (b & 0x7F) << 21;
          if ((b & 0x80) != 0) {
            b = readByte() & 0xFF;
            if ((b & 0xF0) != 0) {
              throwMalformedVarUInt32(b);
            }
            result |= b << 28;
          }
        }
      }
    }
    return result;
  }

  private static void throwMalformedVarUInt32(int fifthByte) {
    throw new IllegalArgumentException(
        "Malformed varuint32 fifth byte " + fifthByte + " exceeds 32 bits");
  }

  /** Reads the 1-5 byte int part of a non-negative varint. */
  public int readVarUInt32() {
    if (AndroidSupport.IS_ANDROID) {
      return readVarUInt32Android();
    }
    int readIdx = readerIndex;
    if (size - readIdx < 5) {
      return readVarUInt32Slow();
    }
    // | 1bit + 7bits | 1bit + 7bits | 1bit + 7bits | 1bit + 7bits |
    int fourByteValue = _unsafeGetInt32(readIdx);
    readIdx++;
    int result = fourByteValue & 0x7F;
    // Duplicate and manual inline for performance.
    // noinspection Duplicates
    if ((fourByteValue & 0x80) != 0) {
      readIdx++;
      // 0x3f80: 0b1111111 << 7
      result |= (fourByteValue >>> 1) & 0x3f80;
      // 0x8000: 0b1 << 15
      if ((fourByteValue & 0x8000) != 0) {
        readIdx++;
        // 0x1fc000: 0b1111111 << 14
        result |= (fourByteValue >>> 2) & 0x1fc000;
        // 0x800000: 0b1 << 23
        if ((fourByteValue & 0x800000) != 0) {
          readIdx++;
          // 0xfe00000: 0b1111111 << 21
          result |= (fourByteValue >>> 3) & 0xfe00000;
          if ((fourByteValue & 0x80000000) != 0) {
            int fifthByte = UNSAFE.getByte(heapMemory, address + readIdx++) & 0xFF;
            if ((fifthByte & 0xF0) != 0) {
              throwMalformedVarUInt32(fifthByte);
            }
            result |= fifthByte << 28;
          }
        }
      }
    }
    readerIndex = readIdx;
    return result;
  }

  /**
   * Fast method for read an unsigned varint which is mostly a small value in 7 bits value in [0,
   * 127). When the value is equal or greater than 127, the read will be a little slower.
   */
  public int readVarUInt32Small7() {
    if (AndroidSupport.IS_ANDROID) {
      return readVarUInt32Android();
    }
    int readIdx = readerIndex;
    if (size - readIdx > 0) {
      byte v = UNSAFE.getByte(heapMemory, address + readIdx++);
      if ((v & 0x80) == 0) {
        readerIndex = readIdx;
        return v;
      }
    }
    return readVarUInt32Small14();
  }

  /**
   * Fast path for read an unsigned varint which is mostly a small value in 14 bits value in [0,
   * 16384). When the value is equal or greater than 16384, the read will be a little slower.
   */
  public int readVarUInt32Small14() {
    if (AndroidSupport.IS_ANDROID) {
      return readVarUInt32Android();
    }
    int readIdx = readerIndex;
    if (size - readIdx >= 5) {
      int fourByteValue = _unsafeGetInt32(readIdx++);
      int value = fourByteValue & 0x7F;
      // Duplicate and manual inline for performance.
      // noinspection Duplicates
      if ((fourByteValue & 0x80) != 0) {
        readIdx++;
        value |= (fourByteValue >>> 1) & 0x3f80;
        if ((fourByteValue & 0x8000) != 0) {
          // merely executed path, make it as a separate method to reduce
          // code size of current method for better jvm inline
          return continueReadVarUInt32(readIdx, fourByteValue, value);
        }
      }
      readerIndex = readIdx;
      return value;
    } else {
      return readVarUInt32Slow();
    }
  }

  private int continueReadVarUInt32(int readIdx, int bulkRead, int value) {
    // Duplicate and manual inline for performance.
    // noinspection Duplicates
    readIdx++;
    value |= (bulkRead >>> 2) & 0x1fc000;
    if ((bulkRead & 0x800000) != 0) {
      readIdx++;
      value |= (bulkRead >>> 3) & 0xfe00000;
      if ((bulkRead & 0x80000000) != 0) {
        int fifthByte = UNSAFE.getByte(heapMemory, address + readIdx++) & 0xFF;
        if ((fifthByte & 0xF0) != 0) {
          throwMalformedVarUInt32(fifthByte);
        }
        value |= fifthByte << 28;
      }
    }
    readerIndex = readIdx;
    return value;
  }

  /** Reads the 1-9 byte int part of a var long. */
  public long readVarInt64() {
    if (AndroidSupport.IS_ANDROID) {
      return readVarInt64Android();
    }
    return LITTLE_ENDIAN ? _readVarInt64OnLE() : _readVarInt64OnBE();
  }

  private long readVarInt64Android() {
    long result = readVarUInt64Android();
    return (result >>> 1) ^ -(result & 1);
  }

  private long readVarUInt64Android() {
    int readIdx = readerIndex;
    long result;
    if (size - readIdx < 9) {
      result = readVarUInt64Slow();
    } else {
      int heapIndex = androidHeapIndexUnchecked(readIdx);
      result = MemoryOps.readVarUInt64(heapMemory, heapIndex);
      readerIndex = readIdx + MemoryOps.varUInt64Bytes(heapMemory, heapIndex);
    }
    return result;
  }

  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readVarInt64OnLE() {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return readVarInt64Android();
    }
    // Duplicate and manual inline for performance.
    // noinspection Duplicates
    int readIdx = readerIndex;
    long result;
    if (size - readIdx < 9) {
      result = readVarUInt64Slow();
    } else {
      long address = this.address;
      long bulkValue = UNSAFE.getLong(heapMemory, address + readIdx);
      // Duplicate and manual inline for performance.
      // noinspection Duplicates
      readIdx++;
      result = bulkValue & 0x7F;
      if ((bulkValue & 0x80) != 0) {
        readIdx++;
        // 0x3f80: 0b1111111 << 7
        result |= (bulkValue >>> 1) & 0x3f80;
        // 0x8000: 0b1 << 15
        if ((bulkValue & 0x8000) != 0) {
          result = continueReadVarInt64(readIdx, bulkValue, result);
          return ((result >>> 1) ^ -(result & 1));
        }
      }
      readerIndex = readIdx;
    }
    return ((result >>> 1) ^ -(result & 1));
  }

  @CodegenInvoke
  // CHECKSTYLE.OFF:MethodName
  public long _readVarInt64OnBE() {
    // CHECKSTYLE.ON:MethodName
    if (AndroidSupport.IS_ANDROID) {
      return readVarInt64Android();
    }
    int readIdx = readerIndex;
    long result;
    if (size - readIdx < 9) {
      result = readVarUInt64Slow();
    } else {
      long address = this.address;
      long bulkValue = Long.reverseBytes(UNSAFE.getLong(heapMemory, address + readIdx));
      // Duplicate and manual inline for performance.
      // noinspection Duplicates
      readIdx++;
      result = bulkValue & 0x7F;
      if ((bulkValue & 0x80) != 0) {
        readIdx++;
        // 0x3f80: 0b1111111 << 7
        result |= (bulkValue >>> 1) & 0x3f80;
        // 0x8000: 0b1 << 15
        if ((bulkValue & 0x8000) != 0) {
          result = continueReadVarInt64(readIdx, bulkValue, result);
          return ((result >>> 1) ^ -(result & 1));
        }
      }
      readerIndex = readIdx;
    }
    return ((result >>> 1) ^ -(result & 1));
  }

  /** Reads the 1-9 byte int part of a non-negative var long. */
  public long readVarUInt64() {
    if (AndroidSupport.IS_ANDROID) {
      return readVarUInt64Android();
    }
    int readIdx = readerIndex;
    if (size - readIdx < 9) {
      return readVarUInt64Slow();
    }
    // varint are written using little endian byte order, so read by little endian byte order.
    long bulkValue = _unsafeGetInt64(readIdx);
    // Duplicate and manual inline for performance.
    // noinspection Duplicates
    readIdx++;
    long result = bulkValue & 0x7F;
    if ((bulkValue & 0x80) != 0) {
      readIdx++;
      // 0x3f80: 0b1111111 << 7
      result |= (bulkValue >>> 1) & 0x3f80;
      // 0x8000: 0b1 << 15
      if ((bulkValue & 0x8000) != 0) {
        return continueReadVarInt64(readIdx, bulkValue, result);
      }
    }
    readerIndex = readIdx;
    return result;
  }

  private long continueReadVarInt64(int readIdx, long bulkValue, long result) {
    readIdx++;
    // 0x1fc000: 0b1111111 << 14
    result |= (bulkValue >>> 2) & 0x1fc000;
    // 0x800000: 0b1 << 23
    if ((bulkValue & 0x800000) != 0) {
      readIdx++;
      // 0xfe00000: 0b1111111 << 21
      result |= (bulkValue >>> 3) & 0xfe00000;
      if ((bulkValue & 0x80000000L) != 0) {
        readIdx++;
        result |= (bulkValue >>> 4) & 0x7f0000000L;
        if ((bulkValue & 0x8000000000L) != 0) {
          readIdx++;
          result |= (bulkValue >>> 5) & 0x3f800000000L;
          if ((bulkValue & 0x800000000000L) != 0) {
            readIdx++;
            result |= (bulkValue >>> 6) & 0x1fc0000000000L;
            if ((bulkValue & 0x80000000000000L) != 0) {
              readIdx++;
              result |= (bulkValue >>> 7) & 0xfe000000000000L;
              if ((bulkValue & 0x8000000000000000L) != 0) {
                long b = UNSAFE.getByte(heapMemory, address + readIdx++);
                result |= b << 56;
              }
            }
          }
        }
      }
    }
    readerIndex = readIdx;
    return result;
  }

  private long readVarUInt64Slow() {
    long b = readByte();
    long result = b & 0x7F;
    // Note:
    //  Loop are not used here to improve performance.
    //  We manually unroll the loop for better performance.
    if ((b & 0x80) != 0) {
      b = readByte();
      result |= (b & 0x7F) << 7;
      if ((b & 0x80) != 0) {
        b = readByte();
        result |= (b & 0x7F) << 14;
        if ((b & 0x80) != 0) {
          b = readByte();
          result |= (b & 0x7F) << 21;
          if ((b & 0x80) != 0) {
            b = readByte();
            result |= (b & 0x7F) << 28;
            if ((b & 0x80) != 0) {
              b = readByte();
              result |= (b & 0x7F) << 35;
              if ((b & 0x80) != 0) {
                b = readByte();
                result |= (b & 0x7F) << 42;
                if ((b & 0x80) != 0) {
                  b = readByte();
                  result |= (b & 0x7F) << 49;
                  if ((b & 0x80) != 0) {
                    b = readByte();
                    // highest bit in last byte is symbols bit.
                    result |= b << 56;
                  }
                }
              }
            }
          }
        }
      }
    }
    return result;
  }

  /** Reads the 1-9 byte int part of an aligned varint. */
  public int readAlignedVarUInt32() {
    if (AndroidSupport.IS_ANDROID) {
      return slowReadAlignedVarUInt32();
    }
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    if (readerIdx < size - 10) {
      return slowReadAlignedVarUInt32();
    }
    long pos = address + readerIdx;
    long startPos = pos;
    int b = UNSAFE.getByte(heapMemory, pos++);
    // Mask first 6 bits,
    // bit 8 `set` indicates have next data bytes.
    int result = b & 0x3F;
    // Note:
    //  Loop are not used here to improve performance.
    //  We manually unroll the loop for better performance.
    if ((b & 0x80) != 0) { // has 2nd byte
      b = UNSAFE.getByte(heapMemory, pos++);
      result |= (b & 0x3F) << 6;
      if ((b & 0x80) != 0) { // has 3rd byte
        b = UNSAFE.getByte(heapMemory, pos++);
        result |= (b & 0x3F) << 12;
        if ((b & 0x80) != 0) { // has 4th byte
          b = UNSAFE.getByte(heapMemory, pos++);
          result |= (b & 0x3F) << 18;
          if ((b & 0x80) != 0) { // has 5th byte
            b = UNSAFE.getByte(heapMemory, pos++);
            result |= (b & 0x3F) << 24;
            if ((b & 0x80) != 0) { // has 6th byte
              b = UNSAFE.getByte(heapMemory, pos++);
              result |= (b & 0x3F) << 30;
            }
          }
        }
      }
    }
    pos = skipPadding(pos, b); // split method for `readVarUint` inlined
    readerIndex = (int) (pos - startPos + readerIdx);
    return result;
  }

  public int slowReadAlignedVarUInt32() {
    int b = readByte();
    // Mask first 6 bits,
    // bit 8 `set` indicates have next data bytes.
    int result = b & 0x3F;
    if ((b & 0x80) != 0) { // has 2nd byte
      b = readByte();
      result |= (b & 0x3F) << 6;
      if ((b & 0x80) != 0) { // has 3rd byte
        b = readByte();
        result |= (b & 0x3F) << 12;
        if ((b & 0x80) != 0) { // has 4th byte
          b = readByte();
          result |= (b & 0x3F) << 18;
          if ((b & 0x80) != 0) { // has 5th byte
            b = readByte();
            result |= (b & 0x3F) << 24;
            if ((b & 0x80) != 0) { // has 6th byte
              b = readByte();
              result |= (b & 0x3F) << 30;
            }
          }
        }
      }
    }
    // bit 7 `unset` indicates have next padding bytes,
    if ((b & 0x40) == 0) { // has first padding bytes
      b = readByte();
      if ((b & 0x40) == 0) { // has 2nd padding bytes
        b = readByte();
        if ((b & 0x40) == 0) { // has 3rd padding bytes
          b = readByte();
          checkArgument((b & 0x40) != 0, "At most 3 padding bytes.");
        }
      }
    }
    return result;
  }

  private long skipPadding(long pos, int b) {
    // bit 7 `unset` indicates have next padding bytes,
    if ((b & 0x40) == 0) { // has first padding bytes
      b = UNSAFE.getByte(heapMemory, pos++);
      if ((b & 0x40) == 0) { // has 2nd padding bytes
        b = UNSAFE.getByte(heapMemory, pos++);
        if ((b & 0x40) == 0) { // has 3rd padding bytes
          b = UNSAFE.getByte(heapMemory, pos++);
          checkArgument((b & 0x40) != 0, "At most 3 padding bytes.");
        }
      }
    }
    return pos;
  }

  public byte[] readBytes(int length) {
    int readerIdx = readerIndex;
    byte[] bytes = new byte[length];
    // use subtract to avoid overflow
    if (length > size - readerIdx) {
      streamReader.readTo(bytes, 0, length);
      return bytes;
    }
    byte[] heapMemory = this.heapMemory;
    if (heapMemory != null) {
      // System.arraycopy faster for some jdk than Unsafe.
      System.arraycopy(heapMemory, heapOffset + readerIdx, bytes, 0, length);
    } else {
      UnsafeOps.copyMemory(null, address + readerIdx, bytes, UnsafeOps.BYTE_ARRAY_OFFSET, length);
    }
    readerIndex = readerIdx + length;
    return bytes;
  }

  public void readBytes(byte[] dst, int dstIndex, int length) {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    if (readerIdx > size - length) {
      streamReader.readTo(dst, dstIndex, length);
      return;
    }
    if (dstIndex < 0 || dstIndex > dst.length - length) {
      throwIndexOOBExceptionForRead();
    }
    get(readerIdx, dst, dstIndex, length);
    readerIndex = readerIdx + length;
  }

  public void readBytes(byte[] dst) {
    readBytes(dst, 0, dst.length);
  }

  /** Read {@code len} bytes into a long using little-endian order. */
  public long readBytesAsInt64(int len) {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    int remaining = size - readerIdx;
    if (remaining >= 8) {
      readerIndex = readerIdx + len;
      if (AndroidSupport.IS_ANDROID) {
        return MemoryOps.getInt64(heapMemory, androidHeapIndexUnchecked(readerIdx))
            & (0xffffffffffffffffL >>> ((8 - len) * 8));
      }
      long v = UNSAFE.getLong(heapMemory, address + readerIdx);
      v = (LITTLE_ENDIAN ? v : Long.reverseBytes(v)) & (0xffffffffffffffffL >>> ((8 - len) * 8));
      return v;
    }
    return slowReadBytesAsInt64(remaining, len);
  }

  private long slowReadBytesAsInt64(int remaining, int len) {
    if (remaining < len) {
      streamReader.fillBuffer(len - remaining);
    }
    int readerIdx = readerIndex;
    readerIndex = readerIdx + len;
    long result = 0;
    byte[] heapMemory = this.heapMemory;
    if (heapMemory != null) {
      for (int i = 0, start = heapOffset + readerIdx; i < len; i++) {
        result |= (((long) heapMemory[start + i]) & 0xff) << (i * 8);
      }
    } else {
      long start = address + readerIdx;
      for (int i = 0; i < len; i++) {
        result |= ((long) UNSAFE.getByte(null, start + i) & 0xff) << (i * 8);
      }
    }
    return result;
  }

  public int read(ByteBuffer dst) {
    int readerIdx = readerIndex;
    int len = dst.remaining();
    // use subtract to avoid overflow
    if (readerIdx > size - len) {
      return streamReader.readToByteBuffer(dst);
    }
    if (heapMemory != null) {
      dst.put(heapMemory, readerIndex + heapOffset, len);
    } else {
      dst.put(sliceAsByteBuffer(readerIdx, len));
    }
    readerIndex = readerIdx + len;
    return len;
  }

  public void read(ByteBuffer dst, int len) {
    int readerIdx = readerIndex;
    // use subtract to avoid overflow
    if (readerIdx > size - len) {
      streamReader.readToByteBuffer(dst, len);
    } else {
      if (heapMemory != null) {
        dst.put(heapMemory, readerIndex + heapOffset, len);
      } else {
        dst.put(sliceAsByteBuffer(readerIdx, len));
      }
      readerIndex = readerIdx + len;
    }
  }

  /**
   * Read size for following binary, this method will check and fill readable bytes too. This method
   * is optimized for small size, it's faster than {@link #readVarUInt32}.
   */
  public int readBinarySize() {
    if (AndroidSupport.IS_ANDROID) {
      return readBinarySizeAndroid();
    }
    int binarySize;
    int readIdx = readerIndex;
    if (size - readIdx >= 5) {
      int fourByteValue = _unsafeGetInt32(readIdx++);
      binarySize = fourByteValue & 0x7F;
      // Duplicate and manual inline for performance.
      // noinspection Duplicates
      if ((fourByteValue & 0x80) != 0) {
        readIdx++;
        binarySize |= (fourByteValue >>> 1) & 0x3f80;
        if ((fourByteValue & 0x8000) != 0) {
          // merely executed path, make it as a separate method to reduce
          // code size of current method for better jvm inline
          return continueReadBinarySize(readIdx, fourByteValue, binarySize);
        }
      }
      readerIndex = readIdx;
    } else {
      binarySize = readVarUInt32Slow();
      readIdx = readerIndex;
    }
    int diff = size - readIdx;
    if (diff < binarySize) {
      streamReader.fillBuffer(diff);
    }
    return binarySize;
  }

  private int readBinarySizeAndroid() {
    int binarySize = readVarUInt32Android();
    int diff = size - readerIndex;
    if (diff < binarySize) {
      streamReader.fillBuffer(diff);
    }
    return binarySize;
  }

  private int continueReadBinarySize(int readIdx, int bulkRead, int binarySize) {
    // Duplicate and manual inline for performance.
    // noinspection Duplicates
    readIdx++;
    binarySize |= (bulkRead >>> 2) & 0x1fc000;
    if ((bulkRead & 0x800000) != 0) {
      readIdx++;
      binarySize |= (bulkRead >>> 3) & 0xfe00000;
      if ((bulkRead & 0x80000000) != 0) {
        int fifthByte = UNSAFE.getByte(heapMemory, address + readIdx++) & 0xFF;
        if ((fifthByte & 0xF0) != 0) {
          throwMalformedVarUInt32(fifthByte);
        }
        binarySize |= fifthByte << 28;
      }
    }
    int diff = size - readIdx;
    if (diff < binarySize) {
      streamReader.fillBuffer(diff);
    }
    return binarySize;
  }

  public byte[] readBytesAndSize() {
    final int numBytes = readBinarySize();
    int readerIdx = readerIndex;
    final byte[] arr = new byte[numBytes];
    // use subtract to avoid overflow
    if (readerIdx > size - numBytes) {
      streamReader.readTo(arr, 0, numBytes);
      return arr;
    }
    byte[] heapMemory = this.heapMemory;
    if (heapMemory != null) {
      System.arraycopy(heapMemory, heapOffset + readerIdx, arr, 0, numBytes);
    } else {
      UnsafeOps.UNSAFE.copyMemory(
          null, address + readerIdx, arr, UnsafeOps.BYTE_ARRAY_OFFSET, numBytes);
    }
    readerIndex = readerIdx + numBytes;
    return arr;
  }

  public boolean[] readBooleans(int numBytes) {
    boolean[] values = new boolean[numBytes];
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readBooleans(heapMemory, androidHeapIndexUnchecked(readerIdx), values, 0, numBytes);
      readerIndex = readerIdx + numBytes;
      return values;
    }
    readJvmPrimitiveArrayPayload(values, UnsafeOps.BOOLEAN_ARRAY_OFFSET, numBytes);
    return values;
  }

  public void readBooleans(boolean[] values, int offset, int numElements) {
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numElements);
      MemoryOps.readBooleans(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, offset, numElements);
      readerIndex = readerIdx + numElements;
      return;
    }
    readPrimitiveArray(values, UnsafeOps.BOOLEAN_ARRAY_OFFSET + offset, numElements);
  }

  /** This method should be used to read data written by {@link #writeCharsWithSize}. */
  public char[] readChars(int numBytes) {
    final char[] chars = new char[numBytes >> 1];
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readChars(heapMemory, androidHeapIndexUnchecked(readerIdx), chars, 0, chars.length);
      readerIndex = readerIdx + numBytes;
      return chars;
    }
    readJvmPrimitiveArrayPayload(chars, UnsafeOps.CHAR_ARRAY_OFFSET, numBytes);
    return chars;
  }

  public void readChars(char[] chars, int numElements) {
    readChars(chars, 0, numElements);
  }

  public void readChars(char[] chars, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 2);
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readChars(
          heapMemory, androidHeapIndexUnchecked(readerIdx), chars, offset, numElements);
      readerIndex = readerIdx + numBytes;
      return;
    }
    readPrimitiveArray(chars, UnsafeOps.CHAR_ARRAY_OFFSET + ((long) offset << 1), numBytes);
  }

  @CodegenInvoke
  public char[] readCharsAndSize() {
    final int numBytes = readBinarySize();
    return readChars(numBytes);
  }

  public short[] readShorts(int numBytes) {
    int numElements = numBytes >> 1;
    short[] values = new short[numElements];
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readShorts(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, 0, numElements);
      readerIndex = readerIdx + numBytes;
      return values;
    }
    readJvmPrimitiveArrayPayload(values, UnsafeOps.SHORT_ARRAY_OFFSET, numBytes);
    return values;
  }

  public void readShorts(short[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 2);
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readShorts(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, offset, numElements);
      readerIndex = readerIdx + numBytes;
      return;
    }
    readPrimitiveArray(values, UnsafeOps.SHORT_ARRAY_OFFSET + ((long) offset << 1), numBytes);
  }

  public int[] readInts(int numBytes) {
    int numElements = numBytes >> 2;
    int[] values = new int[numElements];
    if (AndroidSupport.IS_ANDROID) {
      readAndroidInts(values, numBytes, numElements);
    } else if (numElements <= SMALL_INT_ARRAY_DIRECT_READ_THRESHOLD) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      long pointer = address + readerIdx;
      for (int i = 0; i < numElements; i++) {
        values[i] = UNSAFE.getInt(heapMemory, pointer + ((long) i << 2));
      }
      readerIndex = readerIdx + numBytes;
    } else {
      readJvmPrimitiveArrayPayload(values, UnsafeOps.INT_ARRAY_OFFSET, numBytes);
    }
    return values;
  }

  public void readInts(int[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 4);
    if (AndroidSupport.IS_ANDROID) {
      readAndroidInts(values, offset, numElements, numBytes);
      return;
    }
    readPrimitiveArray(values, UnsafeOps.INT_ARRAY_OFFSET + ((long) offset << 2), numBytes);
  }

  private void readAndroidInts(int[] values, int numBytes, int numElements) {
    readAndroidInts(values, 0, numElements, numBytes);
  }

  private void readAndroidInts(int[] values, int offset, int numElements, int numBytes) {
    int readerIdx = preparePrimitiveArrayRead(numBytes);
    MemoryOps.readInts(
        heapMemory, androidHeapIndexUnchecked(readerIdx), values, offset, numElements);
    readerIndex = readerIdx + numBytes;
  }

  public long[] readLongs(int numBytes) {
    int numElements = numBytes >> 3;
    final long[] values = new long[numElements];
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readLongs(heapMemory, androidHeapIndexUnchecked(readerIdx), values, 0, numElements);
      readerIndex = readerIdx + numBytes;
      return values;
    }
    int readerIdx = readerIndex;
    if (readerIdx > size - numBytes) {
      checkReadableBytes(numBytes);
      readerIdx = readerIndex;
    }
    UNSAFE.copyMemory(
        heapMemory, address + readerIdx, values, UnsafeOps.LONG_ARRAY_OFFSET, numBytes);
    readerIndex = readerIdx + numBytes;
    return values;
  }

  public void readLongs(long[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 8);
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readLongs(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, offset, numElements);
      readerIndex = readerIdx + numBytes;
      return;
    }
    readPrimitiveArray(values, UnsafeOps.LONG_ARRAY_OFFSET + ((long) offset << 3), numBytes);
  }

  public float[] readFloats(int numBytes) {
    int numElements = numBytes >> 2;
    float[] values = new float[numElements];
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readFloats(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, 0, numElements);
      readerIndex = readerIdx + numBytes;
      return values;
    }
    readJvmPrimitiveArrayPayload(values, UnsafeOps.FLOAT_ARRAY_OFFSET, numBytes);
    return values;
  }

  public void readFloats(float[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 4);
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readFloats(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, offset, numElements);
      readerIndex = readerIdx + numBytes;
      return;
    }
    readPrimitiveArray(values, UnsafeOps.FLOAT_ARRAY_OFFSET + ((long) offset << 2), numBytes);
  }

  public double[] readDoubles(int numBytes) {
    int numElements = numBytes >> 3;
    double[] values = new double[numElements];
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readDoubles(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, 0, numElements);
      readerIndex = readerIdx + numBytes;
      return values;
    }
    readJvmPrimitiveArrayPayload(values, UnsafeOps.DOUBLE_ARRAY_OFFSET, numBytes);
    return values;
  }

  public void readDoubles(double[] values, int offset, int numElements) {
    int numBytes = Math.multiplyExact(numElements, 8);
    if (AndroidSupport.IS_ANDROID) {
      int readerIdx = preparePrimitiveArrayRead(numBytes);
      MemoryOps.readDoubles(
          heapMemory, androidHeapIndexUnchecked(readerIdx), values, offset, numElements);
      readerIndex = readerIdx + numBytes;
      return;
    }
    readPrimitiveArray(values, UnsafeOps.DOUBLE_ARRAY_OFFSET + ((long) offset << 3), numBytes);
  }

  private int preparePrimitiveArrayRead(int numBytes) {
    int readerIdx = readerIndex;
    if (readerIdx > size - numBytes) {
      checkReadableBytes(numBytes);
      readerIdx = readerIndex;
    }
    return readerIdx;
  }

  private void readPrimitiveArray(Object target, long targetPointer, int numBytes) {
    readJvmPrimitiveArrayPayload(target, targetPointer, numBytes);
  }

  private void readJvmPrimitiveArrayPayload(Object target, long targetPointer, int numBytes) {
    int readerIdx = preparePrimitiveArrayRead(numBytes);
    UNSAFE.copyMemory(heapMemory, address + readerIdx, target, targetPointer, numBytes);
    readerIndex = readerIdx + numBytes;
  }

  public void checkReadableBytes(int minimumReadableBytes) {
    // use subtract to avoid overflow
    int remaining = size - readerIndex;
    if (minimumReadableBytes > remaining) {
      streamReader.fillBuffer(minimumReadableBytes - remaining);
    }
  }

  /**
   * Returns internal byte array if data is on heap and remaining buffer size is equal to internal
   * byte array size, or create a new byte array which copy remaining data from off-heap.
   */
  public byte[] getRemainingBytes() {
    int length = size - readerIndex;
    if (heapMemory != null && size == length && heapOffset == 0) {
      return heapMemory;
    } else {
      return getBytes(readerIndex, length);
    }
  }

  // ------------------------- Read Methods Finished -------------------------------------

  public void copyTo(int offset, MemoryBuffer target, int targetOffset, int numBytes) {
    if (AndroidSupport.IS_ANDROID) {
      if (heapMemory != null && target.heapMemory != null) {
        if ((numBytes | offset | targetOffset) >= 0
            && offset <= size - numBytes
            && targetOffset <= target.size - numBytes) {
          MemoryOps.copy(
              heapMemory,
              heapOffset + offset,
              target.heapMemory,
              target.heapOffset + targetOffset,
              numBytes);
          return;
        }
      }
      throw new IndexOutOfBoundsException(
          String.format(
              "offset=%d, targetOffset=%d, numBytes=%d, size=%d, targetSize=%d",
              offset, targetOffset, numBytes, size, target.size));
    }
    final byte[] thisHeapRef = this.heapMemory;
    final byte[] otherHeapRef = target.heapMemory;
    final long thisPointer = this.address + offset;
    final long otherPointer = target.address + targetOffset;
    if ((numBytes | offset | targetOffset) >= 0
        && thisPointer <= this.addressLimit - numBytes
        && otherPointer <= target.addressLimit - numBytes) {
      UNSAFE.copyMemory(thisHeapRef, thisPointer, otherHeapRef, otherPointer, numBytes);
    } else {
      throw new IndexOutOfBoundsException(
          String.format(
              "offset=%d, targetOffset=%d, numBytes=%d, address=%d, targetAddress=%d",
              offset, targetOffset, numBytes, this.address, target.address));
    }
  }

  public void copyFrom(int offset, MemoryBuffer source, int sourcePointer, int numBytes) {
    source.copyTo(sourcePointer, this, offset, numBytes);
  }

  /**
   * JVM-only bulk copy method. Copies {@code numBytes} bytes to target unsafe object and pointer.
   * Throws on Android before executing unsafe memory access.
   */
  public void copyToUnsafe(long offset, Object target, long targetPointer, int numBytes) {
    if (AndroidSupport.IS_ANDROID) {
      throw new UnsupportedOperationException("Raw unsafe memory copy is not supported on Android");
    }
    final long thisPointer = this.address + offset;
    checkArgument(thisPointer + numBytes <= addressLimit);
    UNSAFE.copyMemory(this.heapMemory, thisPointer, target, targetPointer, numBytes);
  }

  /**
   * JVM-only bulk copy method. Copies {@code numBytes} bytes from source unsafe object and pointer.
   * Throws on Android before executing unsafe memory access.
   */
  public void copyFromUnsafe(long offset, Object source, long sourcePointer, long numBytes) {
    if (AndroidSupport.IS_ANDROID) {
      throw new UnsupportedOperationException("Raw unsafe memory copy is not supported on Android");
    }
    final long thisPointer = this.address + offset;
    checkArgument(thisPointer + numBytes <= addressLimit);
    UNSAFE.copyMemory(source, sourcePointer, heapMemory, thisPointer, numBytes);
  }

  public byte[] getBytes(int index, int length) {
    if (index == 0 && heapMemory != null && heapOffset == 0) {
      // Arrays.copyOf is an intrinsics, which is faster
      return Arrays.copyOf(heapMemory, length);
    }
    if (index + length > size) {
      throwIndexOOBExceptionForRead(length);
    }
    byte[] data = new byte[length];
    get(index, data, 0, length);
    return data;
  }

  public void getBytes(int index, byte[] dst, int dstIndex, int length) {
    if (dstIndex > dst.length - length) {
      throwOOBException();
    }
    if (index > size - length) {
      throwOOBException();
    }
    get(index, dst, dstIndex, length);
  }

  public MemoryBuffer slice(int offset) {
    return slice(offset, size - offset);
  }

  public MemoryBuffer slice(int offset, int length) {
    if (offset + length > size) {
      throwOOBExceptionForRange(offset, length);
    }
    if (heapMemory != null) {
      return new MemoryBuffer(heapMemory, heapOffset + offset, length);
    } else {
      return new MemoryBuffer(address + offset, length, offHeapBuffer);
    }
  }

  public ByteBuffer sliceAsByteBuffer() {
    return sliceAsByteBuffer(readerIndex, size - readerIndex);
  }

  public ByteBuffer sliceAsByteBuffer(int offset, int length) {
    if (offset + length > size) {
      throwOOBExceptionForRange(offset, length);
    }
    if (heapMemory != null) {
      return ByteBuffer.wrap(heapMemory, heapOffset + offset, length).slice();
    } else {
      ByteBuffer offHeapBuffer = this.offHeapBuffer;
      if (offHeapBuffer != null) {
        ByteBuffer duplicate = offHeapBuffer.duplicate();
        int start = (int) (address - directBufferAddress(duplicate));
        ByteBufferUtil.position(duplicate, start + offset);
        duplicate.limit(start + offset + length);
        return duplicate.slice();
      } else {
        throw new IllegalStateException("Memory buffer does not own a ByteBuffer");
      }
    }
  }

  private void throwOOBExceptionForRange(int offset, int length) {
    throw new IndexOutOfBoundsException(
        String.format("offset(%d) + length(%d) exceeds size(%d): %s", offset, length, size, this));
  }

  public ForyStreamReader getStreamReader() {
    return streamReader;
  }

  /**
   * Equals two memory buffer regions.
   *
   * @param buf2 Buffer to equal this buffer with
   * @param offset1 Offset of this buffer to start equaling
   * @param offset2 Offset of buf2 to start equaling
   * @param len Length of the equaled memory region
   * @return true if buffers equal or len zero, false otherwise
   */
  public boolean equalTo(MemoryBuffer buf2, int offset1, int offset2, int len) {
    if (len == 0) {
      return buf2 != null;
    }
    if (AndroidSupport.IS_ANDROID) {
      checkArgument(offset1 >= 0 && offset1 <= size - len);
      checkArgument(buf2 != null && offset2 >= 0 && offset2 <= buf2.size - len);
      int pos1 = androidHeapIndexUnchecked(offset1);
      int pos2 = buf2.androidHeapIndexUnchecked(offset2);
      for (int i = 0; i < len; i++) {
        if (heapMemory[pos1 + i] != buf2.heapMemory[pos2 + i]) {
          return false;
        }
      }
      return true;
    }
    final long pos1 = address + offset1;
    final long pos2 = buf2.address + offset2;
    checkArgument(pos1 < addressLimit);
    checkArgument(pos2 < buf2.addressLimit);
    return UnsafeOps.arrayEquals(heapMemory, pos1, buf2.heapMemory, pos2, len);
  }

  /**
   * Equals a memory buffer region with a byte array region.
   *
   * @param bytes Array to compare with
   * @param bytesOffset Offset of bytes to start comparing
   * @param offset Offset of this buffer to start comparing
   * @param len Length of the compared memory region
   * @return true if regions are equal or len zero, false otherwise
   */
  public boolean equalTo(byte[] bytes, int bytesOffset, int offset, int len) {
    checkArgument(bytes != null);
    checkArgument(len >= 0);
    checkArgument(bytesOffset >= 0 && bytesOffset <= bytes.length - len);
    checkArgument(offset >= 0 && offset <= size - len);
    if (len == 0) {
      return true;
    }
    if (AndroidSupport.IS_ANDROID) {
      int pos = androidHeapIndexUnchecked(offset);
      for (int i = 0; i < len; i++) {
        if (heapMemory[pos + i] != bytes[bytesOffset + i]) {
          return false;
        }
      }
      return true;
    }
    final long pos = address + offset;
    return UnsafeOps.arrayEquals(
        heapMemory, pos, bytes, UnsafeOps.BYTE_ARRAY_OFFSET + bytesOffset, len);
  }

  private static final class AndroidHeapMemoryBuffer extends MemoryBuffer {
    private AndroidHeapMemoryBuffer(byte[] buffer, int offset, int length) {
      super(buffer, offset, length);
    }

    private AndroidHeapMemoryBuffer(
        byte[] buffer, int offset, int length, ForyStreamReader streamReader) {
      super(buffer, offset, length, streamReader);
    }

    @Override
    public void putByte(int index, int b) {
      MemoryOps.putByte(androidHeapMemory(this), androidHeapIndex(this, index, 1), (byte) b);
    }

    @Override
    public void putByte(int index, byte b) {
      MemoryOps.putByte(androidHeapMemory(this), androidHeapIndex(this, index, 1), b);
    }

    @Override
    public void _unsafePutByte(int index, byte b) {
      MemoryOps.putByte(androidHeapMemory(this), androidHeapIndexUnchecked(this, index), b);
    }

    @Override
    public void putInt16(int index, short value) {
      MemoryOps.putInt16(androidHeapMemory(this), androidHeapIndex(this, index, 2), value);
    }

    @Override
    public void _unsafePutInt16(int index, short value) {
      MemoryOps.putInt16(androidHeapMemory(this), androidHeapIndexUnchecked(this, index), value);
    }

    @Override
    public void putInt32(int index, int value) {
      MemoryOps.putInt32(androidHeapMemory(this), androidHeapIndex(this, index, 4), value);
    }

    @Override
    public void _unsafePutInt32(int index, int value) {
      MemoryOps.putInt32(androidHeapMemory(this), androidHeapIndexUnchecked(this, index), value);
    }

    @Override
    public void putInt64(int index, long value) {
      MemoryOps.putInt64(androidHeapMemory(this), androidHeapIndex(this, index, 8), value);
    }

    @Override
    public void _unsafePutInt64(int index, long value) {
      MemoryOps.putInt64(androidHeapMemory(this), androidHeapIndexUnchecked(this, index), value);
    }

    @Override
    public void writeBoolean(boolean value) {
      final int writerIdx = androidWriterIndex(this);
      final int newIdx = writerIdx + 1;
      ensure(newIdx);
      MemoryOps.putBoolean(
          androidHeapMemory(this), androidHeapIndexUnchecked(this, writerIdx), value);
      androidWriterIndex(this, newIdx);
    }

    @Override
    public void _unsafeWriteByte(byte value) {
      final int writerIdx = androidWriterIndex(this);
      MemoryOps.putByte(androidHeapMemory(this), androidHeapIndexUnchecked(this, writerIdx), value);
      androidWriterIndex(this, writerIdx + 1);
    }

    @Override
    public void writeByte(byte value) {
      final int writerIdx = androidWriterIndex(this);
      final int newIdx = writerIdx + 1;
      ensure(newIdx);
      MemoryOps.putByte(androidHeapMemory(this), androidHeapIndexUnchecked(this, writerIdx), value);
      androidWriterIndex(this, newIdx);
    }

    @Override
    public void writeByte(int value) {
      writeByte((byte) value);
    }

    @Override
    public void writeInt16(short value) {
      final int writerIdx = androidWriterIndex(this);
      final int newIdx = writerIdx + 2;
      ensure(newIdx);
      MemoryOps.putInt16(
          androidHeapMemory(this), androidHeapIndexUnchecked(this, writerIdx), value);
      androidWriterIndex(this, newIdx);
    }

    @Override
    public void writeInt32(int value) {
      final int writerIdx = androidWriterIndex(this);
      final int newIdx = writerIdx + 4;
      ensure(newIdx);
      MemoryOps.putInt32(
          androidHeapMemory(this), androidHeapIndexUnchecked(this, writerIdx), value);
      androidWriterIndex(this, newIdx);
    }

    @Override
    public void writeInt64(long value) {
      final int writerIdx = androidWriterIndex(this);
      final int newIdx = writerIdx + 8;
      ensure(newIdx);
      MemoryOps.putInt64(
          androidHeapMemory(this), androidHeapIndexUnchecked(this, writerIdx), value);
      androidWriterIndex(this, newIdx);
    }

    @Override
    public int writeVarUInt32Small7(int value) {
      final int writerIdx = androidWriterIndex(this);
      ensure(writerIdx + 8);
      int varintBytes =
          MemoryOps.putVarUInt32Small7(
              androidHeapMemory(this), androidHeapIndexUnchecked(this, writerIdx), value);
      androidWriterIndex(this, writerIdx + varintBytes);
      return varintBytes;
    }

    @Override
    public int _unsafePutVarUInt32(int index, int value) {
      return MemoryOps.putVarUInt32(
          androidHeapMemory(this), androidHeapIndexUnchecked(this, index), value);
    }

    @Override
    public MemoryBuffer slice(int offset, int length) {
      if (offset + length > androidSize(this)) {
        throwOOBExceptionForRange(this, offset, length);
      }
      return new AndroidHeapMemoryBuffer(
          androidHeapMemory(this), androidHeapOffset(this) + offset, length);
    }
  }

  @Override
  public String toString() {
    return "MemoryBuffer{"
        + "size="
        + size
        + ", readerIndex="
        + readerIndex
        + ", writerIndex="
        + writerIndex
        + ", heapMemory="
        + (heapMemory == null ? null : "len(" + heapMemory.length + ")")
        + ", heapOffset="
        + heapOffset
        + ", offHeapBuffer="
        + offHeapBuffer
        + ", address="
        + address
        + ", addressLimit="
        + addressLimit
        + '}';
  }

  // ------------------------------------------------------------------------
  // Memory Allocator Support
  // ------------------------------------------------------------------------

  /** Default memory allocator that uses the original heap-based allocation strategy. */
  private static final class DefaultMemoryAllocator implements MemoryAllocator {
    @Override
    public MemoryBuffer allocate(int initialSize) {
      return fromByteArray(new byte[initialSize]);
    }

    @Override
    public void grow(MemoryBuffer buffer, int newCapacity) {
      if (newCapacity <= buffer.size()) {
        return;
      }

      int newSize =
          newCapacity < BUFFER_GROW_STEP_THRESHOLD
              ? newCapacity << 1
              : (int) Math.min(newCapacity * 1.5d, Integer.MAX_VALUE - 8);

      byte[] data = new byte[newSize];
      buffer.get(0, data, 0, buffer.size());
      buffer.initHeapBuffer(data, 0, data.length);
    }
  }

  /**
   * Sets the global memory allocator. This affects all new MemoryBuffer allocations and growth
   * operations.
   *
   * @param allocator the new global allocator to use
   * @throws NullPointerException if allocator is null
   */
  public static void setGlobalAllocator(MemoryAllocator allocator) {
    if (allocator == null) {
      throw new NullPointerException("Memory allocator cannot be null");
    }
    globalAllocator = allocator;
  }

  /**
   * Gets the current global memory allocator.
   *
   * @return the current global allocator
   */
  public static MemoryAllocator getGlobalAllocator() {
    return globalAllocator;
  }

  /** Point this buffer to a new byte array. */
  public void pointTo(byte[] buffer, int offset, int length) {
    initHeapBuffer(buffer, offset, length);
  }

  /** Creates a new memory buffer that targets to the given heap memory region. */
  public static MemoryBuffer fromByteArray(byte[] buffer, int offset, int length) {
    if (AndroidSupport.IS_ANDROID) {
      return new AndroidHeapMemoryBuffer(buffer, offset, length);
    }
    return new MemoryBuffer(buffer, offset, length, null);
  }

  public static MemoryBuffer fromByteArray(
      byte[] buffer, int offset, int length, ForyStreamReader streamReader) {
    if (AndroidSupport.IS_ANDROID) {
      return new AndroidHeapMemoryBuffer(buffer, offset, length, streamReader);
    }
    return new MemoryBuffer(buffer, offset, length, streamReader);
  }

  /** Creates a new memory buffer that targets to the given heap memory region. */
  public static MemoryBuffer fromByteArray(byte[] buffer) {
    if (AndroidSupport.IS_ANDROID) {
      return new AndroidHeapMemoryBuffer(buffer, 0, buffer.length);
    }
    return new MemoryBuffer(buffer, 0, buffer.length);
  }

  /**
   * Creates a new memory buffer that represents the memory backing the given byte buffer section of
   * {@code [buffer.position(), buffer.limit())}. The buffer will change into a heap buffer
   * automatically if not enough.
   *
   * @param buffer a direct buffer or heap buffer
   */
  public static MemoryBuffer fromByteBuffer(ByteBuffer buffer) {
    if (AndroidSupport.IS_ANDROID) {
      ByteBuffer duplicate = buffer.duplicate();
      byte[] bytes = new byte[duplicate.remaining()];
      duplicate.get(bytes);
      return fromByteArray(bytes);
    } else if (buffer.isDirect()) {
      return new MemoryBuffer(
          directBufferAddress(buffer) + buffer.position(), buffer.remaining(), buffer);
    } else if (buffer.hasArray()) {
      int offset = buffer.arrayOffset() + buffer.position();
      return new MemoryBuffer(buffer.array(), offset, buffer.remaining());
    } else {
      ByteBuffer duplicate = buffer.duplicate();
      byte[] bytes = new byte[duplicate.remaining()];
      duplicate.get(bytes);
      return fromByteArray(bytes);
    }
  }

  public static MemoryBuffer fromDirectByteBuffer(
      ByteBuffer buffer, int size, ForyStreamReader streamReader) {
    if (AndroidSupport.IS_ANDROID) {
      throw new UnsupportedOperationException(
          "Direct ByteBuffer memory is not supported on Android");
    }
    long offHeapAddress = directBufferAddress(buffer) + buffer.position();
    return new MemoryBuffer(offHeapAddress, size, buffer, streamReader);
  }

  /**
   * Create a heap buffer of specified initial size. The buffer will grow automatically if not
   * enough.
   */
  public static MemoryBuffer newHeapBuffer(int initialSize) {
    return globalAllocator.allocate(initialSize);
  }
}
