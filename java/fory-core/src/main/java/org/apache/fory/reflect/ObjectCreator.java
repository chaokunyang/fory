package org.apache.fory.reflect;

/**
 * Abstract base class for creating instances of a given type.
 *
 * <p>This class provides a unified interface for object instantiation across different creation
 * strategies such as constructor invocation, unsafe allocation, and record creation.
 * Implementations handle various scenarios including no-arg constructors, parameterized
 * constructors for records, and platform-specific optimizations.
 *
 * <p><strong>Thread Safety:</strong> All implementations of ObjectCreator are thread-safe and can
 * be safely used across multiple threads concurrently. The underlying creation mechanisms
 * (MethodHandle, Constructor, Platform.newInstance) are all thread-safe.
 *
 * @param <T> the type of objects this creator can instantiate
 */
public abstract class ObjectCreator<T> {
  protected final Class<T> type;

  protected ObjectCreator(Class<T> type) {
    this.type = type;
  }

  /**
   * Creates a new instance of type T using the default creation strategy.
   *
   * @return a new instance of type T
   * @throws RuntimeException if instance creation fails
   * @throws UnsupportedOperationException if this creator doesn't support parameterless creation
   */
  public abstract T newInstance();

  /**
   * Creates a new instance of type T using the provided arguments.
   *
   * <p>This method is primarily used for record types that require constructor arguments. Most
   * implementations will throw UnsupportedOperationException.
   *
   * @param arguments the arguments to pass to the constructor
   * @return a new instance of type T
   * @throws RuntimeException if instance creation fails
   * @throws UnsupportedOperationException if this creator doesn't support parameterized creation
   */
  public abstract T newInstanceWithArguments(Object... arguments);
}
