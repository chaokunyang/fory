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

package org.apache.fory.serializer.converter;

import java.lang.reflect.Field;
import java.util.Set;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.Collections;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.type.TypeUtils;

/**
 * Factory class for creating field converters that handle type conversions between different data
 * types. This class provides converters for primitive types and their boxed counterparts, enabling
 * automatic type conversion during serialization/deserialization processes.
 */
public class FieldConverters {
  private static Set<Class<?>> compatibleTypes(Class<?>... types) {
    return java.util.Collections.unmodifiableSet(Collections.ofHashSet(types));
  }

  /**
   * Creates an appropriate field converter based on the target field type and source object type.
   *
   * @param from the source object type to convert from
   * @param field the target field to convert to
   * @return a FieldConverter instance that can handle the conversion, or null if no compatible
   *     converter exists
   */
  public static FieldConverter<?> getConverter(Class<?> from, Field field) {
    FieldConversion conversion = fieldConversion(TypeUtils.wrap(from), field.getType());
    if (conversion == null) {
      return null;
    }
    FieldAccessor accessor = FieldAccessor.createAccessor(field);
    switch (conversion) {
      case INT:
        return new IntConverter(accessor);
      case BOXED_INT:
        return new BoxedIntConverter(accessor);
      case BOOLEAN:
        return new BooleanConverter(accessor);
      case BOXED_BOOLEAN:
        return new BoxedBooleanConverter(accessor);
      case BYTE:
        return new ByteConverter(accessor);
      case BOXED_BYTE:
        return new BoxedByteConverter(accessor);
      case SHORT:
        return new ShortConverter(accessor);
      case BOXED_SHORT:
        return new BoxedShortConverter(accessor);
      case LONG:
        return new LongConverter(accessor);
      case BOXED_LONG:
        return new BoxedLongConverter(accessor);
      case FLOAT:
        return new FloatConverter(accessor);
      case BOXED_FLOAT:
        return new BoxedFloatConverter(accessor);
      case DOUBLE:
        return new DoubleConverter(accessor);
      case BOXED_DOUBLE:
        return new BoxedDoubleConverter(accessor);
      case STRING:
        return new StringConverter(accessor);
      default:
        throw new IllegalStateException("Unknown field conversion " + conversion);
    }
  }

  /** Returns whether a value of {@code from} can be assigned or converted to {@code to}. */
  @Internal
  public static boolean canConvert(Class<?> from, Class<?> to) {
    if (isDirectlyAssignable(from, to)) {
      return true;
    }
    return fieldConversion(TypeUtils.wrap(from), to) != null;
  }

  /**
   * Converts {@code value} from {@code from} to {@code to}, or returns it for direct assignment.
   */
  @Internal
  public static Object convertValue(Class<?> from, Class<?> to, Object value) {
    if (isDirectlyAssignable(from, to)) {
      return value;
    }
    FieldConversion conversion = fieldConversion(TypeUtils.wrap(from), to);
    if (conversion != null) {
      return conversion.convert(value);
    }
    throw new UnsupportedOperationException("Incompatible type: " + from + " -> " + to);
  }

  private static FieldConversion fieldConversion(Class<?> wrappedFrom, Class<?> to) {
    if (to == int.class && IntConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.INT;
    } else if (to == Integer.class && IntConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOXED_INT;
    } else if (to == boolean.class && BooleanConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOOLEAN;
    } else if (to == Boolean.class && BooleanConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOXED_BOOLEAN;
    } else if (to == byte.class && ByteConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BYTE;
    } else if (to == Byte.class && ByteConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOXED_BYTE;
    } else if (to == short.class && ShortConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.SHORT;
    } else if (to == Short.class && ShortConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOXED_SHORT;
    } else if (to == long.class && LongConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.LONG;
    } else if (to == Long.class && LongConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOXED_LONG;
    } else if (to == float.class && FloatConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.FLOAT;
    } else if (to == Float.class && FloatConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOXED_FLOAT;
    } else if (to == double.class && DoubleConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.DOUBLE;
    } else if (to == Double.class && DoubleConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.BOXED_DOUBLE;
    } else if (to == String.class && StringConverter.compatibleTypes.contains(wrappedFrom)) {
      return FieldConversion.STRING;
    }
    return null;
  }

