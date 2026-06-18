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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.fory.benchmark.data.MediaContent;
import org.apache.fory.json.ForyJson;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonSerializationSuite {
  @State(Scope.Thread)
  public static class JsonState {
    ForyJson foryJson;
    JSONWriter.Context fastjson2Context;
    MediaContent mediaContent;

    @Setup
    public void setup() {
      foryJson = ForyJson.builder().build();
      fastjson2Context = new JSONWriter.Context();
      mediaContent = new MediaContent().populate(false);
      byte[] foryBytes = foryJson.toJsonBytes(mediaContent);
      byte[] fastjsonBytes =
          JSON.toJSONBytes(mediaContent, StandardCharsets.UTF_8, fastjson2Context);
      if (!JSON.parseObject(foryBytes).equals(JSON.parseObject(fastjsonBytes))) {
        throw new IllegalStateException("Fory JSON and fastjson2 produce different JSON objects");
      }
      String foryString = foryJson.toJson(mediaContent);
      String fastjsonString = JSON.toJSONString(mediaContent, fastjson2Context);
      if (!JSON.parseObject(foryString).equals(JSON.parseObject(fastjsonString))) {
        throw new IllegalStateException("Fory JSON and fastjson2 produce different JSON strings");
      }
    }
  }

  @Benchmark
  public byte[] foryToJsonBytes(JsonState state) {
    return state.foryJson.toJsonBytes(state.mediaContent);
  }

  @Benchmark
  public byte[] fastjson2ToJsonBytes(JsonState state) {
    return JSON.toJSONBytes(state.mediaContent, StandardCharsets.UTF_8, state.fastjson2Context);
  }

  @Benchmark
  public String foryToJsonString(JsonState state) {
    return state.foryJson.toJson(state.mediaContent);
  }

  @Benchmark
  public String fastjson2ToJsonString(JsonState state) {
    return JSON.toJSONString(state.mediaContent, state.fastjson2Context);
  }
}
