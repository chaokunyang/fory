package org.apache.fory.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.exception.ForyException;
import org.apache.fory.memory.Platform;
import org.apache.fory.util.GraalvmSupport;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.record.RecordUtils;

@SuppressWarnings("unchecked")
public class ObjectCreators {
  private static final ClassValueCache<ObjectCreator<?>> cache =
      ClassValueCache.newClassKeyCache(8);

  public static <T> ObjectCreator<T> getObjectCreator(Class<T> type) {
    return (ObjectCreator<T>) cache.get(type, () -> creategetObjectCreator(type));
  }

  static <T> ObjectCreator<T> creategetObjectCreator(Class<T> type) {
    if (RecordUtils.isRecord(type)) {
      return new RecordCtrCreator<>(type);
    }
    Constructor<T> noArgConstructor = ReflectionUtils.getNoArgConstructor(type, true);
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && Platform.JAVA_VERSION >= 25) {
      if (noArgConstructor == null) {
        throw GraalvmSupport.throwNoArgCtrException(type);
      }
      if (noArgConstructor.getDeclaringClass() == type) {
        return new DeclaredNoArgCtrCreator<>(type);
      } else {
        return new NoArgSerializableObjectCreator<>(type);
      }
    }
    if (noArgConstructor == null || noArgConstructor.getDeclaringClass() != type) {
      return new UnsafeObjectCreator<>(type);
    }
    return new DeclaredNoArgCtrCreator<>(type);
  }

  public static final class UnsafeObjectCreator<T> extends ObjectCreator<T> {

    public UnsafeObjectCreator(Class<T> type) {
      super(type);
    }

    @Override
    public T newInstance() {
      return Platform.newInstance(type);
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class DeclaredNoArgCtrCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;

    public DeclaredNoArgCtrCreator(Class<T> type) {
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

  public static final class NoArgSerializableObjectCreator<T> extends ObjectCreator<T> {
    private final Constructor<T> ctr;

    public NoArgSerializableObjectCreator(Class<T> type) {
      this(type, ReflectionUtils.getNoArgConstructor(type, true));
    }

    public NoArgSerializableObjectCreator(Class<T> type, Constructor<T> ctr) {
      super(type);
      this.ctr = Preconditions.checkNotNull(ctr);
      try {
        ctr.setAccessible(true);
        // ensure it does work;
        newInstance();
      } catch (Throwable e) {
        throw new ForyException("Please provide a no-arg constructor for " + type);
      }
    }

    @Override
    public T newInstance() {
      try {
        return ctr.newInstance();
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      throw new UnsupportedOperationException();
    }
  }

  public static final class RecordCtrCreator<T> extends ObjectCreator<T> {
    private final MethodHandle handle;

    public RecordCtrCreator(Class<T> type) {
      super(type);
      handle = RecordUtils.getRecordCtrHandle(type);
    }

    @Override
    public T newInstance() {
      throw new UnsupportedOperationException();
    }

    @Override
    public T newInstanceWithArguments(Object... arguments) {
      try {
        return (T) handle.invokeWithArguments(arguments);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }
}
