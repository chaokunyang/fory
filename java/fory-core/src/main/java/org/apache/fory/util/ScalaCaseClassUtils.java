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

package org.apache.fory.util;

import com.google.common.cache.Cache;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fory.Fory;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.Collections;
import org.apache.fory.memory.Platform;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.util.unsafe._JDKAccess;

/**
 * Utility class for detecting Scala case classes and their default value methods.
 *
 * <p>Scala case classes with default parameters generate companion objects with methods like
 * `apply$default$1`, `apply$default$2`, etc. that return the default values.
 */
@Internal
public class ScalaCaseClassUtils {

  private static final Cache<Class<?>, Boolean> isScalaCaseClassCache =
      Collections.newClassKeySoftCache(32);
  private static final Cache<Class<?>, Map<Integer, MethodHandle>> defaultValueMethodCache =
      Collections.newClassKeySoftCache(32);

  /** Field info for Scala case class fields with default values. */
  public static final class ScalaDefaultValueField {
    private final Object defaultValue;
    private final String fieldName;
    private final FieldAccessor fieldAccessor;
    private final short classId;

    private ScalaDefaultValueField(
        String fieldName, Object defaultValue, FieldAccessor fieldAccessor, short classId) {
      this.fieldName = fieldName;
      this.defaultValue = defaultValue;
      this.fieldAccessor = fieldAccessor;
      this.classId = classId;
    }

    public Object getDefaultValue() {
      return defaultValue;
    }

    public String getFieldName() {
      return fieldName;
    }

    public FieldAccessor getFieldAccessor() {
      return fieldAccessor;
    }

    public short getClassId() {
      return classId;
    }
  }

  /**
   * Builds Scala default value fields for the given class. Only includes fields that are not
   * present in the serialized data.
   *
   * @param fory the Fory instance
   * @param type the class type
   * @param descriptors list of descriptors that are present in the serialized data
   * @return array of ScalaDefaultValueField objects
   */
  public static ScalaDefaultValueField[] buildScalaDefaultValueFields(
      Fory fory, Class<?> type, java.util.List<org.apache.fory.type.Descriptor> descriptors) {
    if (!isScalaCaseClass(type)) {
      return new ScalaDefaultValueField[0];
    }

    try {
      // Extract fields from descriptors
      java.util.Set<java.lang.reflect.Field> serializedFields = new java.util.HashSet<>();
      for (org.apache.fory.type.Descriptor descriptor : descriptors) {
        java.lang.reflect.Field field = descriptor.getField();
        if (field != null) {
          serializedFields.add(field);
        }
      }

      java.lang.reflect.Field[] allFields = type.getDeclaredFields();
      List<ScalaDefaultValueField> defaultFields = new ArrayList<>();

      for (java.lang.reflect.Field field : allFields) {
        // Only include fields that are not in the serialized data
        if (!serializedFields.contains(field)) {
          String fieldName = field.getName();
          Object defaultValue = getDefaultValueForField(type, fieldName);
          if (defaultValue != null) {
            FieldAccessor fieldAccessor = FieldAccessor.createAccessor(field);
            Short classId = fory.getClassResolver().getRegisteredClassId(field.getType());
            defaultFields.add(
                new ScalaDefaultValueField(
                    fieldName,
                    defaultValue,
                    fieldAccessor,
                    classId != null ? classId : ClassResolver.NO_CLASS_ID));
          }
        }
      }

      return defaultFields.toArray(new ScalaDefaultValueField[0]);
    } catch (Exception e) {
      // Ignore exceptions and return empty array
      return new ScalaDefaultValueField[0];
    }
  }

