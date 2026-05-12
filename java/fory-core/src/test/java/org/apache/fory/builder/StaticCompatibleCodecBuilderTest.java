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

package org.apache.fory.builder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.apache.fory.Fory;
import org.apache.fory.builder.Generated.GeneratedCompatibleMetaSharedSerializer;
import org.apache.fory.context.MetaReadContext;
import org.apache.fory.context.MetaWriteContext;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class StaticCompatibleCodecBuilderTest {
  @DataProvider
  public static Object[][] xlangModes() {
    return new Object[][] {{false}, {true}};
  }

  @Test(dataProvider = "xlangModes")
  public void testStaticCompatibleSerializerReadsRuntimeRemoteTypeDef(boolean xlang)
      throws Exception {
    CompilationResult writerResult =
        compile(
            "test.StaticCompatiblePayload",
            "package test;\n"
                + "public class StaticCompatiblePayload {\n"
                + "  public int id;\n"
                + "  public int legacy;\n"
                + "  public String name;\n"
                + "  public StaticCompatiblePayload() {}\n"
                + "}\n");
    CompilationResult readerResult =
        compile(
            "test.StaticCompatiblePayload",
            "package test;\n"
                + "public class StaticCompatiblePayload {\n"
                + "  public int id;\n"
                + "  public String added = \"default\";\n"
                + "  public String name;\n"
                + "  public StaticCompatiblePayload() {}\n"
                + "}\n");
    Assert.assertTrue(writerResult.success, writerResult.diagnostics());
    Assert.assertTrue(readerResult.success, readerResult.diagnostics());
    try (URLClassLoader writerLoader = writerResult.classLoader();
        URLClassLoader readerLoader = readerResult.classLoader()) {
      Class<?> writerType = writerLoader.loadClass("test.StaticCompatiblePayload");
      Class<?> readerType = readerLoader.loadClass("test.StaticCompatiblePayload");
      Fory writer = compatibleFory(writerLoader, writerType, xlang, "writer");
      Fory reader = compatibleFory(readerLoader, readerType, xlang, "reader");
      Object writerValue = writerType.getConstructor().newInstance();
      setField(writerType, writerValue, "id", 42);
      setField(writerType, writerValue, "legacy", 99);
      setField(writerType, writerValue, "name", xlang ? "xlang" : "native");

      Object result =
          roundTripThroughStaticCompatibleSerializer(
              writer, reader, writerType, readerType, writerValue);
      Assert.assertSame(result.getClass(), readerType);
      Assert.assertEquals(getField(readerType, result, "id"), 42);
      Assert.assertEquals(getField(readerType, result, "added"), "default");
      Assert.assertEquals(getField(readerType, result, "name"), xlang ? "xlang" : "native");
    }
  }

  @Test
  public void testStaticCompatibleRecordSerializerConvertsRemoteField() throws Exception {
    assumeRecordSupport();
    CompilationResult writerResult =
        compile(
            "test.StaticCompatibleRecordPayload",
            "package test;\n"
                + "public class StaticCompatibleRecordPayload {\n"
                + "  public String id;\n"
                + "  public StaticCompatibleRecordPayload() {}\n"
                + "}\n");
    CompilationResult readerResult =
        compile(
            "test.StaticCompatibleRecordPayload",
            "package test;\n" + "public record StaticCompatibleRecordPayload(int id) {}\n");
    Assert.assertTrue(writerResult.success, writerResult.diagnostics());
    Assert.assertTrue(readerResult.success, readerResult.diagnostics());
    try (URLClassLoader writerLoader = writerResult.classLoader();
        URLClassLoader readerLoader = readerResult.classLoader()) {
      Class<?> writerType = writerLoader.loadClass("test.StaticCompatibleRecordPayload");
      Class<?> readerType = readerLoader.loadClass("test.StaticCompatibleRecordPayload");
      Fory writer = compatibleFory(writerLoader, writerType, false, "record-writer");
      Fory reader = compatibleFory(readerLoader, readerType, false, "record-reader");
      Object writerValue = writerType.getConstructor().newInstance();
      setField(writerType, writerValue, "id", "73");

      Object result =
          roundTripThroughStaticCompatibleSerializer(
              writer, reader, writerType, readerType, writerValue);
      Assert.assertSame(result.getClass(), readerType);
      Assert.assertEquals(invoke(readerType, result, "id"), 73);
    }
  }

  @Test
  public void testStaticCompatibleSerializerUsesListArrayAction() throws Exception {
    CompilationResult writerResult =
        compile(
            "test.StaticCompatibleArrayPayload",
            "package test;\n"
                + "import org.apache.fory.annotation.Int32Type;\n"
                + "import org.apache.fory.collection.Int32List;\n"
                + "import org.apache.fory.config.Int32Encoding;\n"
                + "public class StaticCompatibleArrayPayload {\n"
                + "  @Int32Type(encoding = Int32Encoding.FIXED) public Int32List values;\n"
                + "  public StaticCompatibleArrayPayload() {}\n"
                + "}\n");
    CompilationResult readerResult =
        compile(
            "test.StaticCompatibleArrayPayload",
            "package test;\n"
                + "public class StaticCompatibleArrayPayload {\n"
                + "  public int[] values;\n"
                + "  public StaticCompatibleArrayPayload() {}\n"
                + "}\n");
    Assert.assertTrue(writerResult.success, writerResult.diagnostics());
    Assert.assertTrue(readerResult.success, readerResult.diagnostics());
    try (URLClassLoader writerLoader = writerResult.classLoader();
        URLClassLoader readerLoader = readerResult.classLoader()) {
      Class<?> writerType = writerLoader.loadClass("test.StaticCompatibleArrayPayload");
      Class<?> readerType = readerLoader.loadClass("test.StaticCompatibleArrayPayload");
      Fory writer = compatibleFory(writerLoader, writerType, true, "array-writer");
      Fory reader = compatibleFory(readerLoader, readerType, true, "array-reader");
      Object writerValue = writerType.getConstructor().newInstance();
      setField(
          writerType,
          writerValue,
          "values",
          new org.apache.fory.collection.Int32List(new int[] {4, 5, 6}));

      Object result =
          roundTripThroughStaticCompatibleSerializer(
              writer, reader, writerType, readerType, writerValue);
      Assert.assertSame(result.getClass(), readerType);
      Assert.assertTrue(
          Arrays.equals((int[]) getField(readerType, result, "values"), new int[] {4, 5, 6}));
    }
  }

  private static Object roundTripThroughStaticCompatibleSerializer(
      Fory writer, Fory reader, Class<?> writerType, Class<?> readerType, Object writerValue)
      throws Exception {
    TypeDef remoteTypeDef = TypeDef.buildTypeDef(writer.getTypeResolver(), writerType);
    Assert.assertNotEquals(
        remoteTypeDef.getId(), TypeDef.buildTypeDef(reader.getTypeResolver(), readerType).getId());
    Class<Object> readerClass = cast(readerType);
    Class<? extends Serializer> compatibleSerializerClass =
        CodecUtils.loadOrGenStaticCompatibleCodecClass(
            reader.getTypeResolver(), readerClass, remoteTypeDef);
    Assert.assertTrue(
        GeneratedCompatibleMetaSharedSerializer.class.isAssignableFrom(compatibleSerializerClass));
    Serializer<Object> compatibleSerializer =
        compatibleSerializerClass
            .getConstructor(TypeResolver.class, Class.class, TypeDef.class)
            .newInstance(reader.getTypeResolver(), readerClass, remoteTypeDef);
    Assert.assertThrows(
        UnsupportedOperationException.class,
        () -> compatibleSerializer.write(reader.getWriteContext(), null));

    writer.setMetaWriteContext(new MetaWriteContext());
    byte[] bytes = writer.serialize(writerValue);
    reader.setMetaReadContext(new MetaReadContext());
    try (MockedStatic<CodecUtils> codecUtils =
        Mockito.mockStatic(CodecUtils.class, Mockito.CALLS_REAL_METHODS)) {
      codecUtils
          .when(
              () ->
                  CodecUtils.loadOrGenMetaSharedCodecClass(
                      same(reader.getTypeResolver()), eq(readerClass), any(TypeDef.class)))
          .thenReturn(compatibleSerializerClass);
      Object result = reader.deserialize(bytes);
      codecUtils.verify(
          () ->
              CodecUtils.loadOrGenMetaSharedCodecClass(
                  same(reader.getTypeResolver()), eq(readerClass), any(TypeDef.class)),
          atLeastOnce());
      return result;
    }
  }

  private static Fory compatibleFory(
      ClassLoader classLoader, Class<?> type, boolean xlang, String role) {
    Fory fory =
        Fory.builder()
            .withName("static-compatible-" + role + "-" + (xlang ? "xlang" : "native"))
            .withClassLoader(classLoader)
            .withXlang(xlang)
            .withCodegen(true)
            .withMetaShare(true)
            .withScopedMetaShare(false)
            .withCompatible(true)
            .requireClassRegistration(false)
            .build();
    if (xlang) {
      fory.register(type, "test", type.getSimpleName());
    }
    return fory;
  }

  @SuppressWarnings("unchecked")
  private static Class<Object> cast(Class<?> type) {
    return (Class<Object>) type;
  }

  private static CompilationResult compile(String typeName, String source) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Assert.assertNotNull(compiler, "Tests require a JDK compiler");
    Path root = Files.createTempDirectory("fory-static-compatible-test");
    Path sourceRoot = root.resolve("src");
    Path classRoot = root.resolve("classes");
    Files.createDirectories(sourceRoot);
    Files.createDirectories(classRoot);
    Path sourceFile = sourceRoot.resolve(typeName.replace('.', '/') + ".java");
    Files.createDirectories(sourceFile.getParent());
    Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> units =
          fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(sourceFile.toFile()));
      List<String> options =
          Arrays.asList(
              "-classpath",
              System.getProperty("java.class.path"),
              "-d",
              classRoot.toString());
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, diagnostics, options, null, units);
      return new CompilationResult(classRoot, task.call(), diagnostics.getDiagnostics());
    }
  }

  private static void assumeRecordSupport() {
    if (javaSpecificationVersion() < 16) {
      throw new SkipException("Record source tests require JDK 16 or newer");
    }
  }

  private static int javaSpecificationVersion() {
    String version = System.getProperty("java.specification.version");
    if (version.startsWith("1.")) {
      version = version.substring(2);
    }
    int dotIndex = version.indexOf('.');
    if (dotIndex >= 0) {
      version = version.substring(0, dotIndex);
    }
    return Integer.parseInt(version);
  }

  private static void setField(Class<?> type, Object target, String name, Object value)
      throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Object getField(Class<?> type, Object target, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }

  private static Object invoke(Class<?> type, Object target, String name) throws Exception {
    java.lang.reflect.Method method = type.getDeclaredMethod(name);
    method.setAccessible(true);
    return method.invoke(target);
  }

  private static final class CompilationResult {
    final Path classRoot;
    final boolean success;
    final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    CompilationResult(
        Path classRoot, boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
      this.classRoot = classRoot;
      this.success = success;
      this.diagnostics = new ArrayList<>(diagnostics);
    }

    URLClassLoader classLoader() throws IOException {
      URL[] urls = {classRoot.toUri().toURL()};
      return new URLClassLoader(urls, StaticCompatibleCodecBuilderTest.class.getClassLoader());
    }

    String diagnostics() {
      StringBuilder builder = new StringBuilder();
      for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
        builder.append(diagnostic).append('\n');
      }
      return builder.toString();
    }
  }
}
