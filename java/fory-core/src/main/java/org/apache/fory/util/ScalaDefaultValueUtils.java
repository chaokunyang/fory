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
 * Utility class for detecting Scala classes with default values and their default value methods.
 *
 * <p>Scala classes (including case classes) with default parameters generate companion objects with
 * methods like `apply$default$1`, `apply$default$2`, etc. that return the default values.
 */
@Internal
public class ScalaDefaultValueUtils {

  private static final Cache<Class<?>, Boolean> isScalaClassWithDefaultsCache =
      Collections.newClassKeySoftCache(32);
  private static final Cache<Class<?>, Map<Integer, MethodHandle>> defaultValueMethodCache =
      Collections.newClassKeySoftCache(32);
  private static final Cache<Class<?>, Map<Integer, Object>> allDefaultValuesCache =
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
    if (!isScalaClassWithDefaults(type)) {
      System.out.println(
          "DEBUG: " + type.getName() + " is not detected as a Scala class with defaults");
      return new ScalaDefaultValueField[0];
    }

    System.out.println("DEBUG: " + type.getName() + " is detected as a Scala class with defaults");

    try {
      // Extract field names from descriptors
      java.util.Set<String> serializedFieldNames = new java.util.HashSet<>();
      for (org.apache.fory.type.Descriptor descriptor : descriptors) {
        java.lang.reflect.Field field = descriptor.getField();
        if (field != null) {
          serializedFieldNames.add(field.getName());
        }
      }

      System.out.println("DEBUG: Serialized field names: " + serializedFieldNames);

      java.lang.reflect.Field[] allFields = type.getDeclaredFields();
      List<ScalaDefaultValueField> defaultFields = new ArrayList<>();

      for (java.lang.reflect.Field field : allFields) {
        // Only include fields that are not in the serialized data
        if (!serializedFieldNames.contains(field.getName())) {
          String fieldName = field.getName();
          System.out.println("DEBUG: Field " + fieldName + " is missing from serialized data");
          Object defaultValue = getDefaultValueForField(type, fieldName);
          System.out.println("DEBUG: Default value for " + fieldName + ": " + defaultValue);
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

      System.out.println("DEBUG: Found " + defaultFields.size() + " default value fields");
      return defaultFields.toArray(new ScalaDefaultValueField[0]);
    } catch (Exception e) {
      System.out.println("DEBUG: Exception in buildScalaDefaultValueFields: " + e.getMessage());
      e.printStackTrace();
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
    return isScalaClassWithDefaults(cls);
  }

  /**
   * Checks if a class is a Scala class with default values.
   *
   * @param cls the class to check
   * @return true if the class is a Scala class with default values, false otherwise
   */
  public static boolean isScalaClassWithDefaults(Class<?> cls) {
    Preconditions.checkNotNull(cls, "Class must not be null");
    Boolean isScalaClassWithDefaults = isScalaClassWithDefaultsCache.getIfPresent(cls);
    if (isScalaClassWithDefaults == null) {
      isScalaClassWithDefaults = checkIsScalaClassWithDefaults(cls);
      isScalaClassWithDefaultsCache.put(cls, isScalaClassWithDefaults);
    }
    return isScalaClassWithDefaults;
  }

  /**
   * Gets the default value method handle for a specific parameter index in a Scala class.
   *
   * @param cls the Scala class
   * @param paramIndex the parameter index (1-based, as Scala uses 1-based indexing)
   * @return the method handle for the default value method, or null if not found
   */
  public static MethodHandle getDefaultValueMethod(Class<?> cls, int paramIndex) {
    Preconditions.checkNotNull(cls, "Class must not be null");
    Preconditions.checkArgument(
        isScalaClassWithDefaults(cls),
        "Class is not a Scala class with defaults: " + cls.getName());

    Map<Integer, MethodHandle> methods = defaultValueMethodCache.getIfPresent(cls);
    if (methods == null) {
      methods = findDefaultValueMethods(cls);
      defaultValueMethodCache.put(cls, methods);
    }

    return methods.get(paramIndex);
  }

  /**
   * Gets the default value for a specific parameter in a Scala class.
   *
   * @param cls the Scala class
   * @param paramIndex the parameter index (1-based)
   * @return the default value, or null if not found
   */
  public static Object getDefaultValue(Class<?> cls, int paramIndex) {
    Preconditions.checkNotNull(cls, "Class must not be null");

    // Only proceed if it's a Scala class with defaults
    if (!isScalaClassWithDefaults(cls)) {
      return null;
    }

    // Get or create all default values for this class
    Map<Integer, Object> allDefaults = getAllDefaultValues(cls);
    return allDefaults.get(paramIndex);
  }

  /**
   * Gets all default values for a Scala class. This method caches all default values at the class
   * level for better performance.
   *
   * @param cls the Scala class
   * @return a map from parameter index to default value (null if no default)
   */
  public static Map<Integer, Object> getAllDefaultValues(Class<?> cls) {
    Preconditions.checkNotNull(cls, "Class must not be null");

    // Check cache first
    Map<Integer, Object> cached = allDefaultValuesCache.getIfPresent(cls);
    if (cached != null) {
      return cached;
    }

    Map<Integer, Object> allDefaults = new HashMap<>();

    // Check if this is a regular Scala class with static default methods
    boolean isRegularScalaClass = false;
    Method[] methods = cls.getDeclaredMethods();
    for (Method method : methods) {
      if (method.getName().startsWith("$lessinit$greater$default$")) {
        isRegularScalaClass = true;
        break;
      }
    }

    if (isRegularScalaClass) {
      // Use classic reflection for each possible parameter index
      try {
        java.lang.reflect.Constructor<?>[] constructors = cls.getDeclaredConstructors();
        if (constructors.length > 0) {
          java.lang.reflect.Constructor<?> constructor = constructors[0];
          int paramCount = constructor.getParameterCount();
          for (int i = 1; i <= paramCount; i++) {
            try {
              Method m = cls.getDeclaredMethod("$lessinit$greater$default$" + i);
              m.setAccessible(true);
              Object result = m.invoke(null);
              System.out.println(
                  "DEBUG: Reflection invoke (static) for param " + i + " returned: " + result);
              allDefaults.put(i, result);
            } catch (NoSuchMethodException nsme) {
              // No default for this parameter
            }
          }
        }
      } catch (Exception ex) {
        throw new RuntimeException(
            "Error getting default values for regular Scala class: " + cls.getName(), ex);
      }
      allDefaultValuesCache.put(cls, allDefaults);
      return allDefaults;
    }

    // Otherwise, use the method handle approach for case classes
    Map<Integer, MethodHandle> methodsMap = findDefaultValueMethods(cls);
    Object companionInstance = getCompanionObject(cls);
    for (Map.Entry<Integer, MethodHandle> entry : methodsMap.entrySet()) {
      int paramIndex = entry.getKey();
      MethodHandle methodHandle = entry.getValue();
      try {
        Object result = null;
        boolean needsFallback = false;
        try {
          result = methodHandle.invokeWithArguments(companionInstance);
          System.out.println(
              "DEBUG: MethodHandle.invokeWithArguments for param "
                  + paramIndex
                  + " returned: "
                  + result);
          if (result == null) {
            needsFallback = true;
          } else if (result instanceof Integer && ((Integer) result) == 0) {
            needsFallback = true;
          } else if (result instanceof String && ((String) result).isEmpty()) {
            needsFallback = true;
          }
        } catch (Throwable t) {
          System.out.println(
              "DEBUG: Exception in MethodHandle.invokeWithArguments for param "
                  + paramIndex
                  + ": "
                  + t.getMessage());
          needsFallback = true;
        }
        if (needsFallback) {
          try {
            Method m =
                companionInstance.getClass().getDeclaredMethod("apply$default$" + paramIndex);
            m.setAccessible(true);
            result = m.invoke(companionInstance);
            System.out.println(
                "DEBUG: Reflection invoke (companion) for param "
                    + paramIndex
                    + " returned: "
                    + result);
          } catch (Exception ex) {
            System.out.println(
                "DEBUG: Reflection fallback (companion) failed for param "
                    + paramIndex
                    + ": "
                    + ex.getMessage());
            throw ex;
          }
        }
        allDefaults.put(paramIndex, result);
      } catch (Throwable e) {
        throw new RuntimeException(
            "Error invoking default value method for " + cls.getName() + " param " + paramIndex, e);
      }
    }
    allDefaultValuesCache.put(cls, allDefaults);
    return allDefaults;
  }

  /**
   * Gets the default value for a specific field name in a Scala class. This method attempts to map
   * field names to parameter indices.
   *
   * @param cls the Scala class
   * @param fieldName the field name
   * @return the default value, or null if not found
   */
  public static Object getDefaultValueForField(Class<?> cls, String fieldName) {
    Preconditions.checkNotNull(cls, "Class must not be null");
    Preconditions.checkNotNull(fieldName, "Field name must not be null");
    Preconditions.checkArgument(
        isScalaClassWithDefaults(cls),
        "Class is not a Scala class with defaults: " + cls.getName());

    // First check if this is a regular Scala class with default values
    try {
      Method[] methods = cls.getDeclaredMethods();
      System.out.println(
          "DEBUG: Checking methods in " + cls.getName() + " for regular Scala class defaults");
      for (Method method : methods) {
        System.out.println("DEBUG: Method: " + method.getName());
        if (method.getName().startsWith("$lessinit$greater$default$")) {
          // Extract the parameter index from the method name
          String indexStr = method.getName().substring("$lessinit$greater$default$".length());
          int paramIndex = Integer.parseInt(indexStr);
          System.out.println(
              "DEBUG: Found default method: "
                  + method.getName()
                  + " for parameter index: "
                  + paramIndex);
          // This is a regular Scala class with default values
          // For regular Scala classes, we need to map field names to constructor parameters
          // Try to find the constructor and map field names to parameter indices
          java.lang.reflect.Constructor<?>[] constructors = cls.getDeclaredConstructors();
          System.out.println("DEBUG: Found " + constructors.length + " constructors");
          if (constructors.length > 0) {
            java.lang.reflect.Constructor<?> constructor =
                constructors[0]; // Use the first constructor
            System.out.println(
                "DEBUG: Constructor parameter count: " + constructor.getParameterCount());

            // Try to match by parameter name first
            for (int i = 0; i < constructor.getParameterCount(); i++) {
              String paramName = getParameterName(constructor, i);
              System.out.println("DEBUG: Parameter " + i + " name: " + paramName);
              if (fieldName.equals(paramName)) {
                System.out.println("DEBUG: Found parameter match for field: " + fieldName);
                // Use getAllDefaultValues to get the default value for this parameter
                Map<Integer, Object> allDefaults = getAllDefaultValues(cls);
                return allDefaults.get(i + 1); // Scala uses 1-based indexing
              }
            }

            // Fallback: match by field order
            try {
              java.lang.reflect.Field[] fields = cls.getDeclaredFields();
              System.out.println(
                  "DEBUG: Fields in class: "
                      + java.util.Arrays.toString(
                          java.util.Arrays.stream(fields)
                              .map(java.lang.reflect.Field::getName)
                              .toArray()));
              for (int i = 0; i < fields.length && i < constructor.getParameterCount(); i++) {
                if (fieldName.equals(fields[i].getName())) {
                  System.out.println(
                      "DEBUG: Found field match for field: " + fieldName + " at index " + i);
                  // Use getAllDefaultValues to get the default value for this parameter
                  Map<Integer, Object> allDefaults = getAllDefaultValues(cls);
                  return allDefaults.get(i + 1);
                }
              }
            } catch (Exception ex) {
              throw new RuntimeException("Error accessing fields for class " + cls.getName(), ex);
            }
          }
          break; // Found default methods, no need to continue
        }
      }
    } catch (Exception ex) {
      System.out.println("DEBUG: Exception in regular Scala class check: " + ex.getMessage());
      // Ignore exceptions and continue to case class check
    }

    // If not a regular Scala class, try case classes with companion objects
    String companionClassName = cls.getName() + "$";
    try {
      Class<?> companionClass = Class.forName(companionClassName, false, cls.getClassLoader());
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
        // Try to match by parameter name first
        for (int i = 0; i < applyMethod.getParameterCount(); i++) {
          String paramName = getParameterName(applyMethod, i);
          if (fieldName.equals(paramName)) {
            return getDefaultValue(cls, i + 1); // Scala uses 1-based indexing
          }
        }
        // Fallback: match by field order
        try {
          java.lang.reflect.Field[] fields = cls.getDeclaredFields();
          for (int i = 0; i < fields.length && i < applyMethod.getParameterCount(); i++) {
            if (fieldName.equals(fields[i].getName())) {
              return getDefaultValue(cls, i + 1);
            }
          }
        } catch (Exception e) {
          throw new RuntimeException("Error accessing fields for class " + cls.getName(), e);
        }
      }
    } catch (ClassNotFoundException e) {
      // For nested case classes, try to find the companion object in the enclosing class
      try {
        Class<?> enclosingClass = cls.getEnclosingClass();
        if (enclosingClass != null) {
          for (java.lang.reflect.Field field : enclosingClass.getDeclaredFields()) {
            if (field.getType().getName().equals(companionClassName)) {
              field.setAccessible(true);
              Object companionInstance = field.get(null);
              if (companionInstance != null) {
                Method[] methods = companionInstance.getClass().getDeclaredMethods();
                Method applyMethod = null;
                int maxParams = 0;
                for (Method method : methods) {
                  if ("apply".equals(method.getName()) && method.getParameterCount() > maxParams) {
                    applyMethod = method;
                    maxParams = method.getParameterCount();
                  }
                }
                if (applyMethod != null) {
                  // Try to match by parameter name first
                  for (int i = 0; i < applyMethod.getParameterCount(); i++) {
                    String paramName = getParameterName(applyMethod, i);
                    if (fieldName.equals(paramName)) {
                      return getDefaultValue(cls, i + 1); // Scala uses 1-based indexing
                    }
                  }
                  // Fallback: match by field order
                  try {
                    java.lang.reflect.Field[] fields = cls.getDeclaredFields();
                    for (int i = 0; i < fields.length && i < applyMethod.getParameterCount(); i++) {
                      if (fieldName.equals(fields[i].getName())) {
                        return getDefaultValue(cls, i + 1);
                      }
                    }
                  } catch (Exception ex) {
                    throw new RuntimeException(
                        "Error accessing fields for class " + cls.getName(), ex);
                  }
                }
                break; // Found the companion object, no need to continue
              }
            }
          }
        }
      } catch (Exception ex) {
        // Ignore exceptions for nested class detection
      }
    } catch (Exception e) {
      throw new RuntimeException(
          "Error getting default value for field " + fieldName + " in " + cls.getName(), e);
    }
    throw new RuntimeException(
        "No default value found for field " + fieldName + " in " + cls.getName());
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
   * Attempts to get the parameter name for a constructor parameter. This may not work if parameter
   * names are not preserved during compilation.
   */
  private static String getParameterName(
      java.lang.reflect.Constructor<?> constructor, int paramIndex) {
    try {
      // Try to get parameter names using reflection
      // Note: This requires -parameters compiler flag to work
      java.lang.reflect.Parameter[] parameters = constructor.getParameters();
      if (paramIndex < parameters.length && parameters[paramIndex].isNamePresent()) {
        return parameters[paramIndex].getName();
      }
    } catch (Exception e) {
      // Ignore exceptions
    }

    // Fallback: try to infer from field names
    try {
      java.lang.reflect.Field[] fields = constructor.getDeclaringClass().getDeclaredFields();
      if (paramIndex < fields.length) {
        return fields[paramIndex].getName();
      }
    } catch (Exception e) {
      // Ignore exceptions
    }

    return null;
  }

  /**
   * Checks if a class is a Scala class with default values by looking for the companion object and
   * checking for the presence of `apply` method and default value methods.
   */
  private static boolean checkIsScalaClassWithDefaults(Class<?> cls) {
    // First check for case classes with companion objects
    try {
      // Scala classes with default values have a companion object with the same name + "$"
      String companionClassName = cls.getName() + "$";
      Class<?> companionClass = Class.forName(companionClassName, false, cls.getClassLoader());

      // Check if the companion class has an `apply` method
      Method[] methods = companionClass.getDeclaredMethods();
      boolean hasApplyMethod = false;
      boolean hasDefaultMethods = false;

      for (Method method : methods) {
        if ("apply".equals(method.getName())) {
          hasApplyMethod = true;
        }
        if (method.getName().startsWith("apply$default$")) {
          hasDefaultMethods = true;
        }
      }

      // A Scala class with defaults should have both apply method and default value methods
      if (hasApplyMethod && hasDefaultMethods) {
        return true;
      }
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      // Not a case class with companion object, continue to check for regular Scala class
    }

    // For nested case classes, try to find the companion object in the enclosing class
    try {
      Class<?> enclosingClass = cls.getEnclosingClass();
      if (enclosingClass != null) {
        // Look for a companion object field in the enclosing class
        for (java.lang.reflect.Field field : enclosingClass.getDeclaredFields()) {
          if (field.getType().getName().equals(cls.getName() + "$")) {
            // Found a companion object field, check if it has default methods
            field.setAccessible(true);
            Object companionInstance = field.get(null);
            if (companionInstance != null) {
              Method[] companionMethods = companionInstance.getClass().getDeclaredMethods();
              boolean hasApplyMethod = false;
              boolean hasDefaultMethods = false;

              for (Method method : companionMethods) {
                if ("apply".equals(method.getName())) {
                  hasApplyMethod = true;
                }
                if (method.getName().startsWith("apply$default$")) {
                  hasDefaultMethods = true;
                }
              }

              if (hasApplyMethod && hasDefaultMethods) {
                return true;
              }
            }
          }
        }
      }
    } catch (Exception e) {
      // Ignore exceptions for nested class detection
    }

    // Check for regular Scala classes with default values (not case classes)
    // These have $lessinit$greater$default$N methods in the class itself
    try {
      Method[] methods = cls.getDeclaredMethods();
      boolean hasDefaultMethods = false;

      for (Method method : methods) {
        if (method.getName().startsWith("$lessinit$greater$default$")) {
          hasDefaultMethods = true;
          break;
        }
      }

      if (hasDefaultMethods) {
        return true;
      }
    } catch (Exception e) {
      // Ignore exceptions
    }

    return false;
  }

  /**
   * Gets the companion object instance for a Scala class, supporting both top-level and nested
   * classes.
   *
   * @param cls the Scala class
   * @return the companion object instance
   * @throws RuntimeException if the companion object cannot be accessed
   */
  private static Object getCompanionObject(Class<?> cls) {
    // Try the standard approach for top-level case classes
    String companionClassName = cls.getName() + "$";
    try {
      Class<?> companionClass = Class.forName(companionClassName, false, cls.getClassLoader());
      try {
        // Try to get the MODULE$ field (works for top-level objects)
        return companionClass.getField("MODULE$").get(null);
      } catch (NoSuchFieldException e) {
        // For nested case classes, try to find the companion object through the enclosing class
        Class<?> enclosingClass = cls.getEnclosingClass();
        if (enclosingClass != null) {
          // Try to get the companion object from the enclosing class
          try {
            // Look for a field in the enclosing class that holds the companion object
            for (java.lang.reflect.Field field : enclosingClass.getDeclaredFields()) {
              if (field.getType().equals(companionClass)) {
                field.setAccessible(true);
                return field.get(null);
              }
            }

            // If no field found, try to create an instance of the companion class
            // This works for some nested case classes where the companion is accessible
            try {
              return companionClass.newInstance();
            } catch (Exception ex) {
              // Try to find a constructor that takes the enclosing instance
              for (java.lang.reflect.Constructor<?> constructor :
                  companionClass.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0].equals(enclosingClass)) {
                  constructor.setAccessible(true);
                  // Try to get an instance of the enclosing class
                  Object enclosingInstance = getEnclosingInstance(enclosingClass);
                  if (enclosingInstance != null) {
                    return constructor.newInstance(enclosingInstance);
                  }
                }
              }
            }
          } catch (Exception ex) {
            // Continue to next approach
          }
        }

        // For some nested case classes, the companion object might be accessible via a different
        // pattern
        // Try to find any static field that returns the companion class
        for (java.lang.reflect.Field field : companionClass.getDeclaredFields()) {
          if (field.getType().equals(companionClass)
              && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            field.setAccessible(true);
            try {
              return field.get(null);
            } catch (Exception ex) {
              // Continue to next field
            }
          }
        }

        // Last resort: try to create an instance without parameters
        try {
          return companionClass.newInstance();
        } catch (Exception ex) {
          throw new RuntimeException(
              "Cannot access companion object for "
                  + cls.getName()
                  + " (nested case class not supported)",
              ex);
        }
      }
    } catch (ClassNotFoundException e) {
      // For nested case classes, try to find the companion object in the enclosing class
      try {
        Class<?> enclosingClass = cls.getEnclosingClass();
        if (enclosingClass != null) {
          // Look for a companion object field in the enclosing class
          for (java.lang.reflect.Field field : enclosingClass.getDeclaredFields()) {
            if (field.getType().getName().equals(companionClassName)) {
              field.setAccessible(true);
              return field.get(null);
            }
          }
        }
      } catch (Exception ex) {
        // Ignore exceptions for nested class detection
      }

      // Check if this is a regular Scala class with default values
      // For regular Scala classes, we return the class itself as the "companion object"
      try {
        Method[] methods = cls.getDeclaredMethods();
        for (Method method : methods) {
          if (method.getName().startsWith("$lessinit$greater$default$")) {
            // This is a regular Scala class with default values
            // Return the class itself as the companion object
            return cls;
          }
        }
      } catch (Exception ex) {
        // Ignore exceptions
      }

      throw new RuntimeException("Companion class not found for " + cls.getName(), e);
    } catch (Exception e) {
      throw new RuntimeException("Error accessing companion object for " + cls.getName(), e);
    }
  }

