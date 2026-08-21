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
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.security.auth.Subject;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.json.annotation.JsonAnyGetter;
import org.apache.fory.json.annotation.JsonAnySetter;
import org.apache.fory.json.annotation.JsonSubTypes;
import org.apache.fory.json.annotation.JsonType;
import org.apache.fory.json.codec.AbstractJsonValueCodec;
import org.apache.fory.json.codec.DirectUnboxedValueCodec;
import org.apache.fory.json.codec.JsonObjectModel;
import org.apache.fory.json.codec.JsonValueCodec;
import org.apache.fory.json.codec.ObjectCodec;
import org.apache.fory.json.codec.TransparentUnboxedValueCodec;
import org.apache.fory.json.codec.UnboxedValueCodec;
import org.apache.fory.json.codegen.JsonCodegen;
import org.apache.fory.json.data.PublicFields;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.reader.Latin1JsonReader;
import org.apache.fory.json.reader.Utf16JsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonSharedRegistry;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.meta.TypeExtMeta;
import org.apache.fory.platform.JdkVersion;
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
  public void rawWriteNullVersionsReaders() {
    ForyJson first = ForyJson.builder().withAsyncCompilation(false).build();
    ForyJson second = ForyJson.builder().writeNullFields(true).withAsyncCompilation(false).build();
    assertDifferentObjectClasses(
        JsonTestSupport.currentTypeResolver(first).getTypeInfo(Model.class, Model.class),
        JsonTestSupport.currentTypeResolver(second).getTypeInfo(Model.class, Model.class));
  }

  @Test
  public void inactiveAnyDirectionReusesClass() {
    JsonTypeInfo getterA = anyType(GetterAny.class, new ChildCodecA());
    JsonTypeInfo getterB = anyType(GetterAny.class, new ChildCodecB());
    assertNotSame(getterA.stringWriter().getClass(), getterB.stringWriter().getClass());
    assertNotSame(getterA.utf8Writer().getClass(), getterB.utf8Writer().getClass());
    assertSame(getterA.latin1Reader().getClass(), getterB.latin1Reader().getClass());
    assertSame(getterA.utf16Reader().getClass(), getterB.utf16Reader().getClass());
    assertSame(getterA.utf8Reader().getClass(), getterB.utf8Reader().getClass());

    JsonTypeInfo setterA = anyType(SetterAny.class, new ChildCodecA());
    JsonTypeInfo setterB = anyType(SetterAny.class, new ChildCodecB());
    assertSame(setterA.stringWriter().getClass(), setterB.stringWriter().getClass());
    assertSame(setterA.utf8Writer().getClass(), setterB.utf8Writer().getClass());
    assertNotSame(setterA.latin1Reader().getClass(), setterB.latin1Reader().getClass());
    assertNotSame(setterA.utf16Reader().getClass(), setterB.utf16Reader().getClass());
    assertNotSame(setterA.utf8Reader().getClass(), setterB.utf8Reader().getClass());
  }

  @Test
  public void terminalDirectMethodsVersionProjection() throws Exception {
    JsonTypeInfo first = directTerminal(new VariableDirectCodec(false));
    JsonTypeInfo second = directTerminal(new VariableDirectCodec(true));
    ProjectionTransparentCodec firstCodec = new ProjectionTransparentCodec(first);
    ProjectionTransparentCodec secondCodec = new ProjectionTransparentCodec(second);

    assertNotEquals(unboxedProjection(firstCodec, false), unboxedProjection(secondCodec, false));
    assertNotEquals(unboxedProjection(firstCodec, true), unboxedProjection(secondCodec, true));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void hostedSiblingCodecUsesInterface() throws Exception {
    String packageName = "org.apache.fory.json.sibling";
    Path targetOutput = Files.createTempDirectory("fory-json-sibling-target");
    compileSource(
        targetOutput,
        packageName,
        "SiblingModel",
        "public final class SiblingModel { public " + Child.class.getCanonicalName() + " child; }");
    Path codecOutput = Files.createTempDirectory("fory-json-sibling-codec");
    compileSource(
        codecOutput,
        packageName,
        "SiblingChildCodec",
        "public final class SiblingChildCodec extends "
            + ChildCodecA.class.getCanonicalName()
            + " {}");
    try (URLClassLoader targetLoader =
            new URLClassLoader(
                new URL[] {targetOutput.toUri().toURL()}, getClass().getClassLoader());
        URLClassLoader codecLoader =
            new URLClassLoader(
                new URL[] {codecOutput.toUri().toURL()}, getClass().getClassLoader())) {
      Class<?> target = Class.forName(packageName + ".SiblingModel", true, targetLoader);
      Class<?> codecType = Class.forName(packageName + ".SiblingChildCodec", true, codecLoader);
      JsonValueCodec<Child> codec =
          (JsonValueCodec<Child>) codecType.getDeclaredConstructor().newInstance();
      ForyJson configured = parentJson(codec);
      JsonTypeResolver resolver = hostedResolver(configured);
      List<ObjectCodec<?>> models = resolver.generateHostedCodecs(target);
      assertTrue(models.stream().anyMatch(model -> model.type() == target));
    }
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void hostedConcealedTypeUsesInterpretedRole() throws Exception {
    if (JdkVersion.MAJOR_VERSION < 9) {
      return;
    }
    String packageName = "org.apache.fory.json.concealed";
    Path targetOutput = Files.createTempDirectory("fory-json-concealed-target");
    compileSource(
        targetOutput,
        packageName,
        "ConcealedModel",
        "public final class ConcealedModel { public jdk.internal.misc.Unsafe value; }",
        "--add-exports",
        "java.base/jdk.internal.misc=ALL-UNNAMED");
    try (URLClassLoader targetLoader =
        new URLClassLoader(new URL[] {targetOutput.toUri().toURL()}, getClass().getClassLoader())) {
      Class<?> target = Class.forName(packageName + ".ConcealedModel", true, targetLoader);
      Class<?> concealed = Class.forName("jdk.internal.misc.Unsafe");
      ForyJson configured =
          ForyJson.builder().registerCodec((Class) concealed, JsonTestSupport.nullCodec()).build();
      JsonTypeResolver resolver = hostedResolver(configured);
      resolver.generateHostedCodecs(target);
      assertInterpretedObject(resolver.getTypeInfo((Class) target, target));
    }
  }

  @Test
  public void hostedBootstrapPackageNeedsOwner() throws Exception {
    Method method =
        JsonCodegen.class.getDeclaredMethod("hostedDefinitionOwner", Class.class, String.class);
    method.setAccessible(true);
    assertNull(method.invoke(null, Subject.class, CodeGenerator.getPackage(Subject.class)));
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void hostedTransparentTerminalUsesInterpretedRole() throws Exception {
    String packageName = "org.apache.fory.json.terminal";
    Path terminalOutput = Files.createTempDirectory("fory-json-terminal");
    compileSource(
        terminalOutput,
        packageName,
        "SiblingTerminal",
        "public final class SiblingTerminal implements "
            + ProjectionCarrier.class.getCanonicalName()
            + " {}");
    try (URLClassLoader terminalLoader =
        new URLClassLoader(
            new URL[] {terminalOutput.toUri().toURL()}, getClass().getClassLoader())) {
      Class<?> terminal = Class.forName(packageName + ".SiblingTerminal", true, terminalLoader);
      JsonObjectModel model = transparentModel();
      ForyJson configured =
          ForyJson.builder()
              .registerCodec((Class) terminal, JsonTestSupport.nullCodec())
              .registerCodec(
                  ProjectionValue.class,
                  (type, resolver, runtimeType) ->
                      new SiblingTransparentCodec(resolver.getTypeInfo((Class) terminal, terminal)))
              .registerCodec(
                  TransparentModel.class,
                  (type, resolver, runtimeType) -> resolver.createObjectCodec(type, model))
              .build();
      JsonTypeResolver resolver = hostedResolver(configured);
      resolver.generateHostedCodecs(TransparentModel.class);
      assertInterpretedObject(resolver.getTypeInfo(TransparentModel.class, TransparentModel.class));
    }
  }

  @Test
  public void concurrentInstancesShareFirstClass() throws Exception {
    ForyJson first = ForyJson.builder().withAsyncCompilation(false).build();
    ForyJson second = ForyJson.builder().withAsyncCompilation(false).build();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<JsonTypeInfo> firstType =
          executor.submit(
              () -> {
                start.await();
                return JsonTestSupport.currentTypeResolver(first)
                    .getTypeInfo(Model.class, Model.class);
              });
      Future<JsonTypeInfo> secondType =
          executor.submit(
              () -> {
                start.await();
                return JsonTestSupport.currentTypeResolver(second)
                    .getTypeInfo(Model.class, Model.class);
              });
      start.countDown();
      assertObjectClasses(firstType.get(), secondType.get());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void cacheResetAllowsEquivalentClass() {
    ForyJson first = ForyJson.builder().withAsyncCompilation(false).build();
    JsonTypeInfo firstType =
        JsonTestSupport.currentTypeResolver(first).getTypeInfo(Model.class, Model.class);
    JsonCodegen.resetGeneratedClassCache();
    ForyJson second = ForyJson.builder().withAsyncCompilation(false).build();
    JsonTypeInfo secondType =
        JsonTestSupport.currentTypeResolver(second).getTypeInfo(Model.class, Model.class);
    assertDifferentObjectClasses(firstType, secondType);

    Model value = new Model();
    value.value = "retained";
    assertEquals(first.fromJson(first.toJson(value), Model.class).value, "retained");
    assertEquals(second.fromJson(second.toJson(value), Model.class).value, "retained");
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
  private static JsonTypeInfo anyType(Class<?> type, JsonValueCodec<Child> codec) {
    ForyJson json = parentJson(codec);
    return JsonTestSupport.currentTypeResolver(json).getTypeInfo((Class) type, type);
  }

  private static JsonTypeResolver hostedResolver(ForyJson json) throws Exception {
    Constructor<JsonSharedRegistry> constructor =
        JsonSharedRegistry.class.getDeclaredConstructor(
            JsonConfig.class, ExecutorService.class, boolean.class);
    constructor.setAccessible(true);
    return new JsonTypeResolver(constructor.newInstance(json.config(), null, true));
  }

  private static JsonTypeInfo directTerminal(VariableDirectCodec codec) {
    JsonCodecFactory factory =
        (type, resolver, runtimeType) ->
            type.getRawType() == int.class
                    && type.getTypeExtMeta() != null
                    && type.getTypeExtMeta().typeId() == Types.UINT32
                ? codec
                : null;
    ForyJson json =
        ForyJson.builder().withModule(context -> context.registerCodecFactory(factory)).build();
    return JsonTestSupport.currentTypeResolver(json)
        .getTypeInfo(TypeRef.of(int.class, TypeExtMeta.of(Types.UINT32, false, false)));
  }

  private static List<Object> unboxedProjection(UnboxedValueCodec codec, boolean reader)
      throws Exception {
    Method method =
        JsonTypeResolver.class.getDeclaredMethod(
            "addUnboxedProjection",
            UnboxedValueCodec.class,
            boolean.class,
            ArrayList.class,
            ArrayList.class);
    method.setAccessible(true);
    ArrayList<Object> projection = new ArrayList<>();
    method.invoke(null, codec, reader, projection, new ArrayList<Class<?>>());
    return projection;
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

  private static void compileSource(
      Path output, String packageName, String simpleName, String declaration) throws IOException {
    compileSource(output, packageName, simpleName, declaration, new String[0]);
  }

  private static void compileSource(
      Path output, String packageName, String simpleName, String declaration, String... options)
      throws IOException {
    Path source = output.resolve(simpleName + ".java");
    Files.write(
        source, ("package " + packageName + "; " + declaration).getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    ArrayList<String> arguments = new ArrayList<>();
    Collections.addAll(
        arguments, "-proc:none", "-classpath", System.getProperty("java.class.path"));
    Collections.addAll(arguments, options);
    Collections.addAll(arguments, "-d", output.toString(), source.toString());
    assertEquals(compiler.run(null, null, null, arguments.toArray(new String[0])), 0);
  }

  private static JsonObjectModel transparentModel() throws Exception {
    TypeRef<?> logicalType = TypeRef.of(ProjectionValue.class, ordinary(false));
    return new JsonObjectModel(
        TransparentModel.class.getConstructor(),
        null,
        new String[0],
        new Method[0],
        new Method[0],
        new int[0],
        new boolean[0],
        new TypeRef<?>[0],
        new String[] {"value"},
        new Method[] {TransparentModel.class.getMethod("getValue")},
        new Method[] {TransparentModel.class.getMethod("setValue", ProjectionCarrier.class)},
        new TypeRef<?>[] {logicalType});
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

  private static void assertInterpretedObject(JsonTypeInfo typeInfo) {
    assertTrue(typeInfo.stringWriter() instanceof ObjectCodec<?>);
    assertTrue(typeInfo.utf8Writer() instanceof ObjectCodec<?>);
    assertTrue(typeInfo.latin1Reader() instanceof ObjectCodec<?>);
    assertTrue(typeInfo.utf16Reader() instanceof ObjectCodec<?>);
    assertTrue(typeInfo.utf8Reader() instanceof ObjectCodec<?>);
  }

  public static final class Model {
    public String value;

    public Model() {}
  }

  public static final class Parent {
    public Child child;

    public Parent() {}
  }

  public static final class GetterAny {
    private final Map<String, Child> values = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Child> values() {
      return values;
    }
  }

  public static final class SetterAny {
    private final Map<String, Child> values = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String name, Child value) {
      values.put(name, value);
    }
  }

  public static final class Child {
    public String value;

    public Child() {}
  }

  public interface ProjectionCarrier {}

  public static final class ProjectionValue {}

  public static final class TransparentModel {
    private ProjectionCarrier value;

    public TransparentModel() {}

    public ProjectionCarrier getValue() {
      return value;
    }

    public void setValue(ProjectionCarrier value) {
      this.value = value;
    }
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

  public static class VariableDirectCodec extends AbstractJsonValueCodec<Integer>
      implements DirectUnboxedValueCodec {
    private final boolean alternate;

    public VariableDirectCodec(boolean alternate) {
      this.alternate = alternate;
    }

    @Override
    public void write(JsonWriter writer, Integer value) {
      writer.writeInt(value);
    }

    @Override
    public Integer read(JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public Class<?> carrierType() {
      return int.class;
    }

    @Override
    public Object readLatin1Carrier(Latin1JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public Object readUtf16Carrier(Utf16JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public Object readUtf8Carrier(Utf8JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public void writeStringCarrier(StringJsonWriter writer, Object carrier) {
      writer.writeInt((Integer) carrier);
    }

    @Override
    public void writeUtf8Carrier(Utf8JsonWriter writer, Object carrier) {
      writer.writeInt((Integer) carrier);
    }

    @Override
    public Method readCarrierMethod() {
      return method(alternate ? "readSecond" : "readFirst", JsonReader.class);
    }

    @Override
    public Method writeCarrierMethod() {
      return method(alternate ? "writeSecond" : "writeFirst", JsonWriter.class, int.class);
    }

    public static int readFirst(JsonReader reader) {
      return reader.readInt();
    }

    public static int readSecond(JsonReader reader) {
      return reader.readInt();
    }

    public static void writeFirst(JsonWriter writer, int value) {
      writer.writeInt(value);
    }

    public static void writeSecond(JsonWriter writer, int value) {
      writer.writeInt(value);
    }

    private static Method method(String name, Class<?>... parameters) {
      try {
        return VariableDirectCodec.class.getMethod(name, parameters);
      } catch (NoSuchMethodException e) {
        throw new AssertionError(e);
      }
    }
  }

  public static final class ProjectionTransparentCodec extends AbstractJsonValueCodec<Integer>
      implements TransparentUnboxedValueCodec {
    private final JsonTypeInfo valueTypeInfo;

    public ProjectionTransparentCodec(JsonTypeInfo valueTypeInfo) {
      this.valueTypeInfo = valueTypeInfo;
    }

    @Override
    public JsonTypeInfo valueTypeInfo() {
      return valueTypeInfo;
    }

    @Override
    public Object constructCarrier(JsonReader reader, Object value) {
      return value;
    }

    @Override
    public Object extractValue(Object carrier) {
      return carrier;
    }

    @Override
    public Method[] constructMethods() {
      return new Method[0];
    }

    @Override
    public int[] constructBoxBytes() {
      return new int[0];
    }

    @Override
    public Method[] extractMethods() {
      return new Method[0];
    }

    @Override
    public void write(JsonWriter writer, Integer value) {
      writer.writeInt(value);
    }

    @Override
    public Integer read(JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public Class<?> carrierType() {
      return int.class;
    }

    @Override
    public Object readLatin1Carrier(Latin1JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public Object readUtf16Carrier(Utf16JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public Object readUtf8Carrier(Utf8JsonReader reader) {
      return reader.readInt();
    }

    @Override
    public void writeStringCarrier(StringJsonWriter writer, Object carrier) {
      writer.writeInt((Integer) carrier);
    }

    @Override
    public void writeUtf8Carrier(Utf8JsonWriter writer, Object carrier) {
      writer.writeInt((Integer) carrier);
    }
  }

  public static final class SiblingTransparentCodec extends AbstractJsonValueCodec<ProjectionValue>
      implements TransparentUnboxedValueCodec {
    private final JsonTypeInfo valueTypeInfo;

    public SiblingTransparentCodec(JsonTypeInfo valueTypeInfo) {
      this.valueTypeInfo = valueTypeInfo;
    }

    @Override
    public JsonTypeInfo valueTypeInfo() {
      return valueTypeInfo;
    }

    @Override
    public Object constructCarrier(JsonReader reader, Object value) {
      return construct(value);
    }

    @Override
    public Object extractValue(Object carrier) {
      return extract((ProjectionCarrier) carrier);
    }

    @Override
    public Method[] constructMethods() {
      return new Method[] {method("construct", Object.class)};
    }

    @Override
    public int[] constructBoxBytes() {
      return new int[] {0};
    }

    @Override
    public Method[] extractMethods() {
      return new Method[] {method("extract", ProjectionCarrier.class)};
    }

    @Override
    public void write(JsonWriter writer, ProjectionValue value) {
      writer.writeNull();
    }

    @Override
    public ProjectionValue read(JsonReader reader) {
      return null;
    }

    @Override
    public Class<?> carrierType() {
      return ProjectionCarrier.class;
    }

    @Override
    public Object readLatin1Carrier(Latin1JsonReader reader) {
      return null;
    }

    @Override
    public Object readUtf16Carrier(Utf16JsonReader reader) {
      return null;
    }

    @Override
    public Object readUtf8Carrier(Utf8JsonReader reader) {
      return null;
    }

    @Override
    public void writeStringCarrier(StringJsonWriter writer, Object carrier) {
      writer.writeNull();
    }

    @Override
    public void writeUtf8Carrier(Utf8JsonWriter writer, Object carrier) {
      writer.writeNull();
    }

    public static ProjectionCarrier construct(Object value) {
      return (ProjectionCarrier) value;
    }

    public static Object extract(ProjectionCarrier carrier) {
      return carrier;
    }

    private static Method method(String name, Class<?>... parameterTypes) {
      try {
        return SiblingTransparentCodec.class.getMethod(name, parameterTypes);
      } catch (NoSuchMethodException e) {
        throw new AssertionError(e);
      }
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
