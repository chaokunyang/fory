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

package org.apache.fory.annotation.processing;

import java.io.IOException;
import java.lang.reflect.Constructor;
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
import org.apache.fory.builder.CodecUtils;
import org.apache.fory.builder.Generated.GeneratedCompatibleMetaSharedSerializer;
import org.apache.fory.context.MetaReadContext;
import org.apache.fory.context.MetaWriteContext;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.serializer.MetaSharedSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.StaticGeneratedStructSerializer;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.Types;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ForyStructProcessorTest {
  @Test
  public void testStaticSerializerSelectedWithCodegenDisabled() throws Exception {
    CompilationResult result =
        compile(
            "test.SimpleStruct",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "@ForyStruct public class SimpleStruct {\n"
                + "  public int id;\n"
                + "  public String name;\n"
                + "  public SimpleStruct() {}\n"
                + "}\n");
    Assert.assertTrue(result.success, result.diagnostics());
    try (URLClassLoader loader = result.classLoader()) {
      Class<?> type = loader.loadClass("test.SimpleStruct");
      Class<?> serializerType = loader.loadClass("test.SimpleStruct__ForyStaticSerializer__");
      Assert.assertTrue(StaticGeneratedStructSerializer.class.isAssignableFrom(serializerType));

      Object value = type.getConstructor().newInstance();
      setField(type, value, "id", 7);
      setField(type, value, "name", "fory");

      Fory fory =
          Fory.builder()
              .withClassLoader(loader)
              .withCodegen(false)
              .requireClassRegistration(false)
              .build();
      Object serializer = fory.getTypeResolver().getTypeInfo(type).getSerializer();
      Assert.assertEquals(serializer.getClass().getName(), serializerType.getName());
      Object roundTrip = fory.deserialize(fory.serialize(value));
      Assert.assertEquals(getField(type, roundTrip, "id"), 7);
      Assert.assertEquals(getField(type, roundTrip, "name"), "fory");
      Object copied = fory.copy(value);
      Assert.assertEquals(getField(type, copied, "id"), 7);
      Assert.assertEquals(getField(type, copied, "name"), "fory");
    }
  }

  @Test
  public void testPrivateFieldUsesAccessibleAccessors() throws Exception {
    CompilationResult result =
        compile(
            "test.PrivateStruct",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "@ForyStruct public class PrivateStruct {\n"
                + "  private int id;\n"
                + "  private String name;\n"
                + "  public PrivateStruct() {}\n"
                + "  int getId() { return id; }\n"
                + "  void setId(int id) { this.id = id; }\n"
                + "  protected String getName() { return name; }\n"
                + "  protected void setName(String name) { this.name = name; }\n"
                + "}\n");
    Assert.assertTrue(result.success, result.diagnostics());
    try (URLClassLoader loader = result.classLoader()) {
      Class<?> type = loader.loadClass("test.PrivateStruct");
      Object value = type.getConstructor().newInstance();
      invoke(type, value, "setId", int.class, 8);
      invoke(type, value, "setName", String.class, "static");
      Fory fory =
          Fory.builder()
              .withClassLoader(loader)
              .withCodegen(false)
              .requireClassRegistration(false)
              .build();
      Object roundTrip = fory.deserialize(fory.serialize(value));
      Assert.assertEquals(invoke(type, roundTrip, "getId"), 8);
      Assert.assertEquals(invoke(type, roundTrip, "getName"), "static");
    }
  }

  @Test
  public void testPrivateFieldWithoutAccessorsFailsCompilation() throws Exception {
    CompilationResult result =
        compile(
            "test.BadStruct",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "@ForyStruct public class BadStruct {\n"
                + "  private int id;\n"
                + "  public BadStruct() {}\n"
                + "}\n");
    Assert.assertFalse(result.success);
    Assert.assertTrue(result.diagnostics().contains("getter/setter"), result.diagnostics());
  }

  @Test
  public void testPrivateStructFailsCompilation() throws Exception {
    CompilationResult result =
        compile(
            "test.PrivateOwner",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "public class PrivateOwner {\n"
                + "  @ForyStruct private static class HiddenStruct {\n"
                + "    int id;\n"
                + "    HiddenStruct() {}\n"
                + "  }\n"
                + "}\n");
    Assert.assertFalse(result.success);
    Assert.assertTrue(result.diagnostics().contains("must not be private"), result.diagnostics());
  }

  @Test
  public void testDuplicateForyFieldIdFailsCompilation() throws Exception {
    CompilationResult result =
        compile(
            "test.DuplicateIdStruct",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyField;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "@ForyStruct public class DuplicateIdStruct {\n"
                + "  @ForyField(id = 1) public int left;\n"
                + "  @ForyField(id = 1) public int right;\n"
                + "  public DuplicateIdStruct() {}\n"
                + "}\n");
    Assert.assertFalse(result.success);
    Assert.assertTrue(
        result.diagnostics().contains("Duplicate @ForyField id 1"), result.diagnostics());
  }

  @Test
  public void testInnerTypeGeneratedAsTopLevelBinaryTail() throws Exception {
    CompilationResult result =
        compile(
            "test.Outer",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "public class Outer {\n"
                + "  @ForyStruct public static class Inner {\n"
                + "    public int id;\n"
                + "    public Inner() {}\n"
                + "  }\n"
                + "}\n");
    Assert.assertTrue(result.success, result.diagnostics());
    try (URLClassLoader loader = result.classLoader()) {
      Class<?> serializer = loader.loadClass("test.Outer$Inner__ForyStaticSerializer__");
      Assert.assertTrue(StaticGeneratedStructSerializer.class.isAssignableFrom(serializer));
    }
  }

  @Test
  public void testGeneratedNameCollisionFailsCompilation() throws Exception {
    CompilationResult result =
        compile(
            "test.Outer",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "public class Outer {\n"
                + "  @ForyStruct public static class Inner {\n"
                + "    public int id;\n"
                + "    public Inner() {}\n"
                + "  }\n"
                + "}\n"
                + "class Outer$Inner__ForyStaticSerializer__ {}\n");
    Assert.assertFalse(result.success);
    Assert.assertTrue(result.diagnostics().contains("collides"), result.diagnostics());
  }

  @Test
  public void testGeneratedDescriptorsCarryNestedTypeMetadata() throws Exception {
    CompilationResult result =
        compile(
            "test.MetadataStruct",
            "package test;\n"
                + "import java.util.List;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "import org.apache.fory.annotation.Ref;\n"
                + "import org.apache.fory.annotation.UInt16Type;\n"
                + "@ForyStruct public class MetadataStruct {\n"
                + "  public List<@Ref String> names;\n"
                + "  public @UInt16Type int code;\n"
                + "  public MetadataStruct() {}\n"
                + "}\n");
    Assert.assertTrue(result.success, result.diagnostics());
    try (URLClassLoader loader = result.classLoader()) {
      Class<?> type = loader.loadClass("test.MetadataStruct");
      Class<?> serializerType = loader.loadClass("test.MetadataStruct__ForyStaticSerializer__");
      Fory fory =
          Fory.builder()
              .withClassLoader(loader)
              .withCodegen(false)
              .requireClassRegistration(false)
              .build();
      StaticGeneratedStructSerializer<?> serializer =
          (StaticGeneratedStructSerializer<?>)
              serializerType
                  .getConstructor(org.apache.fory.resolver.TypeResolver.class, Class.class)
                  .newInstance(fory.getTypeResolver(), type);
      Descriptor names = descriptor(serializer.getDescriptors(), "names");
      Assert.assertTrue(names.getTypeRef().hasExplicitTypeArguments());
      Assert.assertTrue(names.getTypeRef().getTypeArguments().get(0).hasTypeExtMeta());
      Assert.assertTrue(
          names.getTypeRef().getTypeArguments().get(0).getTypeExtMeta().trackingRef());
      Descriptor code = descriptor(serializer.getDescriptors(), "code");
      Assert.assertTrue(code.getTypeRef().hasTypeExtMeta());
      Assert.assertEquals(code.getTypeRef().getTypeExtMeta().typeId(), Types.UINT16);
    }
  }

  @Test
  public void testRecordReadAndCopyUseCanonicalConstructor() throws Exception {
    CompilationResult result =
        compile(
            "test.RecordStruct",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "import org.apache.fory.annotation.Ignore;\n"
                + "@ForyStruct public record RecordStruct(int id, String ignored, String name) {\n"
                + "  @Ignore public String ignored() { return ignored; }\n"
                + "}\n");
    Assert.assertTrue(result.success, result.diagnostics());
    String generatedSource =
        result.generatedSource("test/RecordStruct__ForyStaticSerializer__.java");
    Assert.assertTrue(generatedSource.contains("private void readCompatibleRecordField0("));
    Assert.assertTrue(generatedSource.contains("switch (matchedId)"));
    try (URLClassLoader loader = result.classLoader()) {
      Class<?> type = loader.loadClass("test.RecordStruct");
      Object value =
          type.getConstructor(int.class, String.class, String.class)
              .newInstance(5, "skip", "record");
      Fory fory =
          Fory.builder()
              .withClassLoader(loader)
              .withCodegen(false)
              .requireClassRegistration(false)
              .build();
      Object roundTrip = fory.deserialize(fory.serialize(value));
      Assert.assertEquals(invoke(type, roundTrip, "id"), 5);
      Assert.assertNull(invoke(type, roundTrip, "ignored"));
      Assert.assertEquals(invoke(type, roundTrip, "name"), "record");
      Object copied = fory.copy(value);
      Assert.assertEquals(invoke(type, copied, "id"), 5);
      Assert.assertNull(invoke(type, copied, "ignored"));
      Assert.assertEquals(invoke(type, copied, "name"), "record");
    }
  }

  @Test
  public void testCompatibleReadUsesGeneratedSerializer() throws Exception {
    CompilationResult writerResult =
        compile(
            "test.EvolvingStruct",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyField;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "@ForyStruct public class EvolvingStruct {\n"
                + "  @ForyField(id = 1) public int id;\n"
                + "  @ForyField(id = 2, nullable = true) public String name;\n"
                + "  public EvolvingStruct() {}\n"
                + "}\n");
    CompilationResult readerResult =
        compile(
            "test.EvolvingStruct",
            "package test;\n"
                + "import org.apache.fory.annotation.ForyField;\n"
                + "import org.apache.fory.annotation.ForyStruct;\n"
                + "@ForyStruct public class EvolvingStruct {\n"
                + "  @ForyField(id = 1) public int id;\n"
                + "  @ForyField(id = 2, nullable = true) public String name;\n"
                + "  @ForyField(id = 3, nullable = true) public String added = \"default\";\n"
                + "  public EvolvingStruct() {}\n"
                + "}\n");
    Assert.assertTrue(writerResult.success, writerResult.diagnostics());
    Assert.assertTrue(readerResult.success, readerResult.diagnostics());
    String generatedSource =
        readerResult.generatedSource("test/EvolvingStruct__ForyStaticSerializer__.java");
    Assert.assertTrue(
        generatedSource.contains(
            "readCompatibleField(readContext, value, remoteField, matchedId(remoteField))"));
    Assert.assertTrue(generatedSource.contains("private void readCompatibleField0("));
    Assert.assertTrue(generatedSource.contains("switch (matchedId)"));
    try (URLClassLoader writerLoader = writerResult.classLoader();
        URLClassLoader readerLoader = readerResult.classLoader()) {
      Class<?> writerType = writerLoader.loadClass("test.EvolvingStruct");
      Object value = writerType.getConstructor().newInstance();
      setField(writerType, value, "id", 42);
      setField(writerType, value, "name", "old");
      Fory writer =
          Fory.builder()
              .withClassLoader(writerLoader)
              .withCodegen(false)
              .withMetaShare(true)
              .withScopedMetaShare(false)
              .withCompatible(true)
              .requireClassRegistration(false)
              .build();
      writer.setMetaWriteContext(new MetaWriteContext());
      byte[] bytes = writer.serialize(value);

      Class<?> readerType = readerLoader.loadClass("test.EvolvingStruct");
      Fory reader =
          Fory.builder()
              .withClassLoader(readerLoader)
              .withCodegen(false)
              .withMetaShare(true)
              .withScopedMetaShare(false)
              .withCompatible(true)
              .requireClassRegistration(false)
              .build();
      reader.setMetaReadContext(new MetaReadContext());
      Object roundTrip = reader.deserialize(bytes);
      Assert.assertSame(roundTrip.getClass(), readerType);
      Assert.assertEquals(getField(readerType, roundTrip, "id"), 42);
      Assert.assertEquals(getField(readerType, roundTrip, "name"), "old");
      Assert.assertEquals(getField(readerType, roundTrip, "added"), "default");
      Object serializer = reader.getTypeResolver().getTypeInfo(readerType).getSerializer();
      Assert.assertTrue(serializer instanceof StaticGeneratedStructSerializer);
    }
  }

  @Test
  public void testGraalvmStaticCompatibleSerializerReadsRuntimeRemoteTypeDef() throws Exception {
    if (AndroidSupport.IS_ANDROID) {
      return;
    }
    CompilationResult writerResult =
        compile(
            "test.NativeImageStruct",
            "package test;\n"
                + "public class NativeImageStruct {\n"
                + "  public int id;\n"
                + "  public int legacy;\n"
                + "  public NativeImageStruct() {}\n"
                + "}\n");
    CompilationResult readerResult =
        compile(
            "test.NativeImageStruct",
            "package test;\n"
                + "public class NativeImageStruct {\n"
                + "  public int id;\n"
                + "  public String added = \"default\";\n"
                + "  public NativeImageStruct() {}\n"
                + "}\n");
    Assert.assertTrue(writerResult.success, writerResult.diagnostics());
    Assert.assertTrue(readerResult.success, readerResult.diagnostics());
    try (URLClassLoader writerLoader = writerResult.classLoader();
        URLClassLoader readerLoader = readerResult.classLoader()) {
      Fory writer =
          Fory.builder()
              .withClassLoader(writerLoader)
              .withCodegen(false)
              .withMetaShare(true)
              .withScopedMetaShare(false)
              .withCompatible(true)
              .requireClassRegistration(false)
              .build();
      Fory reader =
          Fory.builder()
              .withClassLoader(readerLoader)
              .withCodegen(true)
              .withMetaShare(true)
              .withScopedMetaShare(false)
              .withCompatible(true)
              .requireClassRegistration(false)
              .build();
      Class<?> writerType = writerLoader.loadClass("test.NativeImageStruct");
      Class<?> readerType = readerLoader.loadClass("test.NativeImageStruct");
      TypeDef remoteTypeDef = TypeDef.buildTypeDef(writer.getTypeResolver(), writerType);
      Assert.assertNotEquals(
          remoteTypeDef.getId(),
          TypeDef.buildTypeDef(reader.getTypeResolver(), readerType).getId());
      Class<? extends Serializer<Object>> serializerClass =
          CodecUtils.loadOrGenCompatibleMetaSharedCodecClass(
              reader.getTypeResolver(), (Class<Object>) readerType, remoteTypeDef);
      Assert.assertTrue(
          GeneratedCompatibleMetaSharedSerializer.class.isAssignableFrom(serializerClass));
      Constructor<? extends Serializer<Object>> constructor =
          serializerClass.getConstructor(
              org.apache.fory.resolver.TypeResolver.class, Class.class, TypeDef.class);
      Serializer<Object> serializer =
          constructor.newInstance(
              reader.getTypeResolver(), (Class<Object>) readerType, remoteTypeDef);
      Object writerValue = writerType.getConstructor().newInstance();
      setField(writerType, writerValue, "id", 42);
      setField(writerType, writerValue, "legacy", 99);
      MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(128);
      MetaSharedSerializer<Object> writerSerializer =
          new MetaSharedSerializer<>(
              writer.getTypeResolver(), (Class<Object>) writerType, remoteTypeDef);
      writer.getWriteContext().prepare(buffer, null);
      try {
        writerSerializer.write(writer.getWriteContext(), writerValue);
      } finally {
        writer.getWriteContext().reset();
      }
      buffer.readerIndex(0);
      reader.getReadContext().prepare(buffer, null, false);
      Object result;
      try {
        result = serializer.read(reader.getReadContext());
      } finally {
        reader.getReadContext().reset();
      }
      Assert.assertSame(result.getClass(), readerType);
      Assert.assertEquals(getField(readerType, result, "id"), 42);
      Assert.assertEquals(getField(readerType, result, "added"), "default");
      Assert.assertThrows(
          UnsupportedOperationException.class,
          () ->
              serializer.write(
                  reader.getWriteContext(), readerType.getConstructor().newInstance()));
    }
  }

  private static CompilationResult compile(String typeName, String source) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Assert.assertNotNull(compiler, "Tests require a JDK compiler");
    Path root = Files.createTempDirectory("fory-processor-test");
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
              classRoot.toString(),
              "-s",
              root.resolve("generated").toString());
      JavaCompiler.CompilationTask task =
          compiler.getTask(null, fileManager, diagnostics, options, null, units);
      task.setProcessors(Collections.singletonList(new ForyStructProcessor()));
      return new CompilationResult(
          classRoot, root.resolve("generated"), task.call(), diagnostics.getDiagnostics());
    }
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

  private static void invoke(
      Class<?> type, Object target, String name, Class<?> parameterType, Object value)
      throws Exception {
    java.lang.reflect.Method method = type.getDeclaredMethod(name, parameterType);
    method.setAccessible(true);
    method.invoke(target, value);
  }

  private static Descriptor descriptor(List<Descriptor> descriptors, String name) {
    for (Descriptor descriptor : descriptors) {
      if (descriptor.getName().equals(name)) {
        return descriptor;
      }
    }
    throw new AssertionError("Missing descriptor " + name);
  }

  private static final class CompilationResult {
    final Path classRoot;
    final Path generatedRoot;
    final boolean success;
    final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    CompilationResult(
        Path classRoot,
        Path generatedRoot,
        boolean success,
        List<Diagnostic<? extends JavaFileObject>> diagnostics) {
      this.classRoot = classRoot;
      this.generatedRoot = generatedRoot;
      this.success = success;
      this.diagnostics = new ArrayList<>(diagnostics);
    }

    URLClassLoader classLoader() throws IOException {
      URL[] urls = {classRoot.toUri().toURL()};
      return new URLClassLoader(urls, ForyStructProcessorTest.class.getClassLoader());
    }

    String generatedSource(String relativePath) throws IOException {
      return new String(
          Files.readAllBytes(generatedRoot.resolve(relativePath)), StandardCharsets.UTF_8);
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
