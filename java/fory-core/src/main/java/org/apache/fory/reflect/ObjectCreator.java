package org.apache.fory.reflect;

public abstract class ObjectCreator<T> {
  protected final Class<T> type;

  protected ObjectCreator(Class<T> type) {
    this.type = type;
  }

  public abstract T newInstance();

  public abstract T newInstanceWithArguments(Object... arguments);
}
