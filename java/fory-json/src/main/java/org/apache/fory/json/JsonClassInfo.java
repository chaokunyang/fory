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

package org.apache.fory.json;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.fory.annotation.Expose;
import org.apache.fory.annotation.Ignore;
import org.apache.fory.reflect.ObjectInstantiator;
import org.apache.fory.reflect.ObjectInstantiators;

final class JsonClassInfo {
  private final Class<?> type;
  private final JsonPropertyInfo[] writeProperties;
  private final JsonPropertyInfo[] readProperties;
  private final JsonPropertyTable readTable;
  private final ObjectInstantiator<?> instantiator;
  private JsonStringObjectWriter stringWriter;
  private JsonUtf8ObjectWriter utf8Writer;

  private JsonClassInfo(
      Class<?> type,
      JsonPropertyInfo[] writeProperties,
      JsonPropertyInfo[] readProperties,
      ObjectInstantiator<?> instantiator) {
    this.type = type;
    this.writeProperties = writeProperties;
    this.readProperties = readProperties;
    readTable = new JsonPropertyTable(readProperties);
    this.instantiator = instantiator;
  }

  static JsonClassInfo build(Class<?> type) {
    if (type.isInterface()
        || Modifier.isAbstract(type.getModifiers())
        || type.isPrimitive()
        || type.isArray()
        || type.isEnum()) {
      throw new ForyJsonException("Unsupported JSON object type " + type);
    }
    boolean writeExpose = hasWriteExpose(type);
    boolean readExpose = hasReadExpose(type);
    TreeMap<String, PropertyBuilder> builders = new TreeMap<>();
    for (Field field : type.getFields()) {
      int modifiers = field.getModifiers();
      if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
        continue;
      }
      boolean write = includeWrite(field, writeExpose);
      boolean read = !Modifier.isFinal(modifiers) && includeRead(field, readExpose);
      if (!write && !read) {
        continue;
      }
      PropertyBuilder builder = builder(builders, field.getName());
      if (write) {
        builder.setWriteField(field);
      }
      if (read) {
        builder.setReadField(field);
      }
    }
    for (Method method : type.getMethods()) {
      int modifiers = method.getModifiers();
      if (Modifier.isStatic(modifiers) || method.isSynthetic() || method.isBridge()) {
        continue;
      }
      String name = method.getName();
      if (isGetter(method)) {
        if (!includeWrite(method, writeExpose)) {
          continue;
        }
        builder(builders, propertyName(name, 3)).setGetter(method);
      } else if (isBooleanGetter(method)) {
        if (!includeWrite(method, writeExpose)) {
          continue;
        }
        builder(builders, propertyName(name, 2)).setBooleanGetter(method);
      } else if (isSetter(method)) {
        if (!includeRead(method, readExpose)) {
          continue;
        }
        builder(builders, propertyName(name, 3)).setSetter(method);
      }
    }
    List<JsonPropertyInfo> writes = new ArrayList<>();
    List<JsonPropertyInfo> reads = new ArrayList<>();
    for (PropertyBuilder builder : builders.values()) {
      JsonPropertyInfo property = builder.build();
      if (builder.writeAccessor != null) {
        writes.add(property);
      }
      if (builder.readAccessor != null) {
        reads.add(property);
      }
    }
    JsonPropertyInfo[] writeArray = writes.toArray(new JsonPropertyInfo[0]);
    JsonPropertyInfo[] readArray = reads.toArray(new JsonPropertyInfo[0]);
    return new JsonClassInfo(
        type, writeArray, readArray, ObjectInstantiators.createObjectInstantiator(type));
  }

  public Class<?> type() {
    return type;
  }

  public JsonPropertyInfo[] writeProperties() {
    return writeProperties;
  }

  public JsonPropertyTable readTable() {
    return readTable;
  }

  public Object newInstance() {
    return instantiator.newInstance();
  }

  void setObjectWriters(JsonObjectWriters objectWriters) {
    if (objectWriters == null) {
      return;
    }
    stringWriter = objectWriters.stringWriter();
    utf8Writer = objectWriters.utf8Writer();
  }

  boolean hasObjectWriter() {
    return stringWriter != null && utf8Writer != null;
  }

  public void write(JsonWriter writer, Object value, JsonClassCache classCache) {
    writer.writeObjectStart();
    int written = 0;
    JsonPropertyInfo[] properties = writeProperties;
    int length = properties.length;
    int i = 0;
    while (i + 4 <= length) {
      if (properties[i++].write(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].write(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].write(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].write(writer, value, classCache, written)) {
        written++;
      }
    }
    while (i < length) {
      if (properties[i++].write(writer, value, classCache, written)) {
        written++;
      }
    }
    writer.writeObjectEnd();
  }

  public void write(StringJsonWriter writer, Object value, JsonClassCache classCache) {
    JsonStringObjectWriter generatedWriter = stringWriter;
    if (generatedWriter != null) {
      generatedWriter.writeString(writer, value, classCache);
      return;
    }
    writer.writeObjectStart();
    int written = 0;
    JsonPropertyInfo[] properties = writeProperties;
    int length = properties.length;
    int i = 0;
    while (i + 4 <= length) {
      if (properties[i++].writeString(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].writeString(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].writeString(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].writeString(writer, value, classCache, written)) {
        written++;
      }
    }
    while (i < length) {
      if (properties[i++].writeString(writer, value, classCache, written)) {
        written++;
      }
    }
    writer.writeObjectEnd();
  }

  public void writeUtf8(Utf8JsonWriter writer, Object value, JsonClassCache classCache) {
    JsonUtf8ObjectWriter generatedWriter = utf8Writer;
    if (generatedWriter != null) {
      generatedWriter.writeUtf8(writer, value, classCache);
      return;
    }
    writer.writeObjectStart();
    int written = 0;
    JsonPropertyInfo[] properties = writeProperties;
    int length = properties.length;
    int i = 0;
    while (i + 4 <= length) {
      if (properties[i++].writeUtf8(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].writeUtf8(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].writeUtf8(writer, value, classCache, written)) {
        written++;
      }
      if (properties[i++].writeUtf8(writer, value, classCache, written)) {
        written++;
      }
    }
    while (i < length) {
      if (properties[i++].writeUtf8(writer, value, classCache, written)) {
        written++;
      }
    }
    writer.writeObjectEnd();
  }

  private static PropertyBuilder builder(Map<String, PropertyBuilder> builders, String name) {
    PropertyBuilder builder = builders.get(name);
    if (builder == null) {
      builder = new PropertyBuilder(name);
      builders.put(name, builder);
    }
    return builder;
  }

  private static boolean hasWriteExpose(Class<?> type) {
    for (Field field : type.getFields()) {
      if (field.isAnnotationPresent(Expose.class)) {
        return true;
      }
    }
    for (Method method : type.getMethods()) {
      if ((isGetter(method) || isBooleanGetter(method))
          && method.isAnnotationPresent(Expose.class)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasReadExpose(Class<?> type) {
    for (Field field : type.getFields()) {
      if (!Modifier.isFinal(field.getModifiers()) && field.isAnnotationPresent(Expose.class)) {
        return true;
      }
    }
    for (Method method : type.getMethods()) {
      if (isSetter(method) && method.isAnnotationPresent(Expose.class)) {
        return true;
      }
    }
    return false;
  }

  private static boolean includeWrite(Member member, boolean exposeMode) {
    return include(member, exposeMode, true);
  }

  private static boolean includeRead(Member member, boolean exposeMode) {
    return include(member, exposeMode, false);
  }

  private static boolean include(Member member, boolean exposeMode, boolean write) {
    Ignore ignore = annotation(member, Ignore.class);
    boolean ignored = ignore != null && (write ? ignore.ignoreWrite() : ignore.ignoreRead());
    boolean exposed = annotation(member, Expose.class) != null;
    if (ignored && exposed) {
      throw new ForyJsonException("JSON member cannot be both exposed and ignored: " + member);
    }
    if (ignored) {
      return false;
    }
    return !exposeMode || exposed;
  }

  private static <T extends java.lang.annotation.Annotation> T annotation(
      Member member, Class<T> annotationType) {
    if (member instanceof Field) {
      return ((Field) member).getAnnotation(annotationType);
    }
    return ((Method) member).getAnnotation(annotationType);
  }

  private static boolean isGetter(Method method) {
    String name = method.getName();
    return name.length() > 3
        && name.startsWith("get")
        && !"getClass".equals(name)
        && method.getParameterCount() == 0
        && method.getReturnType() != void.class;
  }

  private static boolean isBooleanGetter(Method method) {
    String name = method.getName();
    Class<?> returnType = method.getReturnType();
    return name.length() > 2
        && name.startsWith("is")
        && method.getParameterCount() == 0
        && (returnType == boolean.class || returnType == Boolean.class);
  }

  private static boolean isSetter(Method method) {
    String name = method.getName();
    return name.length() > 3
        && name.startsWith("set")
        && method.getParameterCount() == 1
        && method.getReturnType() == void.class;
  }

  private static String propertyName(String methodName, int prefixLength) {
    String suffix = methodName.substring(prefixLength);
    if (suffix.length() > 1
        && Character.isUpperCase(suffix.charAt(0))
        && Character.isUpperCase(suffix.charAt(1))) {
      return suffix;
    }
    return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
  }

  private static Class<?> box(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    } else if (type == boolean.class) {
      return Boolean.class;
    } else if (type == byte.class) {
      return Byte.class;
    } else if (type == short.class) {
      return Short.class;
    } else if (type == int.class) {
      return Integer.class;
    } else if (type == long.class) {
      return Long.class;
    } else if (type == float.class) {
      return Float.class;
    } else if (type == double.class) {
      return Double.class;
    } else if (type == char.class) {
      return Character.class;
    }
    return type;
  }

  private static final class PropertyBuilder {
    private final String name;
    private Field writeField;
    private Field readField;
    private Method getter;
    private Method booleanGetter;
    private Method setter;
    private JsonMemberAccessor writeAccessor;
    private JsonMemberAccessor readAccessor;

    private PropertyBuilder(String name) {
      this.name = name;
    }

    private void setWriteField(Field field) {
      if (writeField != null) {
        throw new ForyJsonException("Duplicate public JSON field " + name);
      }
      writeField = field;
    }

    private void setReadField(Field field) {
      if (readField != null) {
        throw new ForyJsonException("Duplicate public JSON field " + name);
      }
      readField = field;
    }

    private void setGetter(Method method) {
      if (getter != null) {
        throw new ForyJsonException("Duplicate JSON getter " + name);
      }
      getter = method;
    }

    private void setBooleanGetter(Method method) {
      if (booleanGetter != null) {
        throw new ForyJsonException("Duplicate JSON boolean getter " + name);
      }
      booleanGetter = method;
    }

    private void setSetter(Method method) {
      if (setter != null) {
        throw new ForyJsonException("Duplicate JSON setter " + name);
      }
      setter = method;
    }

    private JsonPropertyInfo build() {
      Member writeMember = writeMember();
      Member readMember = readMember();
      Type writeType = memberType(writeMember, true);
      Type readType = memberType(readMember, false);
      Class<?> writeRawType = memberRawType(writeMember, true);
      Class<?> readRawType = memberRawType(readMember, false);
      if (writeRawType != null
          && readRawType != null
          && !box(readRawType).isAssignableFrom(box(writeRawType))) {
        throw new ForyJsonException("Incompatible JSON property types for " + name);
      }
      writeAccessor = writeMember == null ? null : accessor(writeMember);
      readAccessor = readMember == null ? null : accessor(readMember);
      return new JsonPropertyInfo(
          name,
          writeMember,
          writeType,
          writeRawType,
          readType,
          readRawType,
          writeAccessor,
          readAccessor);
    }

    private Member writeMember() {
      if (booleanGetter != null) {
        return booleanGetter;
      } else if (getter != null) {
        return getter;
      }
      return writeField;
    }

    private Member readMember() {
      return setter != null ? setter : readField;
    }

    private Type memberType(Member member, boolean write) {
      if (member == null) {
        return null;
      } else if (member instanceof Field) {
        return ((Field) member).getGenericType();
      }
      Method method = (Method) member;
      return write ? method.getGenericReturnType() : method.getGenericParameterTypes()[0];
    }

    private Class<?> memberRawType(Member member, boolean write) {
      if (member == null) {
        return null;
      } else if (member instanceof Field) {
        return ((Field) member).getType();
      }
      Method method = (Method) member;
      return write ? method.getReturnType() : method.getParameterTypes()[0];
    }

    private JsonMemberAccessor accessor(Member member) {
      if (member instanceof Field) {
        return JsonMemberAccessor.forField((Field) member);
      }
      return JsonMemberAccessor.forMethod((Method) member);
    }
  }
}
