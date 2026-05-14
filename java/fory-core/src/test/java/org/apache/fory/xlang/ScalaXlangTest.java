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

package org.apache.fory.xlang;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import lombok.Data;
import org.apache.fory.Fory;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.annotation.ForyStruct;
import org.apache.fory.annotation.Nullable;
import org.apache.fory.config.Language;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.serializer.UnionSerializer;
import org.apache.fory.test.TestUtils;
import org.apache.fory.type.union.Union;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Executes Java-driven Scala 3 macro xlang serializer tests. */
@Test
public class ScalaXlangTest extends XlangTestBase {
  private static final String DERIVED_CASE = "derived_struct_round_trip";
  private static final String KNOWN_UNION_CASE = "known_union_case_round_trip";
  private static final String UNKNOWN_UNION_CASE = "unknown_union_case_round_trip";
  private static final String NAMESPACE = "scala_peer";
  private static final File SCALA_DIR = new File("../../scala");

  @BeforeMethod(alwaysRun = true)
  public void skipInheritedXlangCases(Method method) {
    if (method.getDeclaringClass() != ScalaXlangTest.class) {
      throw new SkipException(
          "Scala xlang phase 1 validates macro-derived Scala 3 peer serializers");
    }
  }

  @Override
  protected void ensurePeerReady() {
    String enabled = System.getenv("FORY_SCALA_JAVA_CI");
    if (!"1".equals(enabled)) {
      throw new SkipException("Skipping ScalaXlangTest: FORY_SCALA_JAVA_CI not set to 1");
    }
    boolean buildSuccess =
        TestUtils.executeCommand(
            Arrays.asList("sbt", "--batch", "++3.3.1", "Test/compile"),
            240,
            Collections.emptyMap(),
            SCALA_DIR);
    if (!buildSuccess) {
      throw new AssertionError("Failed to compile Scala xlang peer");
    }
  }

  @Override
  protected CommandContext buildCommandContext(String caseName, Path dataFile) {
    return new CommandContext(
        Arrays.asList(
            "sbt",
            "--batch",
            "++3.3.1",
            "Test/runMain org.apache.fory.serializer.scala.ScalaXlangPeer "
                + caseName
                + " "
                + dataFile.toAbsolutePath()),
        envBuilder(dataFile),
        SCALA_DIR);
  }

  @Override
  protected ExecutionContext prepareExecution(String caseName, byte[] payload) throws IOException {
    if (!DERIVED_CASE.equals(caseName)
        && !KNOWN_UNION_CASE.equals(caseName)
        && !UNKNOWN_UNION_CASE.equals(caseName)) {
      throw new SkipException(
          "Scala xlang phase 1 validates macro-derived Scala 3 peer serializers");
    }
    return super.prepareExecution(caseName, payload);
  }

  @Test(groups = "xlang")
  public void testDerivedStructRoundTrip() throws IOException {
    Fory fory = newFory();
    registerScalaPeerTypes(fory);

    ScalaPeerUserMirror request = new ScalaPeerUserMirror();
    request.id = 41;
    request.name = "java";
    request.email = "java@example.com";

    ExecutionContext context = executePeer(DERIVED_CASE, fory, request);
    ScalaPeerUserMirror response =
        (ScalaPeerUserMirror) fory.deserialize(readBuffer(context.dataFile()));
    Assert.assertEquals(response.id, 42);
    Assert.assertEquals(response.name, "scala-java");
    Assert.assertNull(response.email);
  }

  @Test(groups = "xlang")
  public void testKnownUnionCaseRoundTrip() throws IOException {
    Fory fory = newFory();
    registerScalaPeerTypes(fory);

    ScalaPeerUserMirror user = new ScalaPeerUserMirror();
    user.id = 41;
    user.name = "java";
    user.email = "java@example.com";

    ExecutionContext context =
        executePeer(KNOWN_UNION_CASE, fory, new ScalaPeerTargetMirror(1, user));
    ScalaPeerTargetMirror response =
        (ScalaPeerTargetMirror) fory.deserialize(readBuffer(context.dataFile()));
    Assert.assertEquals(response.getIndex(), 1);
    Assert.assertTrue(response.getValue() instanceof ScalaPeerUserMirror);
    ScalaPeerUserMirror responseUser = (ScalaPeerUserMirror) response.getValue();
    Assert.assertEquals(responseUser.id, 42);
    Assert.assertEquals(responseUser.name, "scala-java");
    Assert.assertNull(responseUser.email);
  }

  @Test(groups = "xlang")
  public void testUnknownUnionCaseRoundTrip() throws IOException {
    Fory fory = newFory();
    registerScalaPeerTypes(fory);

    ScalaPeerUserMirror unknownPayload = new ScalaPeerUserMirror();
    unknownPayload.id = 99;
    unknownPayload.name = "future";
    unknownPayload.email = "future@example.com";

    ExecutionContext context =
        executePeer(UNKNOWN_UNION_CASE, fory, new ScalaPeerTargetMirror(99, unknownPayload));
    ScalaPeerTargetMirror response =
        (ScalaPeerTargetMirror) fory.deserialize(readBuffer(context.dataFile()));
    Assert.assertEquals(response.getIndex(), 99);
    Assert.assertTrue(response.getValue() instanceof ScalaPeerUserMirror);
    ScalaPeerUserMirror responsePayload = (ScalaPeerUserMirror) response.getValue();
    Assert.assertEquals(responsePayload.id, 100);
    Assert.assertEquals(responsePayload.name, "scala-future");
    Assert.assertNull(responsePayload.email);
  }

  private ExecutionContext executePeer(String caseName, Fory fory, Object request)
      throws IOException {
    MemoryBuffer buffer = MemoryUtils.buffer(128);
    fory.serialize(buffer, request);
    ExecutionContext context = prepareExecution(caseName, buffer.getBytes(0, buffer.writerIndex()));
    runPeer(context, 180);
    return context;
  }

  private static Fory newFory() {
    return Fory.builder()
        .withLanguage(Language.XLANG)
        .withCompatible(true)
        .requireClassRegistration(true)
        .build();
  }

  private static void registerScalaPeerTypes(Fory fory) {
    fory.register(ScalaPeerUserMirror.class, NAMESPACE, "ScalaPeerUser");
    fory.registerUnion(
        ScalaPeerTargetMirror.class,
        NAMESPACE,
        "ScalaPeerTarget",
        new UnionSerializer(fory.getTypeResolver(), ScalaPeerTargetMirror.class));
  }

  @Data
  @ForyStruct
  public static class ScalaPeerUserMirror {
    @ForyField(id = 1)
    public int id;

    @ForyField(id = 2)
    public String name;

    @Nullable
    @ForyField(id = 3)
    public String email;
  }

  public static final class ScalaPeerTargetMirror extends Union {
    public ScalaPeerTargetMirror(int index, Object value) {
      super(index, value);
    }
  }
}