  /**
   * Attempts to get an instance of the enclosing class for a nested class. This is a best-effort
   * approach and may not work in all cases.
   */
  private static Object getEnclosingInstance(Class<?> enclosingClass) {
    try {
      // Try to get a static instance if available
      for (java.lang.reflect.Field field : enclosingClass.getDeclaredFields()) {
        if (field.getType().equals(enclosingClass)
            && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
          field.setAccessible(true);
          return field.get(null);
        }
      }

      // Try to create a new instance if there's a no-arg constructor
      try {
        return enclosingClass.newInstance();
      } catch (Exception e) {
        // Cannot create instance
        return null;
      }
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Finds all default value methods for a Scala class.
   *
   * @param cls the Scala class
   * @return a map from parameter index to method handle
   */
  private static Map<Integer, MethodHandle> findDefaultValueMethods(Class<?> cls) {
    Map<Integer, MethodHandle> methods = new HashMap<>();
    String companionClassName = cls.getName() + "$";

    // First try case classes with companion objects
    try {
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

      // If we found methods, return them
      if (!methods.isEmpty()) {
        return methods;
      }
    } catch (ClassNotFoundException e) {
      // For nested case classes, try to find the companion object in the enclosing class
      try {
        Class<?> enclosingClass = cls.getEnclosingClass();
        if (enclosingClass != null) {
          // Look for a companion object field in the enclosing class
          for (java.lang.reflect.Field field : enclosingClass.getDeclaredFields()) {
            if (field.getType().getName().equals(companionClassName)) {
              field.setAccessible(true);
              Object companionInstance = field.get(null);
              if (companionInstance != null) {
                MethodHandles.Lookup lookup =
                    _JDKAccess._trustedLookup(companionInstance.getClass());

                // Look for methods named `apply$default$1`, `apply$default$2`, etc.
                Method[] companionMethods = companionInstance.getClass().getDeclaredMethods();
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
                    } catch (NumberFormatException ex) {
                      // Skip if we can't parse the parameter index
                    }
                  }
                }
                if (!methods.isEmpty()) {
                  return methods;
                }
                break; // Found the companion object, no need to continue
              }
            }
          }
        }
      } catch (Exception ex) {
        // If anything goes wrong, continue to next approach
      }
    } catch (Exception e) {
      // If anything goes wrong, continue to next approach
    }

