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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.apache.fory.json.annotation.JsonSubTypes;
import org.apache.fory.json.annotation.JsonType;
import org.apache.fory.json.codec.JsonObjectModel;
import org.apache.fory.json.codec.JsonValueCodec;
import org.apache.fory.json.codec.ObjectCodec;
import org.apache.fory.json.data.PublicFields;
import org.apache.fory.json.reader.Latin1JsonReader;
import org.apache.fory.json.reader.Utf16JsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonSharedRegistry;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.meta.TypeExtMeta;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.serializer.StringSerializer;
import org.apache.fory.type.Types;
import org.testng.annotations.Test;

public class JsonGeneratedCapabilityKeyTest {
  @Test
  public void outerNullabilityReusesObjectClasses() {
    JsonTypeResolver resolver = resolver();
    JsonTypeInfo raw = resolver.getTypeInfo(Model.class, Model.class);
    JsonTypeInfo nonNull = resolver.getTypeInfo(TypeRef.of(Model.class, ordinary(false)));
    JsonTypeInfo nullable = resolver.getTypeInfo(TypeRef.of(Model.class, ordinary(true)));

    assertObjectClasses(raw, nonNull);
    assertObjectClasses(raw, nullable);

    JsonTypeInfo tracked =
        resolver.getTypeInfo(
            TypeRef.of(Model.class, TypeExtMeta.of(Types.UNKNOWN, false, true, false, false)));
    assertSame(raw.utf8Reader().getClass(), tracked.utf8Reader().getClass());
  }

  @Test
  public void outerNullabilityReusesCollectionClasses() {
    JsonTypeResolver resolver = resolver();
    TypeRef<?> element = TypeRef.of(String.class);
    JsonTypeInfo raw = resolver.getTypeInfo(listType(null, element));
    JsonTypeInfo nonNull = resolver.getTypeInfo(listType(ordinary(false), element));
    JsonTypeInfo nullable = resolver.getTypeInfo(listType(ordinary(true), element));

    assertSame(raw.utf8Writer().getClass(), nonNull.utf8Writer().getClass());
    assertSame(raw.utf8Writer().getClass(), nullable.utf8Writer().getClass());
    assertSame(raw.utf8Reader().getClass(), nonNull.utf8Reader().getClass());
    assertSame(raw.utf8Reader().getClass(), nullable.utf8Reader().getClass());

    JsonTypeInfo nonNullElement =
        resolver.getTypeInfo(listType(null, TypeRef.of(String.class, ordinary(false))));
    JsonTypeInfo nullableElement =
        resolver.getTypeInfo(listType(null, TypeRef.of(String.class, ordinary(true))));
    assertSame(nonNullElement.utf8Writer().getClass(), nullableElement.utf8Writer().getClass());
    assertSame(nonNullElement.utf8Reader().getClass(), nullableElement.utf8Reader().getClass());
  }

  @Test
  public void injectedComponentMetadataReusesParentClass() {
    JsonTypeResolver resolver = resolver();
    TypeRef<?> nonNullArray =
        TypeRef.of(
            String[].class, ordinary(false), null, TypeRef.of(String.class, ordinary(false)));
    TypeRef<?> nullableArray =
        TypeRef.of(String[].class, ordinary(false), null, TypeRef.of(String.class, ordinary(true)));
    JsonTypeInfo nonNull = resolver.getTypeInfo(boxType(nonNullArray));
    JsonTypeInfo nullable = resolver.getTypeInfo(boxType(nullableArray));
    assertSame(nonNull.utf8Writer().getClass(), nullable.utf8Writer().getClass());
    assertSame(nonNull.utf8Reader().getClass(), nullable.utf8Reader().getClass());
  }