  private enum FieldConversion {
    INT {
      @Override
      Object convert(Object value) {
        return IntConverter.convertFrom(value);
      }
    },
    BOXED_INT {
      @Override
      Object convert(Object value) {
        return BoxedIntConverter.convertFrom(value);
      }
    },
    BOOLEAN {
      @Override
      Object convert(Object value) {
        return BooleanConverter.convertFrom(value);
      }
    },
    BOXED_BOOLEAN {
      @Override
      Object convert(Object value) {
        return BoxedBooleanConverter.convertFrom(value);
      }
    },
    BYTE {
      @Override
      Object convert(Object value) {
        return ByteConverter.convertFrom(value);
      }
    },
    BOXED_BYTE {
      @Override
      Object convert(Object value) {
        return BoxedByteConverter.convertFrom(value);
      }
    },
    SHORT {
      @Override
      Object convert(Object value) {
        return ShortConverter.convertFrom(value);
      }
    },
    BOXED_SHORT {
      @Override
      Object convert(Object value) {
        return BoxedShortConverter.convertFrom(value);
      }
    },
    LONG {
      @Override
      Object convert(Object value) {
        return LongConverter.convertFrom(value);
      }
    },
    BOXED_LONG {
      @Override
      Object convert(Object value) {
        return BoxedLongConverter.convertFrom(value);
      }
    },
    FLOAT {
      @Override
      Object convert(Object value) {
        return FloatConverter.convertFrom(value);
      }
    },
    BOXED_FLOAT {
      @Override
      Object convert(Object value) {
        return BoxedFloatConverter.convertFrom(value);
      }
    },
    DOUBLE {
      @Override
      Object convert(Object value) {
        return DoubleConverter.convertFrom(value);
      }
    },
    BOXED_DOUBLE {
      @Override
      Object convert(Object value) {
        return BoxedDoubleConverter.convertFrom(value);
      }
    },
    STRING {
      @Override
      Object convert(Object value) {
        return StringConverter.convertFrom(value);
      }
    };

    abstract Object convert(Object value);
  }

  private static boolean isDirectlyAssignable(Class<?> from, Class<?> to) {
    if (to.isAssignableFrom(from)) {
      return true;
    }
    if (from.isPrimitive() && !to.isPrimitive()) {
      return to.isAssignableFrom(TypeUtils.wrap(from));
    }
    return false;
  }

  /**
   * Converter for primitive boolean fields. Converts compatible types to boolean values. Returns
   * false for null values and incompatible types.
   */
  public static class BooleanConverter extends FieldConverter<Boolean> {
    static Set<Class<?>> compatibleTypes = compatibleTypes(String.class, Boolean.class);