  /**
   * Sets default values for missing fields in a Scala case class.
   *
   * @param obj the object to set default values on
   * @param scalaDefaultValueFields the cached default value fields
   */
  public static void setScalaDefaultValues(
      Object obj, ScalaDefaultValueField[] scalaDefaultValueFields) {
    for (ScalaDefaultValueField defaultField : scalaDefaultValueFields) {
      FieldAccessor fieldAccessor = defaultField.getFieldAccessor();
      if (fieldAccessor != null) {
        Object defaultValue = defaultField.getDefaultValue();
        short classId = defaultField.getClassId();
        long fieldOffset = fieldAccessor.getFieldOffset();
        switch (classId) {
          case ClassResolver.PRIMITIVE_BOOLEAN_CLASS_ID:
          case ClassResolver.BOOLEAN_CLASS_ID:
            Platform.putBoolean(obj, fieldOffset, (Boolean) defaultValue);
            break;
          case ClassResolver.PRIMITIVE_BYTE_CLASS_ID:
          case ClassResolver.BYTE_CLASS_ID:
            Platform.putByte(obj, fieldOffset, (Byte) defaultValue);
            break;
          case ClassResolver.PRIMITIVE_CHAR_CLASS_ID:
          case ClassResolver.CHAR_CLASS_ID:
            Platform.putChar(obj, fieldOffset, (Character) defaultValue);
            break;
          case ClassResolver.PRIMITIVE_SHORT_CLASS_ID:
          case ClassResolver.SHORT_CLASS_ID:
            Platform.putShort(obj, fieldOffset, (Short) defaultValue);
            break;
          case ClassResolver.PRIMITIVE_INT_CLASS_ID:
          case ClassResolver.INTEGER_CLASS_ID:
            Platform.putInt(obj, fieldOffset, (Integer) defaultValue);
            break;
          case ClassResolver.PRIMITIVE_LONG_CLASS_ID:
          case ClassResolver.LONG_CLASS_ID:
            Platform.putLong(obj, fieldOffset, (Long) defaultValue);
            break;
          case ClassResolver.PRIMITIVE_FLOAT_CLASS_ID:
          case ClassResolver.FLOAT_CLASS_ID:
            Platform.putFloat(obj, fieldOffset, (Float) defaultValue);
            break;
          case ClassResolver.PRIMITIVE_DOUBLE_CLASS_ID:
          case ClassResolver.DOUBLE_CLASS_ID:
            Platform.putDouble(obj, fieldOffset, (Double) defaultValue);
            break;
          default:
            // Object type
            fieldAccessor.putObject(obj, defaultValue);
        }
      }
    }
  }

  /**
   * Checks if a class is a Scala case class.
   *
   * @param cls the class to check
   * @return true if the class is a Scala case class, false otherwise
   */
  public static boolean isScalaCaseClass(Class<?> cls) {
    Boolean isScalaCaseClass = isScalaCaseClassCache.getIfPresent(cls);
    if (isScalaCaseClass == null) {
      isScalaCaseClass = checkIsScalaCaseClass(cls);
      isScalaCaseClassCache.put(cls, isScalaCaseClass);
    }
    return isScalaCaseClass;
  }

  /**
   * Gets the default value method handle for a specific parameter index in a Scala case class.
   *
   * @param cls the Scala case class
   * @param paramIndex the parameter index (1-based, as Scala uses 1-based indexing)
   * @return the method handle for the default value method, or null if not found
   */
  public static MethodHandle getDefaultValueMethod(Class<?> cls, int paramIndex) {
    if (!isScalaCaseClass(cls)) {
      return null;
    }

    Map<Integer, MethodHandle> methods = defaultValueMethodCache.getIfPresent(cls);
    if (methods == null) {
      methods = findDefaultValueMethods(cls);
      defaultValueMethodCache.put(cls, methods);
    }

    return methods.get(paramIndex);
  }

  /**
   * Gets the default value for a specific parameter in a Scala case class.
   *
   * @param cls the Scala case class
   * @param paramIndex the parameter index (1-based)
   * @return the default value, or null if not found
   */
  public static Object getDefaultValue(Class<?> cls, int paramIndex) {
    MethodHandle methodHandle = getDefaultValueMethod(cls, paramIndex);
    if (methodHandle == null) {
      return null;
    }

    try {
      return methodHandle.invoke();
    } catch (Throwable e) {
      Platform.throwException(e);
      return null;
    }
  }

