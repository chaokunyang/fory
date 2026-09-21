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

package org.apache.fory.benchmark;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fory.benchmark.JsonSerializationSuite.JsonState;
import org.apache.fory.benchmark.data.MediaContent;
import org.apache.fory.json.ForyJson;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JsonSerializationSuiteTest {
  @Test
  public void prettyMatchesJackson() throws IOException {
    JsonState state = new JsonState();
    state.setup();
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("emptyObject", Collections.emptyMap());
    nested.put("emptyArray", Collections.emptyList());
    nested.put(
        "values", Arrays.asList(Collections.singletonMap("中", "😀\\\"{}[],:"), new int[] {1, 2}));
    ObjectMapper mapper = new ObjectMapper();
    for (boolean codegen : new boolean[] {false, true}) {
      ForyJson json = ForyJson.builder().withCodegen(codegen).withAsyncCompilation(false).build();
      for (Object value :
          new Object[] {state.mediaContent, nested, new Object[] {nested, nested}}) {
        String compact = state.foryJson.toJson(value);
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        String expected =
            mapper
                .writer(
                    new DefaultPrettyPrinter()
                        .withObjectIndenter(indenter)
                        .withArrayIndenter(indenter))
                .writeValueAsString(mapper.readTree(compact));
        Assert.assertEquals(json.toPrettyJson(value), expected);
        Assert.assertEquals(
            json.toPrettyJsonBytes(value), expected.getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  public void testJackson() throws IOException {
    JsonSerializationSuite suite = new JsonSerializationSuite();

    JsonState jackson = new JsonState();
    jackson.setup();
    assertJackson(jackson, suite);
  }

  private static void assertJackson(JsonState state, JsonSerializationSuite suite)
      throws IOException {
    MediaContent fromBytes =
        state.mapper.readValue(suite.jacksonToJsonBytes(state), MediaContent.class);
    MediaContent fromString =
        state.mapper.readValue(suite.jacksonToJsonString(state), MediaContent.class);
    Assert.assertEquals(fromBytes, state.mediaContent);
    Assert.assertEquals(fromString, state.mediaContent);
    Assert.assertEquals(suite.jacksonFromJsonBytes(state), state.mediaContent);
    Assert.assertEquals(suite.jacksonFromJsonString(state), state.mediaContent);
  }
}
