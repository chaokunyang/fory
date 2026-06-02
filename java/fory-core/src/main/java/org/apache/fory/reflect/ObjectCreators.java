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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fory.annotation.ForyConstructor;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.resolver.TypeResolver;
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
 *   <li><strong>Classes without accessible constructors:</strong> Uses a private
 *       constructor-bypassing creator on runtimes where that is still supported
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
  private static final ClassValueCache<ObjectCreator<?>> objectStreamCache =
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
    return (ObjectCreator<T>) cache.get(type, () -> createObjectCreator(type, null));
  }

  public static <T> ObjectCreator<T> getObjectCreator(TypeResolver typeResolver, Class<T> type) {
    return typeResolver.getSharedRegistry().getObjectCreatorRegistry().getObjectCreator(type);
  }

  /**
   * Returns the creator used by Java ObjectStream-compatible serializers.
   *
   * <p>ObjectStream serializers reconstruct an empty instance before applying stream fields.
   * Serializable class constructors and constructor mappings from {@link ForyConstructor} or {@code
   * BaseFory.registerConstructor} are not semantically valid for this path.
   */
  @Internal
  public static <T> ObjectCreator<T> getObjectStreamCreator(Class<T> type) {
    return (ObjectCreator<T>) objectStreamCache.get(type, () -> createObjectStreamCreator(type));
  }

  static <T> ObjectCreator<T> createObjectCreator(
      Class<T> type, ConstructorMatch<T> registeredConstructor) {
    if (RecordUtils.isRecord(type)) {
      return new RecordObjectCreator<>(type);
    }
    ConstructorMatch<T> explicitConstructor = explicitConstructor(type, registeredConstructor);
    if (explicitConstructor != null) {
      return new ConstructorObjectCreator<>(type, explicitConstructor);
    }
    Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type);
    if (AndroidSupport.IS_ANDROID) {
      if (noArgConstructor != null) {
        return new ReflectiveNoArgCtrObjectCreator<>(type, noArgConstructor);
      }
      return new UnsupportedObjectCreator<>(
          type, "Android cannot create " + type + " without an accessible no-arg constructor");
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

  private static <T> ObjectCreator<T> createObjectStreamCreator(Class<T> type) {
    if (AndroidSupport.IS_ANDROID) {
      Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type);
      if (noArgConstructor != null) {
        return new ReflectiveNoArgCtrObjectCreator<>(type, noArgConstructor);
      }
      return new UnsupportedObjectCreator<>(
          type, "Android cannot create " + type + " without an accessible no-arg constructor");
    }
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE || JdkVersion.MAJOR_VERSION >= 25) {
      return new UnsafeObjectCreator<>(type);
    }
    return new ParentNoArgCtrObjectCreator<>(type);
  }

  static final class ConstructorMatch<T> {
    private final Constructor<T> constructor;
    private final String[] fieldNames;
    private final Class<?>[] declaringClasses;
    private final Class<?>[] fieldTypes;
    private final boolean[] finalFields;

    private ConstructorMatch(
        Constructor<T> constructor,
        String[] fieldNames,
        Class<?>[] declaringClasses,
        Class<?>[] fieldTypes,
        boolean[] finalFields) {
      this.constructor = constructor;
      this.fieldNames = fieldNames;
      this.declaringClasses = declaringClasses;
      this.fieldTypes = fieldTypes;
      this.finalFields = finalFields;
    }
  }

  private static <T> ConstructorMatch<T> explicitConstructor(
      Class<T> type, ConstructorMatch<T> registeredConstructor) {
    if (registeredConstructor != null) {
      return registeredConstructor;
    }
    Constructor<?> annotatedConstructor = null;
    ForyConstructor annotation = null;
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      ForyConstructor currentAnnotation = constructor.getAnnotation(ForyConstructor.class);
      if (currentAnnotation == null || isCompilerGeneratedConstructor(constructor)) {
        continue;
      }
      if (annotatedConstructor != null) {
        throw new ForyException(type + " must not declare more than one @ForyConstructor");
      }
      annotatedConstructor = constructor;
      annotation = currentAnnotation;
    }
    if (annotatedConstructor == null) {
      return null;
    }
    return explicitConstructor(
        type, (Constructor<T>) annotatedConstructor, annotation.value(), "@ForyConstructor");
  }

  static <T> ConstructorMatch<T> explicitConstructor(
      Class<T> type, Constructor<T> constructor, String[] fieldNames, String source) {
    if (isJavaPlatformType(type)) {
      throw new ForyException(
          source
              + " constructor binding is not supported for Java platform type "
              + type.getName());
    }
    if (constructor.getDeclaringClass() != type) {
      throw new ForyException(
          source + " constructor " + constructor + " does not belong to " + type);
    }
    if (fieldNames.length == 0) {
      throw new ForyException(
          source
              + " constructor "
              + constructor
              + " must map at least one field. Leave ordinary no-arg constructors unbound.");
    }
    if (fieldNames.length != constructor.getParameterCount()) {
      throw new ForyException(
          source
              + " constructor "
              + constructor
              + " maps "
              + fieldNames.length
              + " fields to "
              + constructor.getParameterCount()
              + " parameters");
    }
    ConstructorMatch<T> match =
        matchConstructorFields(
            constructor, fieldsByExplicitNames(type, constructor, fieldNames, source));
    if (match == null) {
      throw new ForyException(
          source
              + " constructor "
              + constructor
              + " parameter types do not match fields "
              + Arrays.toString(fieldNames));
    }
    return match;
  }

  private static boolean isCompilerGeneratedConstructor(Constructor<?> constructor) {
    if (constructor.isSynthetic()) {
      return true;
    }
    Class<?>[] parameterTypes = constructor.getParameterTypes();
    return parameterTypes.length > 0
        && "kotlin.jvm.internal.DefaultConstructorMarker"
            .equals(parameterTypes[parameterTypes.length - 1].getName());
  }

  private static boolean isJavaPlatformType(Class<?> type) {
    return type.getName().startsWith("java.");
  }

  private static Field[] fieldsByExplicitNames(
      Class<?> type, Constructor<?> constructor, String[] names, String source) {
    List<Field> fields = new ArrayList<>();
    fields.addAll(Descriptor.getFields(type));
    Map<String, Field> fieldsByName = new LinkedHashMap<>();
    Set<String> duplicateNames = new LinkedHashSet<>();
    for (Field field : fields) {
      Field previous = fieldsByName.put(field.getName(), field);
      if (previous != null) {
        duplicateNames.add(field.getName());
      }
    }
    Field[] constructorFields = new Field[names.length];
    Set<String> seenNames = new LinkedHashSet<>();
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      if (!seenNames.add(name)) {
        throw new ForyException(
            source + " constructor " + constructor + " maps " + name + " twice");
      }
      if (duplicateNames.contains(name)) {
        throw new ForyException(
            source
                + " constructor "
                + constructor
                + " cannot bind duplicate field name "
                + name
                + " in "
                + type);
      }
      Field field = fieldsByName.get(name);
      if (field == null) {
        throw new ForyException(
            source + " constructor " + constructor + " maps unknown field " + name);
      }
      constructorFields[i] = field;
    }
    return constructorFields;
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
        constructor, names, declaringClasses, fieldTypes, finalFieldFlags);
  }

  private static boolean constructorTypeMatches(Class<?> parameterType, Field field) {
    Class<?> boxedParameterType = TypeUtils.boxedType(parameterType);
    Class<?> boxedFieldType = TypeUtils.boxedType(field.getType());
    return boxedParameterType.isAssignableFrom(boxedFieldType);
  }

  private static MethodHandle constructorHandle(Class<?> type, Constructor<?> constructor) {
    Lookup lookup = _JDKAccess._trustedLookup(type);
    if (lookup == null) {
      return null;
    }
    try {
      MethodHandle handle =
          lookup.findConstructor(
              type, MethodType.methodType(void.class, constructor.getParameterTypes()));
      return handle.asSpreader(Object[].class, constructor.getParameterCount());
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

    private ConstructorObjectCreator(Class<T> type, ConstructorMatch<T> match) {
      super(type);
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
      return fieldNames.clone();
    }

    @Override
    public Class<?>[] getConstructorFieldDeclaringClasses() {
      return declaringClasses.clone();
    }

    @Override
    public Class<?>[] getConstructorFieldTypes() {
      return fieldTypes.clone();
    }

    @Override
    public boolean[] getConstructorFieldFinal() {
      return finalFields.clone();
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
        return (T) handle.invoke(arguments);
      } catch (Throwable e) {
        throw new ForyException("Failed to create instance using constructor: " + type, e);
      }
    }
  }

  private static final class UnsafeObjectCreator<T> extends ObjectCreator<T> {

    public UnsafeObjectCreator(Class<T> type) {
      super(type);
    }

    @Override
    public T newInstance() {
      return UnsafeObjectAllocator.allocate(type);
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
      handle =
          tuple2.f1 == null
              ? null
              : tuple2.f1.asSpreader(Object[].class, constructor.getParameterCount());
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
          return (T) handle.invoke(arguments);
        }
      } catch (Throwable e) {
        throw new ForyException("Failed to create record instance: " + type, e);
      }
    }
  }

  public static final class ParentNoArgCtrObjectCreator<T> extends ObjectCreator<T> {
    private static volatile Object reflectionFactory;
    private static volatile Method newConstructorForSerializationMethod;

    private final Constructor<T> constructor;

    public ParentNoArgCtrObjectCreator(Class<T> type) {
      super(type);
      if (JdkVersion.MAJOR_VERSION >= 25) {
        throw new ForyException(
            "ReflectionFactory object creation is unavailable in JDK25+ zero-Unsafe mode for "
                + type);
      }
      this.constructor = createSerializationConstructor(type);
    }

    private static <T> Constructor<T> createSerializationConstructor(Class<T> type) {
      try {
        if (reflectionFactory == null) {
          Class<?> reflectionFactoryClass = Class.forName("sun.reflect.ReflectionFactory");
          Method getReflectionFactory = reflectionFactoryClass.getMethod("getReflectionFactory");
          reflectionFactory = getReflectionFactory.invoke(null);
          newConstructorForSerializationMethod =
              reflectionFactoryClass.getMethod(
                  "newConstructorForSerialization", Class.class, Constructor.class);
        }
        Constructor<?> parentConstructor = findSerializationConstructor(type);
        return (Constructor<T>)
            newConstructorForSerializationMethod.invoke(reflectionFactory, type, parentConstructor);
      } catch (Throwable e) {
        throw new ForyException(
            "Failed to create instance, please provide a no-arg constructor for " + type, e);
      }
    }

    private static Constructor<?> findSerializationConstructor(Class<?> type)
        throws NoSuchMethodException {
      Class<?> current = type.getSuperclass();
      // Java ObjectStream reconstruction skips every Serializable class constructor and invokes
      // only the first non-Serializable superclass no-arg constructor.
      while (current != null && Serializable.class.isAssignableFrom(current)) {
        current = current.getSuperclass();
      }
      if (current == null) {
        current = Object.class;
      }
      Constructor<?> constructor = current.getDeclaredConstructor();
      if (!validSerializationConstructor(type, current, constructor)) {
        throw new ForyException(
            "First non-Serializable superclass "
                + current.getName()
                + " does not expose a valid no-arg constructor for "
                + type);
      }
      return constructor;
    }

    private static boolean validSerializationConstructor(
        Class<?> type, Class<?> constructorClass, Constructor<?> constructor) {
      int modifiers = constructor.getModifiers();
      if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
        return true;
      }
      if (Modifier.isPrivate(modifiers)) {
        return false;
      }
      return ReflectionUtils.getPackage(type).equals(ReflectionUtils.getPackage(constructorClass));
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
