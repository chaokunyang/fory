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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.IdentityHashMap;
import org.apache.fory.Fory;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.resolver.ClassInfo;
import org.apache.fory.resolver.ClassInfoHolder;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.serializer.collection.CollectionFlags;
import org.apache.fory.serializer.collection.ForyArrayAsListSerializer;
import org.apache.fory.type.GenericType;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.type.Types;
import org.apache.fory.util.Preconditions;

/** Serializers for array types. */
public class ArraySerializers {

  /** May be multi-dimension array, or multi-dimension primitive array. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static final class ObjectArraySerializer<T> extends Serializer<T[]> {
    private final Class<T> innerType;
    private final Serializer componentTypeSerializer;
    private final ClassInfoHolder classInfoHolder;
    private final int[] stubDims;
    private final GenericType componentGenericType;

    public ObjectArraySerializer(Fory fory, Class<T[]> cls) {
      super(fory, cls);
      fory.getClassResolver().setSerializer(cls, this);
      Preconditions.checkArgument(cls.isArray());
      Class<?> t = cls;
      Class<?> innerType = cls;
      int dimension = 0;
      while (t != null && t.isArray()) {
        dimension++;
        t = t.getComponentType();
        if (t != null) {
          innerType = t;
        }
      }
      this.innerType = (Class<T>) innerType;
      Class<?> componentType = cls.getComponentType();
      componentGenericType = fory.getClassResolver().buildGenericType(componentType);
      if (fory.getClassResolver().isMonomorphic(componentType)) {
        if (fory.isCrossLanguage()) {
          this.componentTypeSerializer = null;
        } else {
          this.componentTypeSerializer = fory.getClassResolver().getSerializer(componentType);
        }
      } else {
        // TODO add ClassInfo cache for non-final component type.
        this.componentTypeSerializer = null;
      }
      this.stubDims = new int[dimension];
      classInfoHolder = fory.getClassResolver().nilClassInfoHolder();
    }

    @Override
    public void write(MemoryBuffer buffer, T[] arr) {
      int len = arr.length;
      RefResolver refResolver = fory.getRefResolver();
      Serializer componentSerializer = this.componentTypeSerializer;
      int header = componentSerializer != null ? 0b1 : 0b0;
      buffer.writeVarUint32Small7(len << 1 | header);
      if (componentSerializer != null) {
        for (T t : arr) {
          if (!refResolver.writeRefOrNull(buffer, t)) {
            componentSerializer.write(buffer, t);
          }
        }
      } else {
        Fory fory = this.fory;
        ClassResolver classResolver = fory.getClassResolver();
        ClassInfo classInfo = null;
        Class<?> elemClass = null;
        for (T t : arr) {
          if (!refResolver.writeRefOrNull(buffer, t)) {
            Class<?> clz = t.getClass();
            if (clz != elemClass) {
              elemClass = clz;
              classInfo = classResolver.getClassInfo(clz);
            }
            fory.writeNonRef(buffer, t, classInfo);
          }
        }
      }
    }

    @Override
    public T[] copy(T[] originArray) {
      int length = originArray.length;
      Object[] newArray = newArray(length);
      if (needToCopyRef) {
        fory.reference(originArray, newArray);
      }
      Serializer componentSerializer = this.componentTypeSerializer;
      if (componentSerializer != null) {
        if (componentSerializer.isImmutable()) {
          System.arraycopy(originArray, 0, newArray, 0, length);
        } else {
          for (int i = 0; i < length; i++) {
            newArray[i] = componentSerializer.copy(originArray[i]);
          }
        }
      } else {
        for (int i = 0; i < length; i++) {
          newArray[i] = fory.copyObject(originArray[i]);
        }
      }
      return (T[]) newArray;
    }

    @Override
    public void xwrite(MemoryBuffer buffer, T[] arr) {
      int len = arr.length;
      buffer.writeVarUint32Small7(len);
      // TODO(chaokunyang) use generics by creating component serializers to multi-dimension array.
      for (T t : arr) {
        fory.xwriteRef(buffer, t);
      }
    }

    @Override
    public T[] read(MemoryBuffer buffer) {
      int numElements = buffer.readVarUint32Small7();
      boolean isFinal = (numElements & 0b1) != 0;
      numElements >>>= 1;
      Object[] value = newArray(numElements);
      RefResolver refResolver = fory.getRefResolver();
      refResolver.reference(value);
      if (isFinal) {
        final Serializer componentTypeSerializer = this.componentTypeSerializer;
        for (int i = 0; i < numElements; i++) {
          Object elem;
          int nextReadRefId = refResolver.tryPreserveRefId(buffer);
          if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
            elem = componentTypeSerializer.read(buffer);
            refResolver.setReadObject(nextReadRefId, elem);
          } else {
            elem = refResolver.getReadObject();
          }
          value[i] = elem;
        }
      } else {
        Fory fory = this.fory;
        ClassInfoHolder classInfoHolder = this.classInfoHolder;
        for (int i = 0; i < numElements; i++) {
          int nextReadRefId = refResolver.tryPreserveRefId(buffer);
          Object o;
          if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
            // ref value or not-null value
            o = fory.readNonRef(buffer, classInfoHolder);
            refResolver.setReadObject(nextReadRefId, o);
          } else {
            o = refResolver.getReadObject();
          }
          value[i] = o;
        }
      }
      return (T[]) value;
    }

    @Override
    public T[] xread(MemoryBuffer buffer) {
      int numElements = buffer.readVarUint32Small7();
      Object[] value = newArray(numElements);
      fory.getGenerics().pushGenericType(componentGenericType);
      for (int i = 0; i < numElements; i++) {
        Object x = fory.xreadRef(buffer);
        value[i] = x;
      }
      fory.getGenerics().popGenericType();
      return (T[]) value;
    }

    private Object[] newArray(int numElements) {
      Object[] value;
      if ((Class) type == Object[].class) {
        value = new Object[numElements];
      } else {
        stubDims[0] = numElements;
        value = (Object[]) Array.newInstance(innerType, stubDims);
      }
      return value;
    }
  }

  public static final class PrimitiveArrayBufferObject implements BufferObject {
    private final Object array;
    private final int offset;
    private final int elemSize;
    private final int length;

    public PrimitiveArrayBufferObject(Object array, int offset, int elemSize, int length) {
      this.array = array;
      this.offset = offset;
      this.elemSize = elemSize;
      this.length = length;
    }

    @Override
    public int totalBytes() {
      return length * elemSize;
    }

    @Override
    public void writeTo(MemoryBuffer buffer) {
      int size = Math.multiplyExact(length, elemSize);
      int writerIndex = buffer.writerIndex();
      int end = writerIndex + size;
      buffer.ensure(end);
      buffer.copyFromUnsafe(writerIndex, array, offset, size);
      buffer.writerIndex(end);
    }

    @Override
    public MemoryBuffer toBuffer() {
      MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(totalBytes());
      writeTo(buffer);
      return buffer.slice(0, buffer.writerIndex());
    }
  }

  // Implement all read/write methods in subclasses to avoid
  // virtual method call cost.
  public abstract static class PrimitiveArraySerializer<T>
      extends Serializers.CrossLanguageCompatibleSerializer<T> {
    protected final int offset;
    protected final int elemSize;

    public PrimitiveArraySerializer(Fory fory, Class<T> cls) {
      super(fory, cls);
      Class<?> innerType = TypeUtils.getArrayComponentInfo(cls).f0;
      this.offset = primitiveInfo.get(innerType)[0];
      this.elemSize = primitiveInfo.get(innerType)[1];
    }

    @Override
    public void xwrite(MemoryBuffer buffer, T value) {
      write(buffer, value);
    }

    @Override
    public T xread(MemoryBuffer buffer) {
      return read(buffer);
    }
  }

  public static final class BooleanArraySerializer extends PrimitiveArraySerializer<boolean[]> {

    public BooleanArraySerializer(Fory fory) {
      super(fory, boolean[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, boolean[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, elemSize);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, elemSize, value.length));
      }
    }

    @Override
    public boolean[] copy(boolean[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public boolean[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        int numElements = size / elemSize;
        boolean[] values = new boolean[numElements];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        int numElements = size / elemSize;
        boolean[] values = new boolean[numElements];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }
  }

  public static final class ByteArraySerializer extends PrimitiveArraySerializer<byte[]> {

    public ByteArraySerializer(Fory fory) {
      super(fory, byte[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, byte[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, 1);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, 1, value.length));
      }
    }

    @Override
    public byte[] copy(byte[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public byte[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        byte[] values = new byte[size];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        byte[] values = new byte[size];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }
  }

  public static final class CharArraySerializer extends PrimitiveArraySerializer<char[]> {

    public CharArraySerializer(Fory fory) {
      super(fory, char[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, char[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, elemSize);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, elemSize, value.length));
      }
    }

    @Override
    public char[] copy(char[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public char[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        int numElements = size / elemSize;
        char[] values = new char[numElements];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        int numElements = size / elemSize;
        char[] values = new char[numElements];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }

    @Override
    public void xwrite(MemoryBuffer buffer, char[] value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public char[] xread(MemoryBuffer buffer) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class ShortArraySerializer extends PrimitiveArraySerializer<short[]> {

    public ShortArraySerializer(Fory fory) {
      super(fory, short[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, short[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, elemSize);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, elemSize, value.length));
      }
    }

    @Override
    public short[] copy(short[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public short[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        int numElements = size / elemSize;
        short[] values = new short[numElements];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        int numElements = size / elemSize;
        short[] values = new short[numElements];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }
  }

  public static final class IntArraySerializer extends PrimitiveArraySerializer<int[]> {

    public IntArraySerializer(Fory fory) {
      super(fory, int[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, int[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, elemSize);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, elemSize, value.length));
      }
    }

    @Override
    public int[] copy(int[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public int[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        int numElements = size / elemSize;
        int[] values = new int[numElements];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        int numElements = size / elemSize;
        int[] values = new int[numElements];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }
  }

  public static final class LongArraySerializer extends PrimitiveArraySerializer<long[]> {

    public LongArraySerializer(Fory fory) {
      super(fory, long[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, long[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, elemSize);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, elemSize, value.length));
      }
    }

    @Override
    public long[] copy(long[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public long[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        int numElements = size / elemSize;
        long[] values = new long[numElements];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        int numElements = size / elemSize;
        long[] values = new long[numElements];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }
  }

  public static final class FloatArraySerializer extends PrimitiveArraySerializer<float[]> {

    public FloatArraySerializer(Fory fory) {
      super(fory, float[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, float[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, elemSize);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, elemSize, value.length));
      }
    }

    @Override
    public float[] copy(float[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public float[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        int numElements = size / elemSize;
        float[] values = new float[numElements];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        int numElements = size / elemSize;
        float[] values = new float[numElements];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }
  }

  public static final class DoubleArraySerializer extends PrimitiveArraySerializer<double[]> {

    public DoubleArraySerializer(Fory fory) {
      super(fory, double[].class);
    }

    @Override
    public void write(MemoryBuffer buffer, double[] value) {
      if (fory.getBufferCallback() == null) {
        int size = Math.multiplyExact(value.length, elemSize);
        buffer.writePrimitiveArrayWithSize(value, offset, size);
      } else {
        fory.writeBufferObject(
            buffer, new PrimitiveArrayBufferObject(value, offset, elemSize, value.length));
      }
    }

    @Override
    public double[] copy(double[] originArray) {
      return Arrays.copyOf(originArray, originArray.length);
    }

    @Override
    public double[] read(MemoryBuffer buffer) {
      if (fory.isPeerOutOfBandEnabled()) {
        MemoryBuffer buf = fory.readBufferObject(buffer);
        int size = buf.remaining();
        int numElements = size / elemSize;
        double[] values = new double[numElements];
        buf.copyToUnsafe(0, values, offset, size);
        return values;
      } else {
        int size = buffer.readVarUint32Small7();
        int numElements = size / elemSize;
        double[] values = new double[numElements];
        buffer.readToUnsafe(values, offset, size);
        return values;
      }
    }
  }

  public static final class StringArraySerializer extends Serializer<String[]> {
    private final StringSerializer stringSerializer;
    private final ForyArrayAsListSerializer collectionSerializer;
    private final ForyArrayAsListSerializer.ArrayAsList list;

    public StringArraySerializer(Fory fory) {
      super(fory, String[].class);
      stringSerializer = new StringSerializer(fory);
      collectionSerializer = new ForyArrayAsListSerializer(fory);
      collectionSerializer.setElementSerializer(stringSerializer);
      list = new ForyArrayAsListSerializer.ArrayAsList(0);
    }

    @Override
    public void write(MemoryBuffer buffer, String[] value) {
      int len = value.length;
      buffer.writeVarUint32Small7(len);
      if (len == 0) {
        return;
      }
      list.setArray(value);
      // TODO reference support
      // this method won't throw exception.
      int flags = collectionSerializer.writeNullabilityHeader(buffer, list);
      list.clearArray(); // clear for gc
      StringSerializer stringSerializer = this.stringSerializer;
      if ((flags & CollectionFlags.HAS_NULL) != CollectionFlags.HAS_NULL) {
        for (String elem : value) {
          stringSerializer.write(buffer, elem);
        }
      } else {
        for (String elem : value) {
          if (elem == null) {
            buffer.writeByte(Fory.NULL_FLAG);
          } else {
            buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
            stringSerializer.write(buffer, elem);
          }
        }
      }
    }

    @Override
    public String[] copy(String[] originArray) {
      String[] newArray = new String[originArray.length];
      System.arraycopy(originArray, 0, newArray, 0, originArray.length);
      return newArray;
    }

    @Override
    public String[] read(MemoryBuffer buffer) {
      int numElements = buffer.readVarUint32Small7();
      String[] value = new String[numElements];
      if (numElements == 0) {
        return value;
      }
      int flags = buffer.readByte();
      StringSerializer serializer = this.stringSerializer;
      if ((flags & CollectionFlags.HAS_NULL) != CollectionFlags.HAS_NULL) {
        for (int i = 0; i < numElements; i++) {
          value[i] = serializer.readJavaString(buffer);
        }
      } else {
        for (int i = 0; i < numElements; i++) {
          if (buffer.readByte() != Fory.NULL_FLAG) {
            value[i] = serializer.readJavaString(buffer);
          }
        }
      }
      return value;
    }

    @Override
    public void xwrite(MemoryBuffer buffer, String[] value) {
      int len = value.length;
      buffer.writeVarUint32Small7(len);
      for (String elem : value) {
        if (elem != null) {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          stringSerializer.writeString(buffer, elem);
        } else {
          buffer.writeByte(Fory.NULL_FLAG);
        }
      }
    }

    @Override
    public String[] xread(MemoryBuffer buffer) {
      int numElements = buffer.readVarUint32Small7();
      String[] value = new String[numElements];
      for (int i = 0; i < numElements; i++) {
        if (buffer.readByte() >= Fory.NOT_NULL_VALUE_FLAG) {
          value[i] = stringSerializer.readString(buffer);
        } else {
          value[i] = null;
        }
      }
      return value;
    }
  }

  public static void registerDefaultSerializers(Fory fory) {
    ClassResolver resolver = fory.getClassResolver();
    resolver.registerSerializer(Object[].class, new ObjectArraySerializer<>(fory, Object[].class));
    resolver.registerSerializer(Class[].class, new ObjectArraySerializer<>(fory, Class[].class));
    resolver.registerSerializer(byte[].class, new ByteArraySerializer(fory));
    resolver.registerSerializer(Byte[].class, new ObjectArraySerializer<>(fory, Byte[].class));
    resolver.registerSerializer(char[].class, new CharArraySerializer(fory));
    resolver.registerSerializer(
        Character[].class, new ObjectArraySerializer<>(fory, Character[].class));
    resolver.registerSerializer(short[].class, new ShortArraySerializer(fory));
    resolver.registerSerializer(Short[].class, new ObjectArraySerializer<>(fory, Short[].class));
    resolver.registerSerializer(int[].class, new IntArraySerializer(fory));
    resolver.registerSerializer(
        Integer[].class, new ObjectArraySerializer<>(fory, Integer[].class));
    resolver.registerSerializer(long[].class, new LongArraySerializer(fory));
    resolver.registerSerializer(Long[].class, new ObjectArraySerializer<>(fory, Long[].class));
    resolver.registerSerializer(float[].class, new FloatArraySerializer(fory));
    resolver.registerSerializer(Float[].class, new ObjectArraySerializer<>(fory, Float[].class));
    resolver.registerSerializer(double[].class, new DoubleArraySerializer(fory));
    resolver.registerSerializer(Double[].class, new ObjectArraySerializer<>(fory, Double[].class));
    resolver.registerSerializer(boolean[].class, new BooleanArraySerializer(fory));
    resolver.registerSerializer(
        Boolean[].class, new ObjectArraySerializer<>(fory, Boolean[].class));
    resolver.registerSerializer(String[].class, new StringArraySerializer(fory));
  }

  // ########################## utils ##########################

  static void writePrimitiveArray(
      MemoryBuffer buffer, Object arr, int offset, int numElements, int elemSize) {
    int size = Math.multiplyExact(numElements, elemSize);
    buffer.writeVarUint32Small7(size);
    int writerIndex = buffer.writerIndex();
    int end = writerIndex + size;
    buffer.ensure(end);
    buffer.copyFromUnsafe(writerIndex, arr, offset, size);
    buffer.writerIndex(end);
  }

  public static PrimitiveArrayBufferObject byteArrayBufferObject(byte[] array) {
    return new PrimitiveArrayBufferObject(array, Platform.BYTE_ARRAY_OFFSET, 1, array.length);
  }

  static final IdentityHashMap<Class<?>, int[]> primitiveInfo = new IdentityHashMap<>();

  static {
    primitiveInfo.put(
        boolean.class, new int[] {Platform.BOOLEAN_ARRAY_OFFSET, 1, Types.BOOL_ARRAY});
    primitiveInfo.put(byte.class, new int[] {Platform.BYTE_ARRAY_OFFSET, 1, Types.BINARY});
    primitiveInfo.put(
        char.class, new int[] {Platform.CHAR_ARRAY_OFFSET, 2, Fory.NOT_SUPPORT_XLANG});
    primitiveInfo.put(short.class, new int[] {Platform.SHORT_ARRAY_OFFSET, 2, Types.INT16_ARRAY});
    primitiveInfo.put(int.class, new int[] {Platform.INT_ARRAY_OFFSET, 4, Types.INT32_ARRAY});
    primitiveInfo.put(long.class, new int[] {Platform.LONG_ARRAY_OFFSET, 8, Types.INT64_ARRAY});
    primitiveInfo.put(float.class, new int[] {Platform.FLOAT_ARRAY_OFFSET, 4, Types.FLOAT32_ARRAY});
    primitiveInfo.put(
        double.class, new int[] {Platform.DOUBLE_ARRAY_OFFSET, 8, Types.FLOAT64_ARRAY});
  }

  public abstract static class AbstractedNonexistentArrayClassSerializer extends Serializer {
    protected final String className;
    private final int dims;

    public AbstractedNonexistentArrayClassSerializer(
        Fory fory, String className, Class<?> stubClass) {
      super(fory, stubClass);
      this.className = className;
      this.dims = TypeUtils.getArrayDimensions(stubClass);
    }

    @Override
    public Object[] read(MemoryBuffer buffer) {
      switch (dims) {
        case 1:
          return read1DArray(buffer);
        case 2:
          return read2DArray(buffer);
        case 3:
          return read3DArray(buffer);
        default:
          throw new UnsupportedOperationException(
              String.format("Unsupported array dimension %s for class %s", dims, className));
      }
    }

    protected abstract Object readInnerElement(MemoryBuffer buffer);

    private Object[] read1DArray(MemoryBuffer buffer) {
      int numElements = buffer.readVarUint32Small7();
      boolean isFinal = (numElements & 0b1) != 0;
      numElements >>>= 1;
      RefResolver refResolver = fory.getRefResolver();
      Object[] value = new Object[numElements];
      refResolver.reference(value);

      if (isFinal) {
        for (int i = 0; i < numElements; i++) {
          Object elem;
          int nextReadRefId = refResolver.tryPreserveRefId(buffer);
          if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
            elem = readInnerElement(buffer);
            refResolver.setReadObject(nextReadRefId, elem);
          } else {
            elem = refResolver.getReadObject();
          }
          value[i] = elem;
        }
      } else {
        for (int i = 0; i < numElements; i++) {
          value[i] = fory.readRef(buffer);
        }
      }
      return value;
    }

    private Object[][] read2DArray(MemoryBuffer buffer) {
      int numElements = buffer.readVarUint32Small7();
      boolean isFinal = (numElements & 0b1) != 0;
      numElements >>>= 1;
      RefResolver refResolver = fory.getRefResolver();
      Object[][] value = new Object[numElements][];
      refResolver.reference(value);
      if (isFinal) {
        for (int i = 0; i < numElements; i++) {
          Object[] elem;
          int nextReadRefId = refResolver.tryPreserveRefId(buffer);
          if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
            elem = read1DArray(buffer);
            refResolver.setReadObject(nextReadRefId, elem);
          } else {
            elem = (Object[]) refResolver.getReadObject();
          }
          value[i] = elem;
        }
      } else {
        for (int i = 0; i < numElements; i++) {
          value[i] = (Object[]) fory.readRef(buffer);
        }
      }
      return value;
    }

    private Object[] read3DArray(MemoryBuffer buffer) {
      int numElements = buffer.readVarUint32Small7();
      boolean isFinal = (numElements & 0b1) != 0;
      numElements >>>= 1;
      RefResolver refResolver = fory.getRefResolver();
      Object[][][] value = new Object[numElements][][];
      refResolver.reference(value);
      if (isFinal) {
        for (int i = 0; i < numElements; i++) {
          Object[][] elem;
          int nextReadRefId = refResolver.tryPreserveRefId(buffer);
          if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
            elem = read2DArray(buffer);
            refResolver.setReadObject(nextReadRefId, elem);
          } else {
            elem = (Object[][]) refResolver.getReadObject();
          }
          value[i] = elem;
        }
      } else {
        for (int i = 0; i < numElements; i++) {
          value[i] = (Object[][]) fory.readRef(buffer);
        }
      }
      return value;
    }
  }

  @SuppressWarnings("rawtypes")
  public static final class NonexistentArrayClassSerializer
      extends AbstractedNonexistentArrayClassSerializer {
    private final Serializer componentSerializer;

    public NonexistentArrayClassSerializer(Fory fory, Class<?> cls) {
      this(fory, "Unknown", cls);
    }

    public NonexistentArrayClassSerializer(Fory fory, String className, Class<?> cls) {
      super(fory, className, cls);
      if (TypeUtils.getArrayComponent(cls).isEnum()) {
        componentSerializer = new NonexistentClassSerializers.NonexistentEnumClassSerializer(fory);
      } else {
        if (fory.getConfig().getCompatibleMode() == CompatibleMode.COMPATIBLE) {
          componentSerializer =
              new CompatibleSerializer<>(fory, NonexistentClass.NonexistentSkip.class);
        } else {
          componentSerializer = null;
        }
      }
    }

    @Override
    protected Object readInnerElement(MemoryBuffer buffer) {
      if (componentSerializer == null) {
        throw new IllegalStateException(
            String.format("Class %s should serialize elements as non-morphic", className));
      }
      return componentSerializer.read(buffer);
    }
  }
}