  /**
   * Gets the default value for a specific field name in a Scala case class. This method attempts to
   * map field names to parameter indices.
   *
   * @param cls the Scala case class
   * @param fieldName the field name
   * @return the default value, or null if not found
   */
  public static Object getDefaultValueForField(Class<?> cls, String fieldName) {
    if (!isScalaCaseClass(cls)) {
      return null;
    }

    // Try to find the parameter index by looking at the constructor parameters
    try {
      String companionClassName = cls.getName() + "$";
      Class<?> companionClass = Class.forName(companionClassName, false, cls.getClassLoader());

      // Find the apply method with the most parameters (the main constructor)
      Method[] methods = companionClass.getDeclaredMethods();
      Method applyMethod = null;
      int maxParams = 0;

      for (Method method : methods) {
        if ("apply".equals(method.getName()) && method.getParameterCount() > maxParams) {
          applyMethod = method;
          maxParams = method.getParameterCount();
        }
      }

      if (applyMethod != null) {
        // Try to match field name with parameter names
        // This is a simplified approach - in practice, we might need more sophisticated matching
        for (int i = 0; i < applyMethod.getParameterCount(); i++) {
          // Try to get parameter name (this might not work in all cases due to compilation
          // settings)
          String paramName = getParameterName(applyMethod, i);
          if (fieldName.equals(paramName)) {
            // Found matching parameter, get its default value
            return getDefaultValue(cls, i + 1); // Scala uses 1-based indexing
          }
        }
      }
    } catch (Exception e) {
      // If anything goes wrong, return null
    }

    return null;
  }

  /**
   * Attempts to get the parameter name for a method parameter. This may not work if parameter names
   * are not preserved during compilation.
   */
  private static String getParameterName(Method method, int paramIndex) {
    try {
      // Try to get parameter names using reflection
      // Note: This requires -parameters compiler flag to work
      java.lang.reflect.Parameter[] parameters = method.getParameters();
      if (paramIndex < parameters.length && parameters[paramIndex].isNamePresent()) {
        return parameters[paramIndex].getName();
      }
    } catch (Exception e) {
      // Ignore exceptions
    }

    // Fallback: try to infer from field names
    try {
      java.lang.reflect.Field[] fields =
          method.getDeclaringClass().getEnclosingClass().getDeclaredFields();
      if (paramIndex < fields.length) {
        return fields[paramIndex].getName();
      }
    } catch (Exception e) {
      // Ignore exceptions
    }

    return null;
  }

  /**
   * Checks if a class is a Scala case class by looking for the companion object and checking for
   * the presence of `apply` method.
   */
  private static boolean checkIsScalaCaseClass(Class<?> cls) {
    try {
      // Scala case classes have a companion object with the same name + "$"
      String companionClassName = cls.getName() + "$";
      Class<?> companionClass = Class.forName(companionClassName, false, cls.getClassLoader());

      // Check if the companion class has an `apply` method
      Method[] methods = companionClass.getDeclaredMethods();
      for (Method method : methods) {
        if ("apply".equals(method.getName())) {
          return true;
        }
      }
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      // Not a Scala case class if companion object doesn't exist
    }

    return false;
  }

  /**
   * Finds all default value methods for a Scala case class.
   *
   * @param cls the Scala case class
   * @return a map from parameter index to method handle
   */
  private static Map<Integer, MethodHandle> findDefaultValueMethods(Class<?> cls) {
    Map<Integer, MethodHandle> methods = new HashMap<>();

    try {
      String companionClassName = cls.getName() + "$";
      Class<?> companionClass = Class.forName(companionClassName, false, cls.getClassLoader());

      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(companionClass);

      // Look for methods named `apply$default$1`, `apply$default$2`, etc.
      Method[] companionMethods = companionClass.getDeclaredMethods();
      for (Method method : companionMethods) {
        String methodName = method.getName();
        if (methodName.startsWith("apply$default$")) {
          try {
            // Extract the parameter index from the method name
            String indexStr = methodName.substring("apply$default$".length());
            int paramIndex = Integer.parseInt(indexStr);

            // Create method handle for the default value method
            MethodHandle methodHandle = lookup.unreflect(method);
            methods.put(paramIndex, methodHandle);
          } catch (NumberFormatException e) {
            // Skip if we can't parse the parameter index
          }
        }
      }
    } catch (Exception e) {
      // If anything goes wrong, return empty map
    }

    return methods;
  }
}
