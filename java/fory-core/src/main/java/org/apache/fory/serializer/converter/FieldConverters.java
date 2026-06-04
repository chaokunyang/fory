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
import org.apache.fory.annotation.Internal;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DispatchId;
import org.apache.fory.type.TypeUtils;

/** Factory for cold compatible-field scalar converters. */
public class FieldConverters {
  /**
   * Creates an appropriate field converter based on the target field type and source object type.
   *
   * @param from the source object type to convert from
   * @param field the target field to convert to
   * @return a FieldConverter instance that can handle the conversion, or null if no compatible
   *     converter exists
   */
  public static FieldConverter<?> getConverter(Class<?> from, Field field) {
    int fromDispatchId = CompatibleScalarConverter.dispatchId(TypeUtils.wrap(from));
    Class<?> to = field.getType();
    int toDispatchId = CompatibleScalarConverter.dispatchId(TypeUtils.wrap(to));
    if (!needsConverter(fromDispatchId, TypeUtils.wrap(from), toDispatchId, to)) {
      return null;
    }
    return new ScalarFieldConverter(
        FieldAccessor.createAccessor(field),
        fromDispatchId,
        TypeUtils.wrap(from),
        toDispatchId,
        to,
        field.getDeclaringClass().getName() + "." + field.getName());
  }

  /**
   * Creates a descriptor-aware converter for compatible field reads.
   *
   * <p>Descriptor dispatch IDs preserve scalar annotations such as uint widths and integer
   * encoding, so this path must be used when remote and local schema metadata are both available.
   */
  @Internal
  public static FieldConverter<?> getConverter(
      TypeResolver resolver, Descriptor from, Descriptor to) {
    Field field = to.getField();
    int fromDispatchId = DispatchId.getDispatchId(resolver, from);
    int toDispatchId = DispatchId.getDispatchId(resolver, to);
    if (field == null
        || from.isTrackingRef()
        || to.isTrackingRef()
        || !needsConverter(fromDispatchId, from.getRawType(), toDispatchId, to.getRawType())) {
      return null;
    }
    return new ScalarFieldConverter(
        FieldAccessor.createAccessor(field),
        fromDispatchId,
        from.getRawType(),
        toDispatchId,
        to.getRawType(),
        to.getDeclaringClass() + "." + to.getName());
  }

  /** Returns whether a value of {@code from} can be assigned or converted to {@code to}. */
  @Internal
  public static boolean canConvert(Class<?> from, Class<?> to) {
    if (isDirectlyAssignable(from, to)) {
      return true;
    }
    Class<?> wrappedFrom = TypeUtils.wrap(from);
    Class<?> wrappedTo = TypeUtils.wrap(to);
    return CompatibleScalarConverter.canConvert(
        CompatibleScalarConverter.dispatchId(wrappedFrom),
        wrappedFrom,
        CompatibleScalarConverter.dispatchId(wrappedTo),
        wrappedTo);
  }

  /**
   * Returns whether descriptor-level compatible read can assign or convert {@code from} to {@code
   * to}.
   */
  @Internal
  public static boolean canConvert(TypeResolver resolver, Descriptor from, Descriptor to) {
    int fromDispatchId = DispatchId.getDispatchId(resolver, from);
    int toDispatchId = DispatchId.getDispatchId(resolver, to);
    if (isDirectIdentity(
        fromDispatchId,
        from.getRawType(),
        from.isTrackingRef(),
        toDispatchId,
        to.getRawType(),
        to.isTrackingRef())) {
      return true;
    }
    if (from.isTrackingRef() || to.isTrackingRef()) {
      return false;
    }
    return CompatibleScalarConverter.canConvert(
        fromDispatchId, from.getRawType(), toDispatchId, to.getRawType());
  }

  /**
   * Returns whether descriptor-level compatible read can assign or convert {@code from} to {@code
   * to}.
   */
  @Internal
  public static boolean canConvert(SerializationFieldInfo from, SerializationFieldInfo to) {
    if (isDirectIdentity(
        from.dispatchId, from.type, from.trackingRef, to.dispatchId, to.type, to.trackingRef)) {
      return true;
    }
    if (from.trackingRef || to.trackingRef) {
      return false;
    }
    return CompatibleScalarConverter.canConvert(from.dispatchId, from.type, to.dispatchId, to.type);
  }

  /**
   * Converts {@code value} from {@code from} to {@code to}, or returns it for direct assignment.
   */
  @Internal
  public static Object convertValue(Class<?> from, Class<?> to, Object value) {
    if (isDirectlyAssignable(from, to)) {
      return value;
    }
    Class<?> wrappedFrom = TypeUtils.wrap(from);
    Class<?> wrappedTo = TypeUtils.wrap(to);
    return CompatibleScalarConverter.convert(
        CompatibleScalarConverter.dispatchId(wrappedFrom),
        wrappedFrom,
        CompatibleScalarConverter.dispatchId(wrappedTo),
        wrappedTo,
        value,
        "<unknown>");
  }