    // Check for regular Scala classes with default values (not case classes)
    // These have $lessinit$greater$default$N methods in the class itself
    try {
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(cls);
      Method[] classMethods = cls.getDeclaredMethods();

      System.out.println("DEBUG: Checking for default methods in " + cls.getName());
      for (Method method : classMethods) {
        String methodName = method.getName();
        System.out.println("DEBUG: Checking method: " + methodName);
        if (methodName.startsWith("$lessinit$greater$default$")) {
          try {
            // Extract the parameter index from the method name
            String indexStr = methodName.substring("$lessinit$greater$default$".length());
            int paramIndex = Integer.parseInt(indexStr);
            System.out.println(
                "DEBUG: Found default method: "
                    + methodName
                    + " for parameter index: "
                    + paramIndex);

            // Create method handle for the default value method
            MethodHandle methodHandle = lookup.unreflect(method);
            methods.put(paramIndex, methodHandle);
          } catch (NumberFormatException e) {
            System.out.println("DEBUG: Could not parse parameter index from method: " + methodName);
            // Skip if we can't parse the parameter index
          }
        }
      }
      System.out.println(
          "DEBUG: Found " + methods.size() + " default methods: " + methods.keySet());
    } catch (Exception e) {
      System.out.println("DEBUG: Exception in findDefaultValueMethods: " + e.getMessage());
      e.printStackTrace();
      // If anything goes wrong, return empty map
    }

    return methods;
  }
}