    protected BooleanConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a boolean value.
     *
     * @param from the object to convert
     * @return the converted boolean value
     */
    public static Boolean convertFrom(Object from) {
      if (from == null) {
        return false;
      }
      if (from instanceof Boolean) {
        return (Boolean) from;
      } else if (from instanceof String) {
        return Boolean.parseBoolean((String) from);
      } else {
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public Boolean convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for boxed Boolean fields. Converts compatible types to Boolean values. Returns null
   * for null values, unlike the primitive version.
   */
  public static class BoxedBooleanConverter extends FieldConverter<Boolean> {
    protected BoxedBooleanConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a Boolean value.
     *
     * @param from the object to convert
     * @return the converted Boolean value, or null if from is null
     */
    public static Boolean convertFrom(Object from) {
      if (from == null) {
        return null;
      }
      return BooleanConverter.convertFrom(from);
    }

    @Override
    public Boolean convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for primitive byte fields. Converts compatible types to byte values. Returns 0 for
   * null values.
   */
  public static class ByteConverter extends FieldConverter<Byte> {
    static Set<Class<?>> compatibleTypes =
        compatibleTypes(String.class, Integer.class, Long.class, Short.class, Byte.class);

    protected ByteConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a byte value.
     *
     * @param from the object to convert
     * @return the converted byte value
     * @throws NumberFormatException if the string cannot be parsed as a byte
     * @throws ArithmeticException if the numeric value is out of byte range
     */
    public static Byte convertFrom(Object from) {
      if (from == null) {
        return 0;
      }
      if (from instanceof Byte) {
        return (Byte) from;
      } else if (from instanceof Integer) {
        return ((Integer) from).byteValue();
      } else if (from instanceof Long) {
        return ((Long) from).byteValue();
      } else if (from instanceof Short) {
        return ((Short) from).byteValue();
      } else if (from instanceof String) {
        return Byte.parseByte((String) from);
      } else {
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public Byte convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for boxed Byte fields. Converts compatible types to Byte values. Returns null for
   * null values, unlike the primitive version.
   */
  public static class BoxedByteConverter extends FieldConverter<Byte> {
    protected BoxedByteConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a Byte value.
     *
     * @param from the object to convert
     * @return the converted Byte value, or null if from is null
     */
    public static Byte convertFrom(Object from) {
      if (from == null) {
        return null;
      }
      return ByteConverter.convertFrom(from);
    }

    @Override
    public Byte convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for primitive short fields. Converts compatible types to short values. Returns 0 for
   * null values.
   */
  public static class ShortConverter extends FieldConverter<Short> {
    static Set<Class<?>> compatibleTypes =
        compatibleTypes(String.class, Integer.class, Long.class, Short.class);

    protected ShortConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a short value.
     *
     * @param from the object to convert
     * @return the converted short value
     * @throws NumberFormatException if the string cannot be parsed as a short
     * @throws ArithmeticException if the numeric value is out of short range
     */
    public static Short convertFrom(Object from) {
      if (from == null) {
        return 0;
      }
      if (from instanceof Short) {
        return (Short) from;
      } else if (from instanceof Integer) {
        return ((Integer) from).shortValue();
      } else if (from instanceof Long) {
        return ((Long) from).shortValue();
      } else if (from instanceof String) {
        return Short.parseShort((String) from);
      } else {
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public Short convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for boxed Short fields. Converts compatible types to Short values. Returns null for
   * null values, unlike the primitive version.
   */
  public static class BoxedShortConverter extends FieldConverter<Short> {
    protected BoxedShortConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    public static Short convertFrom(Object from) {
      if (from == null) {
        return null;
      }
      return ShortConverter.convertFrom(from);
    }

    @Override
    public Short convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for primitive int fields. Converts compatible types to int values. Returns 0 for null
   * values.
   */
  public static class IntConverter extends FieldConverter<Integer> {
    static Set<Class<?>> compatibleTypes = compatibleTypes(String.class, Long.class, Integer.class);

    protected IntConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to an int value.
     *
     * @param from the object to convert
     * @return the converted int value
     * @throws NumberFormatException if the string cannot be parsed as an int
     * @throws ArithmeticException if the numeric value is out of int range
     */
    public static Integer convertFrom(Object from) {
      if (from == null) {
        return 0;
      }
      if (from instanceof Long) {
        return Math.toIntExact((Long) from);
      } else if (from instanceof Integer) {
        return (Integer) from;
      } else if (from instanceof String) {
        return Integer.parseInt((String) from);
      } else {
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public Integer convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for boxed Integer fields. Converts compatible types to Integer values. Returns null
   * for null values, unlike the primitive version.
   */
  public static class BoxedIntConverter extends FieldConverter<Integer> {
    protected BoxedIntConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to an Integer value.
     *
     * @param from the object to convert
     * @return the converted Integer value, or null if from is null
     */
    public static Integer convertFrom(Object from) {
      if (from == null) {
        return null;
      }
      return IntConverter.convertFrom(from);
    }

    @Override
    public Integer convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for primitive long fields. Converts compatible types to long values. Returns 0 for
   * null values.
   */
  public static class LongConverter extends FieldConverter<Long> {
    static Set<Class<?>> compatibleTypes = compatibleTypes(String.class, Long.class);

    protected LongConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a long value.
     *
     * @param from the object to convert
     * @return the converted long value
     * @throws NumberFormatException if the string cannot be parsed as a long
     */
    public static Long convertFrom(Object from) {
      if (from == null) {
        return 0L;
      }
      if (from instanceof Long) {
        return (Long) from;
      } else if (from instanceof String) {
        return Long.parseLong((String) from);
      } else {
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public Long convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for boxed Long fields. Converts compatible types to Long values. Returns null for
   * null values, unlike the primitive version.
   */
  public static class BoxedLongConverter extends FieldConverter<Long> {
    protected BoxedLongConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a Long value.
     *
     * @param from the object to convert
     * @return the converted Long value, or null if from is null
     */
    public static Long convertFrom(Object from) {
      if (from == null) {
        return null;
      }
      return LongConverter.convertFrom(from);
    }

    @Override
    public Long convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for primitive float fields. Converts compatible types to float values. Returns 0.0f
   * for null values. Only allows conversion from String.
   */
  public static class FloatConverter extends FieldConverter<Float> {
    static Set<Class<?>> compatibleTypes = compatibleTypes(String.class, Float.class);

    protected FloatConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a float value.
     *
     * @param from the object to convert
     * @return the converted float value
     * @throws NumberFormatException if the string cannot be parsed as a float
     */
    public static Float convertFrom(Object from) {
      if (from == null) {
        return 0.0f;
      }
      if (from instanceof String) {
        return Float.parseFloat((String) from);
      } else if (from instanceof Float) {
        return (Float) from;
      } else {
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public Float convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for boxed Float fields. Converts compatible types to Float values. Returns null for
   * null values, unlike the primitive version. Only allows conversion from String.
   */
  public static class BoxedFloatConverter extends FieldConverter<Float> {
    protected BoxedFloatConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a Float value.
     *
     * @param from the object to convert
     * @return the converted Float value, or null if from is null
     */
    public static Float convertFrom(Object from) {
      if (from == null) {
        return null;
      }
      return FloatConverter.convertFrom(from);
    }

    @Override
    public Float convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for primitive double fields. Converts compatible types to double values. Returns 0.0
   * for null values. Allows conversion from String and Float.
   */
  public static class DoubleConverter extends FieldConverter<Double> {
    static Set<Class<?>> compatibleTypes = compatibleTypes(String.class, Float.class, Double.class);

    protected DoubleConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a double value.
     *
     * @param from the object to convert
     * @return the converted double value
     * @throws NumberFormatException if the string cannot be parsed as a double
     */
    public static Double convertFrom(Object from) {
      if (from == null) {
        return 0.0;
      }
      if (from instanceof String) {
        return Double.parseDouble((String) from);
      } else if (from instanceof Double) {
        return (Double) from;
      } else if (from instanceof Float) {
        return ((Float) from).doubleValue();
      } else {
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public Double convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for boxed Double fields. Converts compatible types to Double values. Returns null for
   * null values, unlike the primitive version. Allows conversion from String and Float.
   */
  public static class BoxedDoubleConverter extends FieldConverter<Double> {
    protected BoxedDoubleConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a Double value.
     *
     * @param from the object to convert
     * @return the converted Double value, or null if from is null
     */
    public static Double convertFrom(Object from) {
      if (from == null) {
        return null;
      }
      return DoubleConverter.convertFrom(from);
    }

    @Override
    public Double convert(Object from) {
      return convertFrom(from);
    }
  }

  /**
   * Converter for String fields. Converts compatible types to String values. Only allows conversion
   * from Number types to prevent malicious toString() calls.
   */
  public static class StringConverter extends FieldConverter<String> {
    static Set<Class<?>> compatibleTypes =
        compatibleTypes(
            Integer.class,
            Long.class,
            Short.class,
            Byte.class,
            Boolean.class,
            Float.class,
            Double.class);

    protected StringConverter(FieldAccessor fieldAccessor) {
      super(fieldAccessor);
    }

    /**
     * Converts an object to a String value.
     *
     * @param from the object to convert
     * @return the converted String value, or null if from is null
     */
    public static String convertFrom(Object from) {
      if (from == null) {
        return null;
      } else if (from instanceof Number || from instanceof Boolean) {
        return from.toString();
      } else {
        // disallow on other types, to avoid malicious toString get called.
        throw new UnsupportedOperationException("Incompatible type: " + from.getClass());
      }
    }

    @Override
    public String convert(Object from) {
      return convertFrom(from);
    }
  }
}
