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

package org.apache.fory.reflect;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.record.RecordUtils;

/**
 * Factory class for creating and caching {@link ObjectCreator} instances.
 *
 * <p>This class provides a centralized way to obtain optimized object creators for different types.
 * It automatically selects the most appropriate creation strategy based on the target type and
 * runtime environment:
 *
 * <ul>
 *   <li><strong>Record types:</strong> Uses {@link RecordObjectCreator} with MethodHandle for
 *       parameterized constructor invocation
 *   <li><strong>Classes with no-arg constructors:</strong> Uses {@link
 *       DeclaredNoArgCtrObjectCreator} with MethodHandle for fast invocation
 *   <li><strong>Classes without accessible constructors:</strong> Uses {@link UnsafeObjectCreator}
 *       with platform-specific unsafe allocation
 *   <li><strong>GraalVM native image compatibility:</strong> Uses {@link
 *       ParentNoArgCtrObjectCreator} for constructor generate-based creation when needed
 *   <li><strong>Android compatibility:</strong> Uses reflection for records and no-arg
 *       constructors, and throws when no supported reflective construction path exists
 * </ul>
 *
 * <p>All created ObjectCreator instances are cached using a soft reference cache to improve
 * performance on repeated requests for the same type.
 *
 * <p><strong>Thread Safety:</strong> This class and all returned ObjectCreator instances are
 * thread-safe and can be safely used across multiple threads concurrently.
 */
@SuppressWarnings("unchecked")
public class ObjectCreators {
  private static final ClassValueCache<ObjectCreator<?>> cache =
      ClassValueCache.newClassKeySoftCache(8);

  /**
   * Returns an optimized ObjectCreator for the given type.
   *
   * <p>This method automatically selects the most appropriate creation strategy based on the type
   * characteristics and caches the result for future use. The selection logic prioritizes
   * performance and platform compatibility.
   *
   * @param <T> the type for which to create an ObjectCreator
   * @param type the Class object representing the target type
   * @return a cached ObjectCreator instance optimized for the given type
   * @throws ForyException if the type cannot be instantiated (e.g., missing no-arg constructor in
   *     GraalVM native image)
   */
  public static <T> ObjectCreator<T> getObjectCreator(Class<T> type) {
    return (ObjectCreator<T>) cache.get(type, () -> creategetObjectCreator(type));
  }