  @Test
  public void fixedSubtypeIsGeneratedLeaf() {
    ForyJson json =
        ForyJson.builder()
            .registerCodec(
                FixedValue.class,
                (type, resolver, runtimeType) ->
                    resolver.createObjectCodec(
                        type, JsonObjectModel.fixedInstance(FixedValue.INSTANCE)))
            .withAsyncCompilation(false)
            .build();
    JsonTypeInfo typeInfo =
        JsonTestSupport.currentTypeResolver(json)
            .getTypeInfo(FixedContainer.class, FixedContainer.class);
    assertGeneratedObject(typeInfo);

    FixedContainer value = new FixedContainer();
    value.value = FixedValue.INSTANCE;
    assertSame(json.fromJson(json.toJson(value), FixedContainer.class).value, FixedValue.INSTANCE);

    String input = "{\"value\":{\"kind\":\"fixed\"}}";
    if (StringSerializer.isBytesBackedString()) {
      assertSame(
          ((FixedContainer)
                  typeInfo.latin1Reader().readLatin1(JsonTestSupport.newLatin1Reader(input)))
              .value,
          FixedValue.INSTANCE);
    }
    assertSame(
        ((FixedContainer) typeInfo.utf16Reader().readUtf16(JsonTestSupport.newUtf16Reader(input)))
            .value,
        FixedValue.INSTANCE);
    assertSame(
        ((FixedContainer)
                typeInfo
                    .utf8Reader()
                    .readUtf8(
                        JsonTestSupport.newUtf8Reader(input.getBytes(StandardCharsets.UTF_8))))
            .value,
        FixedValue.INSTANCE);
  }

  @Test
  public void hostedModelNeedsNoCompanion() throws Exception {
    ForyJson json = ForyJson.builder().withAsyncCompilation(false).build();
    Constructor<JsonSharedRegistry> constructor =
        JsonSharedRegistry.class.getDeclaredConstructor(
            JsonConfig.class, ExecutorService.class, boolean.class);
    constructor.setAccessible(true);
    JsonSharedRegistry registry = constructor.newInstance(json.config(), null, true);
    JsonTypeInfo typeInfo =
        new JsonTypeResolver(registry)
            .getTypeInfo(HostedAnnotatedModel.class, HostedAnnotatedModel.class);
    assertSame(typeInfo.rawType(), HostedAnnotatedModel.class);
  }

  @Test
  public void directCodecClassVersionsParent() {
    JsonTypeInfo first = parentType(new ChildCodecA());
    JsonTypeInfo equivalent = parentType(new ChildCodecA());
    JsonTypeInfo different = parentType(new ChildCodecB());

    assertObjectClasses(first, equivalent);
    assertDifferentObjectClasses(first, different);
  }

  @Test
  public void directCodecStateStaysInstanceOwned() {
    ForyJson first = parentJson(new StatefulChildCodec("first:"));
    ForyJson second = parentJson(new StatefulChildCodec("second:"));
    JsonTypeInfo firstType = parentType(first);
    JsonTypeInfo secondType = parentType(second);
    assertObjectClasses(firstType, secondType);

    Parent value = new Parent();
    value.child = new Child();
    value.child.value = "value";
    assertEquals(first.toJson(value), "{\"child\":\"first:value\"}");
    assertEquals(second.toJson(value), "{\"child\":\"second:value\"}");
    assertEquals(first.fromJson("{\"child\":\"first:value\"}", Parent.class).child.value, "value");
    assertEquals(
        second.fromJson("{\"child\":\"second:value\"}", Parent.class).child.value, "value");
  }

  @Test
  public void unrelatedRegistrationDoesNotVersionParent() {
    JsonTypeInfo first = parentType(new ChildCodecA());
    ForyJson json = parentJson(new ChildCodecA(), true);
    assertObjectClasses(first, parentType(json));
  }

  @Test
  public void configuredLoaderDoesNotVersionClass() {
    ForyJson first = ForyJson.builder().withAsyncCompilation(false).build();
    ForyJson second =
        ForyJson.builder()
            .withClassLoader(new ClassLoader(Model.class.getClassLoader()) {})
            .withAsyncCompilation(false)
            .build();
    JsonTypeInfo firstType =
        JsonTestSupport.currentTypeResolver(first).getTypeInfo(Model.class, Model.class);
    JsonTypeInfo secondType =
        JsonTestSupport.currentTypeResolver(second).getTypeInfo(Model.class, Model.class);
    assertObjectClasses(firstType, secondType);
  }