  /** Converts a compatible field value using descriptor-level scalar metadata. */
  @Internal
  public static Object convertValue(
      SerializationFieldInfo from, SerializationFieldInfo to, Object value) {
    if (isDirectIdentity(
        from.dispatchId, from.type, from.trackingRef, to.dispatchId, to.type, to.trackingRef)) {
      return value;
    }
    if (from.trackingRef || to.trackingRef) {
      throw new IllegalArgumentException(
          "Reference-tracked scalar conversion is schema incompatible for "
              + to.qualifiedFieldName);
    }
    return CompatibleScalarConverter.convert(
        from.dispatchId, from.type, to.dispatchId, to.type, value, to.qualifiedFieldName);
  }

  /** Converts a compatible field value using scalar metadata captured at code generation time. */
  @Internal
  public static Object convertValue(
      int fromDispatchId,
      Class<?> fromType,
      int toDispatchId,
      Class<?> toType,
      String fieldName,
      Object value) {
    return CompatibleScalarConverter.convert(
        fromDispatchId, fromType, toDispatchId, toType, value, fieldName);
  }

  @Internal
  public static int fromDispatchId(FieldConverter<?> converter) {
    return scalarConverter(converter).fromDispatchId;
  }

  @Internal
  public static Class<?> fromType(FieldConverter<?> converter) {
    return scalarConverter(converter).fromType;
  }

  @Internal
  public static int toDispatchId(FieldConverter<?> converter) {
    return scalarConverter(converter).toDispatchId;
  }

  @Internal
  public static Class<?> toType(FieldConverter<?> converter) {
    return scalarConverter(converter).toType;
  }

  @Internal
  public static String fieldName(FieldConverter<?> converter) {
    return scalarConverter(converter).fieldName;
  }

  private static boolean needsConverter(
      int fromDispatchId, Class<?> from, int toDispatchId, Class<?> to) {
    if (isDirectIdentity(fromDispatchId, from, toDispatchId, to)) {
      return false;
    }
    return CompatibleScalarConverter.canConvert(fromDispatchId, from, toDispatchId, to);
  }

  private static boolean isDirectIdentity(
      int fromDispatchId,
      Class<?> from,
      boolean fromTrackingRef,
      int toDispatchId,
      Class<?> to,
      boolean toTrackingRef) {
    if (!isDirectlyAssignable(from, to)) {
      return false;
    }
    boolean fromScalar = CompatibleScalarConverter.isScalar(fromDispatchId, from);
    boolean toScalar = CompatibleScalarConverter.isScalar(toDispatchId, to);
    if (fromScalar && toScalar) {
      return fromTrackingRef == toTrackingRef
          && CompatibleScalarConverter.sameScalar(fromDispatchId, from, toDispatchId, to);
    }
    return true;
  }

  private static boolean isDirectIdentity(
      int fromDispatchId, Class<?> from, int toDispatchId, Class<?> to) {
    if (!isDirectlyAssignable(from, to)) {
      return false;
    }
    boolean fromScalar = CompatibleScalarConverter.isScalar(fromDispatchId, from);
    boolean toScalar = CompatibleScalarConverter.isScalar(toDispatchId, to);
    return !fromScalar
        || !toScalar
        || CompatibleScalarConverter.sameScalar(fromDispatchId, from, toDispatchId, to);
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

  private static ScalarFieldConverter scalarConverter(FieldConverter<?> converter) {
    if (!(converter instanceof ScalarFieldConverter)) {
      throw new IllegalArgumentException("Unsupported compatible field converter: " + converter);
    }
    return (ScalarFieldConverter) converter;
  }

  private static final class ScalarFieldConverter extends FieldConverter<Object> {
    private final int fromDispatchId;
    private final Class<?> fromType;
    private final int toDispatchId;
    private final Class<?> toType;
    private final String fieldName;

    private ScalarFieldConverter(
        FieldAccessor fieldAccessor,
        int fromDispatchId,
        Class<?> fromType,
        int toDispatchId,
        Class<?> toType,
        String fieldName) {
      super(fieldAccessor);
      this.fromDispatchId = fromDispatchId;
      this.fromType = fromType;
      this.toDispatchId = toDispatchId;
      this.toType = toType;
      this.fieldName = fieldName;
    }

    @Override
    public Object convert(Object from) {
      return CompatibleScalarConverter.convert(
          fromDispatchId, fromType, toDispatchId, toType, from, fieldName);
    }
  }
}