  private static <T> ObjectCreator<T> creategetObjectCreator(Class<T> type) {
    if (RecordUtils.isRecord(type)) {
      return new RecordObjectCreator<>(type);
    }
    Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type);
    if (AndroidSupport.IS_ANDROID) {
      if (noArgConstructor != null) {
        return new ReflectiveNoArgCtrObjectCreator<>(type, noArgConstructor);
      }
      return new UnsupportedObjectCreator<>(
          type, "Android cannot create " + type + " without an accessible no-arg constructor");
    }
    if (JdkVersion.MAJOR_VERSION >= 25 && noArgConstructor == null) {
      return new ConstructorObjectCreator<>(type);
    }
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      if (noArgConstructor != null) {
        return new DeclaredNoArgCtrObjectCreator<>(type);
      } else {
        return new UnsafeObjectCreator<>(type);
      }
    }
    if (noArgConstructor == null) {
      return new UnsafeObjectCreator<>(type);
    }
    return new DeclaredNoArgCtrObjectCreator<>(type);
  }

  public static boolean supportsJdk25Creation(Class<?> type) {
    if (JdkVersion.MAJOR_VERSION < 25 || RecordUtils.isRecord(type)) {
      return true;
    }
    try {
      ObjectCreator<?> creator = creategetObjectCreator(type);
      return !(creator instanceof UnsupportedObjectCreator);
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static final class ConstructorMatch<T> {
    private final Constructor<T> constructor;
    private final String[] fieldNames;
    private final Class<?>[] declaringClasses;
    private final Class<?>[] fieldTypes;
    private final boolean[] finalFields;
    private final int score;

    private ConstructorMatch(
        Constructor<T> constructor,
        String[] fieldNames,
        Class<?>[] declaringClasses,
        Class<?>[] fieldTypes,
        boolean[] finalFields,
        int score) {
      this.constructor = constructor;
      this.fieldNames = fieldNames;
      this.declaringClasses = declaringClasses;
      this.fieldTypes = fieldTypes;
      this.finalFields = finalFields;
      this.score = score;
    }
  }

  private static <T> ConstructorMatch<T> findConstructor(Class<T> type) {
    List<Field> fields = new ArrayList<>();
    fields.addAll(Descriptor.getFields(type));
    Map<String, Field> fieldsByName = new LinkedHashMap<>();
    Map<String, List<Field>> fieldsByNameList = new LinkedHashMap<>();
    Map<Integer, Field> fieldsById = new LinkedHashMap<>();
    Set<String> duplicateNames = new LinkedHashSet<>();
    Set<Integer> duplicateIds = new LinkedHashSet<>();
    for (Field field : fields) {
      fieldsByNameList.computeIfAbsent(field.getName(), name -> new ArrayList<>()).add(field);
      Field previous = fieldsByName.put(field.getName(), field);
      if (previous != null) {
        duplicateNames.add(field.getName());
      }
      ForyField foryField = field.getAnnotation(ForyField.class);
      if (foryField != null && foryField.id() >= 0) {
        previous = fieldsById.put(foryField.id(), field);
        if (previous != null) {
          duplicateIds.add(foryField.id());
        }
      }
    }
    ConstructorMatch<T> best = null;
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      if (constructor.isSynthetic()) {
        continue;
      }
      ConstructorMatch<T> match =
          matchConstructor(
              type,
              (Constructor<T>) constructor,
              fieldsByName,
              fieldsByNameList,
              fieldsById,
              duplicateNames,
              duplicateIds);
      if (match != null && (best == null || match.score > best.score)) {
        best = match;
      }
    }
    if (best == null) {
      throw new ForyException(
          "JDK25 zero-Unsafe mode requires "
              + "a bindable constructor because no no-arg constructor is available"
              + " for "
              + type
              + ". Annotate the constructor with java.beans.ConstructorProperties or compile "
              + "the class with -parameters.");
    }
    return best;
  }

  private static <T> ConstructorMatch<T> matchConstructor(
      Class<T> type,
      Constructor<T> constructor,
      Map<String, Field> fieldsByName,
      Map<String, List<Field>> fieldsByNameList,
      Map<Integer, Field> fieldsById,
      Set<String> duplicateNames,
      Set<Integer> duplicateIds) {
    Field[] fields =
        constructorFields(
            constructor, fieldsByName, fieldsByNameList, fieldsById, duplicateNames, duplicateIds);
    if (fields == null) {
      return null;
    }
    return matchConstructorFields(constructor, fields);
  }

  private static <T> ConstructorMatch<T> matchConstructorFields(
      Constructor<T> constructor, Field[] fields) {
    Class<?>[] parameterTypes = constructor.getParameterTypes();
    String[] names = new String[fields.length];
    Class<?>[] declaringClasses = new Class<?>[fields.length];
    Class<?>[] fieldTypes = new Class<?>[fields.length];
    boolean[] finalFieldFlags = new boolean[fields.length];
    for (int i = 0; i < fields.length; i++) {
      Field field = fields[i];
      if (!constructorTypeMatches(parameterTypes[i], field)) {
        return null;
      }
      names[i] = field.getName();
      declaringClasses[i] = field.getDeclaringClass();
      fieldTypes[i] = field.getType();
      finalFieldFlags[i] = Modifier.isFinal(field.getModifiers());
    }
    return new ConstructorMatch<>(
        constructor, names, declaringClasses, fieldTypes, finalFieldFlags, 300 - fields.length);
  }

  private static Field[] constructorFields(
      Constructor<?> constructor,
      Map<String, Field> fieldsByName,
      Map<String, List<Field>> fieldsByNameList,
      Map<Integer, Field> fieldsById,
      Set<String> duplicateNames,
      Set<Integer> duplicateIds) {
    Field[] fields = fieldsByForyFieldId(constructor, fieldsById, duplicateIds);
    if (fields != null) {
      return fields;
    }
    String[] names = constructorFieldNames(constructor);
    if (names != null) {
      if (names.length != constructor.getParameterCount()) {
        return null;
      }
      return fieldsByName(constructor, fieldsByName, fieldsByNameList, duplicateNames, names);
    }
    return null;
  }

  private static Field[] fieldsByForyFieldId(
      Constructor<?> constructor, Map<Integer, Field> fieldsById, Set<Integer> duplicateIds) {
    Parameter[] parameters = constructor.getParameters();
    Field[] fields = new Field[parameters.length];
    boolean hasForyFieldId = false;
    for (int i = 0; i < parameters.length; i++) {
      ForyField foryField = parameters[i].getAnnotation(ForyField.class);
      if (foryField == null || foryField.id() < 0) {
        continue;
      }
      hasForyFieldId = true;
      int id = foryField.id();
      if (duplicateIds.contains(id)) {
        throw new ForyException("Constructor parameter id " + id + " is ambiguous");
      }
      fields[i] = fieldsById.get(id);
      if (fields[i] == null) {
        return null;
      }
    }
    if (!hasForyFieldId) {
      return null;
    }
    for (Field field : fields) {
      if (field == null) {
        return null;
      }
    }
    return fields;
  }

  private static Field[] fieldsByName(
      Constructor<?> constructor,
      Map<String, Field> fieldsByName,
      Map<String, List<Field>> fieldsByNameList,
      Set<String> duplicateNames,
      String[] names) {
    Field[] fields = new Field[names.length];
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      if (duplicateNames.contains(name)) {
        Field field = syntheticField(constructor, fieldsByNameList.get(name));
        if (field == null) {
          throw new ForyException(
              "Constructor parameter "
                  + name
                  + " is ambiguous because "
                  + constructor.getDeclaringClass()
                  + " has duplicate field names");
        }
        fields[i] = field;
        continue;
      }
      Field field = fieldsByName.get(name);
      if (field == null) {
        return null;
      }
      fields[i] = field;
    }
    return fields;
  }

  private static Field syntheticField(Constructor<?> constructor, List<Field> fields) {
    if (fields == null) {
      return null;
    }
    Class<?> declaringClass = constructor.getDeclaringClass();
    for (Field field : fields) {
      if (field.isSynthetic() && field.getDeclaringClass() == declaringClass) {
        return field;
      }
    }
    return null;
  }

  private static boolean constructorTypeMatches(Class<?> parameterType, Field field) {
    Class<?> boxedParameterType = TypeUtils.boxedType(parameterType);
    Class<?> boxedFieldType = TypeUtils.boxedType(field.getType());
    return boxedParameterType.isAssignableFrom(boxedFieldType);
  }

  private static String[] constructorFieldNames(Constructor<?> constructor) {
    String[] names = constructorProperties(constructor);
    if (names != null) {
      return names;
    }
    Parameter[] parameters = constructor.getParameters();
    for (Parameter parameter : parameters) {
      if (!parameter.isNamePresent()) {
        return null;
      }
    }
    names = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      names[i] = parameters[i].getName();
    }
    return names;
  }

  private static String[] constructorProperties(Constructor<?> constructor) {
    for (Annotation annotation : constructor.getDeclaredAnnotations()) {
      if ("java.beans.ConstructorProperties".equals(annotation.annotationType().getName())) {
        try {
          return (String[]) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (ReflectiveOperationException e) {
          throw new ForyException("Failed to read ConstructorProperties for " + constructor, e);
        }
      }
    }
    return null;
  }

  private static MethodHandle constructorHandle(Class<?> type, Constructor<?> constructor) {
    Lookup lookup = _JDKAccess._trustedLookup(type);
    if (lookup == null) {
      return null;
    }
    try {
      return lookup.findConstructor(
          type, MethodType.methodType(void.class, constructor.getParameterTypes()));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }

  private static final class ReflectiveNoArgCtrObjectCreator<T> extends ObjectCreator<T> {
    private final Constructor<T> constructor;

    private ReflectiveNoArgCtrObjectCreator(Class<T> type, Constructor<T> constructor) {
      super(type);
      this.constructor = constructor;
      try {
        constructor.setAccessible(true);
      } catch (RuntimeException e) {
        throw new ForyException("Failed to make no-arg constructor accessible for " + type, e);
      }
    }

    @Override
    public T newInstance() {
      try {
        return constructor.newInstance();
      } catch (Exception e) {
        throw new ForyException("Failed to create instance using no-arg constructor: " + type, e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class UnsupportedObjectCreator<T> extends ObjectCreator<T> {
    private final String message;

    private UnsupportedObjectCreator(Class<T> type, String message) {
      super(type);
      this.message = message;
    }

    @Override
    public T newInstance() {
      throw new ForyException(message);
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new ForyException(message);
    }
  }

  public static final class ConstructorObjectCreator<T> extends ObjectCreator<T> {
    private final Constructor<T> constructor;
    private final MethodHandle handle;
    private final String[] fieldNames;
    private final Class<?>[] declaringClasses;
    private final Class<?>[] fieldTypes;
    private final boolean[] finalFields;

    private ConstructorObjectCreator(Class<T> type) {
      super(type);
      ConstructorMatch<T> match = findConstructor(type);
      constructor = match.constructor;
      handle = constructorHandle(type, constructor);
      fieldNames = match.fieldNames;
      declaringClasses = match.declaringClasses;
      fieldTypes = match.fieldTypes;
      finalFields = match.finalFields;
      try {
        constructor.setAccessible(true);
      } catch (RuntimeException e) {
        throw new ForyException("Failed to make constructor accessible for " + type, e);
      }
    }

    @Override
    public boolean hasConstructorFields() {
      return true;
    }

    @Override
    public String[] getConstructorFieldNames() {
      return fieldNames;
    }

    @Override
    public Class<?>[] getConstructorFieldDeclaringClasses() {
      return declaringClasses;
    }

    @Override
    public Class<?>[] getConstructorFieldTypes() {
      return fieldTypes;
    }

    @Override
    public boolean[] getConstructorFieldFinal() {
      return finalFields;
    }

    @Override
    public boolean isConstructorPublic() {
      return Modifier.isPublic(type.getModifiers())
          && Modifier.isPublic(constructor.getModifiers());
    }

    @Override
    public boolean isOnlyPublicConstructor() {
      if (!isConstructorPublic()) {
        return false;
      }
      for (Constructor<?> declaredConstructor : type.getDeclaredConstructors()) {
        if (Modifier.isPublic(declaredConstructor.getModifiers())
            && declaredConstructor != constructor) {
          return false;
        }
      }
      return true;
    }

    @Override
    public T newInstance() {
      throw new ForyException(
          "JDK25 zero-Unsafe mode requires constructor field values to create " + type);
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      try {
        if (handle == null) {
          return constructor.newInstance(arguments);
        }
        return (T) handle.invokeWithArguments(arguments);
      } catch (Throwable e) {
        throw new ForyException("Failed to create instance using constructor: " + type, e);
      }
    }
  }

  public static final class UnsafeObjectCreator<T> extends ObjectCreator<T> {

    public UnsafeObjectCreator(Class<T> type) {
      super(type);
    }

    @Override
    public T newInstance() {
      return UnsafeOps.newInstance(type);
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class DeclaredNoArgCtrObjectCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;

    public DeclaredNoArgCtrObjectCreator(Class<T> type) {
      super(type);
      handle = ReflectionUtils.getCtrHandle(type, true);
    }

    @Override
    public T newInstance() {
      try {
        return (T) handle.invoke();
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class RecordObjectCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;
    private final Constructor<?> constructor;

    public RecordObjectCreator(Class<T> type) {
      super(type);
      Tuple2<Constructor, MethodHandle> tuple2 = RecordUtils.getRecordConstructor(type);
      constructor = tuple2.f0;
      handle = tuple2.f1;
      if (AndroidSupport.IS_ANDROID
          || (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && JdkVersion.MAJOR_VERSION >= 25)) {
        try {
          constructor.setAccessible(true);
        } catch (Throwable t) {
          throw new ForyException(
              "Failed to create instance, please provide a public constructor for " + type, t);
        }
      }
    }

    @Override
    public T newInstance() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      try {
        // compile-time constant is eligible for dead code elimination.
        if (AndroidSupport.IS_ANDROID
            || handle == null
            || (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && JdkVersion.MAJOR_VERSION >= 25)) {
          return (T) constructor.newInstance(arguments);
        } else {
          // Regular path: use method handle
          return (T) handle.invokeWithArguments(arguments);
        }
      } catch (Throwable e) {
        throw new ForyException("Failed to create record instance: " + type, e);
      }
    }
  }

  public static final class ParentNoArgCtrObjectCreator<T> extends ObjectCreator<T> {
    private static volatile Object reflectionFactory;
    private static volatile MethodHandle newConstructorForSerializationMethod;

    private final Constructor<T> constructor;

    public ParentNoArgCtrObjectCreator(Class<T> type) {
      super(type);
      this.constructor = createSerializationConstructor(type);
    }

    private static <T> Constructor<T> createSerializationConstructor(Class<T> type) {
      try {
        // Get ReflectionFactory instance
        if (reflectionFactory == null) {
          Class<?> reflectionFactoryClass;
          if (JdkVersion.MAJOR_VERSION >= 9) {
            reflectionFactoryClass = Class.forName("jdk.internal.reflect.ReflectionFactory");
          } else {
            reflectionFactoryClass = Class.forName("sun.reflect.ReflectionFactory");
          }
          Lookup lookup = _JDKAccess._trustedLookup(reflectionFactoryClass);
          MethodHandle handle =
              lookup.findStatic(
                  reflectionFactoryClass,
                  "getReflectionFactory",
                  MethodType.methodType(reflectionFactoryClass));
          reflectionFactory = handle.invoke();
          newConstructorForSerializationMethod =
              lookup.findVirtual(
                  reflectionFactoryClass,
                  "newConstructorForSerialization",
                  MethodType.methodType(Constructor.class, Class.class, Constructor.class));
        }
        // Find a public no-arg constructor in parent classes that we can use as a template
        Constructor<?> parentConstructor = findPublicNoArgConstructor(type);
        if (parentConstructor == null) {
          // Use Object's constructor as fallback
          parentConstructor = Object.class.getDeclaredConstructor();
        } else {
          try {
            parentConstructor.newInstance();
          } catch (Throwable ignored) {
            parentConstructor = Object.class.getDeclaredConstructor();
          }
        }
        // Create serialization constructor using ReflectionFactory
        return (Constructor<T>)
            newConstructorForSerializationMethod.invoke(reflectionFactory, type, parentConstructor);
      } catch (Throwable e) {
        throw new ForyException(
            "Failed to create instance, please provide a no-arg constructor for " + type, e);
      }
    }

    private static Constructor<?> findPublicNoArgConstructor(Class<?> type) {
      Class<?> current = type.getSuperclass();
      while (current != null && current != Object.class) {
        try {
          Constructor<?> constructor = current.getDeclaredConstructor();
          if (constructor.getModifiers() == java.lang.reflect.Modifier.PUBLIC) {
            return constructor;
          }
        } catch (NoSuchMethodException ignored) {
          // Continue searching
        }
        current = current.getSuperclass();
      }
      return null;
    }

    @Override
    public T newInstance() {
      try {
        return constructor.newInstance();
      } catch (Exception e) {
        throw new ForyException(
            "Failed to create instance, please provide a no-arg constructor for " + type, e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }
}