  @Test
  public void collectionClassIgnoresElementCodec() {
    JsonTypeInfo first = collectionType(new ChildCodecA());
    JsonTypeInfo different = collectionType(new ChildCodecB());

    assertSame(first.utf8Writer().getClass(), different.utf8Writer().getClass());
    assertSame(first.utf8Reader().getClass(), different.utf8Reader().getClass());
  }

  @Test
  public void sameNamedLoaderClassesDoNotCollide() throws Exception {
    byte[] bytes = classBytes(PublicFields.class);
    Class<?> firstClass = shadowClass(PublicFields.class, bytes);
    Class<?> secondClass = shadowClass(PublicFields.class, bytes);
    assertNotSame(firstClass, secondClass);

    JsonTypeInfo first = loaderType(firstClass);
    JsonTypeInfo second = loaderType(secondClass);
    assertDifferentObjectClasses(first, second);
  }

  private static JsonTypeResolver resolver() {
    ForyJson json = ForyJson.builder().withAsyncCompilation(false).build();
    return JsonTestSupport.currentTypeResolver(json);
  }

  private static JsonTypeInfo parentType(JsonValueCodec<Child> codec) {
    return parentType(parentJson(codec));
  }

  private static ForyJson parentJson(JsonValueCodec<Child> codec) {
    return parentJson(codec, false);
  }

  private static ForyJson parentJson(JsonValueCodec<Child> codec, boolean unrelated) {
    ForyJsonBuilder builder =
        ForyJson.builder().registerCodec(Child.class, codec).withAsyncCompilation(false);
    if (unrelated) {
      builder.registerCodec(Unrelated.class, JsonTestSupport.nullCodec());
    }
    return builder.build();
  }

  private static JsonTypeInfo parentType(ForyJson json) {
    return JsonTestSupport.currentTypeResolver(json).getTypeInfo(Parent.class, Parent.class);
  }

  private static JsonTypeInfo collectionType(JsonValueCodec<Child> codec) {
    ForyJson json =
        ForyJson.builder().registerCodec(Child.class, codec).withAsyncCompilation(false).build();
    return JsonTestSupport.currentTypeResolver(json).getTypeInfo(new TypeRef<List<Child>>() {});
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static JsonTypeInfo loaderType(Class<?> type) {
    ForyJson json =
        ForyJson.builder()
            .withClassLoader(type.getClassLoader())
            .withAsyncCompilation(false)
            .build();
    return JsonTestSupport.currentTypeResolver(json).getTypeInfo((Class) type, type);
  }

  private static Class<?> shadowClass(Class<?> type, byte[] bytes) throws ClassNotFoundException {
    String name = type.getName();
    ClassLoader loader =
        new ClassLoader(type.getClassLoader()) {
          @Override
          protected Class<?> loadClass(String className, boolean resolve)
              throws ClassNotFoundException {
            synchronized (getClassLoadingLock(className)) {
              if (!name.equals(className)) {
                return super.loadClass(className, resolve);
              }
              Class<?> loaded = findLoadedClass(className);
              if (loaded == null) {
                loaded = defineClass(className, bytes, 0, bytes.length);
              }
              if (resolve) {
                resolveClass(loaded);
              }
              return loaded;
            }
          }
        };
    return loader.loadClass(name);
  }

  private static byte[] classBytes(Class<?> type) throws IOException {
    String resource = "/" + type.getName().replace('.', '/') + ".class";
    try (InputStream input = type.getResourceAsStream(resource);
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static TypeExtMeta ordinary(boolean nullable) {
    return TypeExtMeta.of(Types.UNKNOWN, nullable, false, false, false);
  }

  private static TypeRef<?> listType(TypeExtMeta metadata, TypeRef<?> element) {
    return TypeRef.ofDeclaredTypeArguments(
        List.class, metadata, Collections.singletonList(element), null);
  }

  private static TypeRef<?> boxType(TypeRef<?> value) {
    return TypeRef.ofDeclaredTypeArguments(
        Box.class, ordinary(false), Collections.singletonList(value), null);
  }

  private static void assertObjectClasses(JsonTypeInfo expected, JsonTypeInfo actual) {
    assertSame(expected.stringWriter().getClass(), actual.stringWriter().getClass());
    assertSame(expected.utf8Writer().getClass(), actual.utf8Writer().getClass());
    assertSame(expected.latin1Reader().getClass(), actual.latin1Reader().getClass());
    assertSame(expected.utf16Reader().getClass(), actual.utf16Reader().getClass());
    assertSame(expected.utf8Reader().getClass(), actual.utf8Reader().getClass());
  }

  private static void assertDifferentObjectClasses(JsonTypeInfo expected, JsonTypeInfo actual) {
    assertNotSame(expected.stringWriter().getClass(), actual.stringWriter().getClass());
    assertNotSame(expected.utf8Writer().getClass(), actual.utf8Writer().getClass());
    assertNotSame(expected.latin1Reader().getClass(), actual.latin1Reader().getClass());
    assertNotSame(expected.utf16Reader().getClass(), actual.utf16Reader().getClass());
    assertNotSame(expected.utf8Reader().getClass(), actual.utf8Reader().getClass());
  }

  private static void assertGeneratedObject(JsonTypeInfo typeInfo) {
    assertFalse(ObjectCodec.class.isAssignableFrom(typeInfo.stringWriter().getClass()));
    assertFalse(ObjectCodec.class.isAssignableFrom(typeInfo.utf8Writer().getClass()));
    assertFalse(ObjectCodec.class.isAssignableFrom(typeInfo.latin1Reader().getClass()));
    assertFalse(ObjectCodec.class.isAssignableFrom(typeInfo.utf16Reader().getClass()));
    assertFalse(ObjectCodec.class.isAssignableFrom(typeInfo.utf8Reader().getClass()));
  }

  public static final class Model {
    public String value;

    public Model() {}
  }

  public static final class Parent {
    public Child child;

    public Parent() {}
  }

  public static final class Child {
    public String value;

    public Child() {}
  }

  public static class ChildCodecA implements JsonValueCodec<Child> {
    @Override
    public void writeString(StringJsonWriter writer, Child value) {
      writer.writeString(value.value);
    }

    @Override
    public void writeUtf8(Utf8JsonWriter writer, Child value) {
      writer.writeString(value.value);
    }

    @Override
    public Child readLatin1(Latin1JsonReader reader) {
      return child(reader.readString());
    }

    @Override
    public Child readUtf16(Utf16JsonReader reader) {
      return child(reader.readString());
    }

    @Override
    public Child readUtf8(Utf8JsonReader reader) {
      return child(reader.readString());
    }

    private static Child child(String text) {
      Child value = new Child();
      value.value = text;
      return value;
    }
  }

  public static final class ChildCodecB extends ChildCodecA {}

  public static final class StatefulChildCodec extends ChildCodecA {
    private final String prefix;

    public StatefulChildCodec(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public void writeString(StringJsonWriter writer, Child value) {
      writer.writeString(prefix + value.value);
    }

    @Override
    public void writeUtf8(Utf8JsonWriter writer, Child value) {
      writer.writeString(prefix + value.value);
    }

    @Override
    public Child readLatin1(Latin1JsonReader reader) {
      return childWithoutPrefix(reader.readString());
    }

    @Override
    public Child readUtf16(Utf16JsonReader reader) {
      return childWithoutPrefix(reader.readString());
    }

    @Override
    public Child readUtf8(Utf8JsonReader reader) {
      return childWithoutPrefix(reader.readString());
    }

    private Child childWithoutPrefix(String text) {
      Child value = new Child();
      value.value = text.substring(prefix.length());
      return value;
    }
  }

  public static final class Unrelated {}

  public static final class Box<T> {
    public T value;

    public Box() {}
  }

  @JsonSubTypes(
      value = @JsonSubTypes.Type(value = FixedValue.class, name = "fixed"),
      inclusion = JsonSubTypes.Inclusion.PROPERTY,
      property = "kind")
  public interface FixedBase {}

  public static final class FixedValue implements FixedBase {
    static final FixedValue INSTANCE = new FixedValue();

    private FixedValue() {}
  }

  public static final class FixedContainer {
    public FixedBase value;

    public FixedContainer() {}
  }

  @JsonType
  public static final class HostedAnnotatedModel {
    public int value;

    public HostedAnnotatedModel() {}
  }
}
