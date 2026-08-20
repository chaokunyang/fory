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

package org.apache.fory.json.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonModule;
import org.apache.fory.json.spring.ForyJsonDecoder;
import org.apache.fory.json.spring.ForyJsonEncoder;
import org.apache.fory.json.spring.ForyJsonHttpMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.SpringVersion;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;

class ForyJsonAutoConfigurationTest {
  private final WebApplicationContextRunner servletContextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ForyJsonAutoConfiguration.class));

  private final ReactiveWebApplicationContextRunner reactiveContextRunner =
      new ReactiveWebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ForyJsonAutoConfiguration.class));

  @Test
  void discoversAutoConfigurationMetadata() {
    assertThat(
            ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates())
        .contains(ForyJsonAutoConfiguration.class.getName());
  }

  @Test
  void configuresServletRuntimeAndConverter() {
    servletContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ForyJson.class);
          assertThat(context).hasSingleBean(ForyJsonProperties.class);
          assertThat(context).hasSingleBean(ServerHttpMessageConvertersCustomizer.class);
          assertThat(context).doesNotHaveBean(ForyJsonHttpMessageConverter.class);
          assertThat(context).doesNotHaveBean(ForyJsonEncoder.class);
          assertThat(context).doesNotHaveBean(ForyJsonDecoder.class);

          ServerHttpMessageConvertersCustomizer customizer =
              context.getBean(ServerHttpMessageConvertersCustomizer.class);
          HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer();
          customizer.customize(builder);
          ForyJsonHttpMessageConverter converter =
              findConverter(builder.build(), ForyJsonHttpMessageConverter.class);
          assertThat(converter.getMaxInputBytes())
              .isEqualTo(ForyJsonProperties.DEFAULT_MAX_INPUT_BYTES);
          assertThat(converter.canWrite(ProblemDetail.class, null)).isTrue();
        });
  }

  @Test
  void bindsMvcInputLimit() {
    servletContextRunner
        .withPropertyValues("fory.json.max-input-bytes=4096")
        .run(
            context -> {
              HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer();
              context.getBean(ServerHttpMessageConvertersCustomizer.class).customize(builder);
              ForyJsonHttpMessageConverter converter =
                  findConverter(builder.build(), ForyJsonHttpMessageConverter.class);
              assertThat(converter.getMaxInputBytes()).isEqualTo(4096);
            });
  }

  @Test
  void rejectsInvalidMvcInputLimit() {
    servletContextRunner
        .withPropertyValues("fory.json.max-input-bytes=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void installsModuleBeans() {
    AtomicBoolean installed = new AtomicBoolean();
    servletContextRunner
        .withBean(ForyJsonModule.class, () -> context -> installed.set(true))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ForyJson.class);
              assertThat(installed).isTrue();
            });
  }

  @Test
  void backsOffForUserRuntime() {
    ForyJson custom = ForyJson.builder().withCodegen(false).build();
    servletContextRunner
        .withBean(ForyJson.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ForyJson.class);
              assertThat(context.getBean(ForyJson.class)).isSameAs(custom);
            });
  }

  @Test
  void configuresReactiveRuntimeAndCodecs() {
    reactiveContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ForyJson.class);
          assertThat(context).hasSingleBean(ForyJsonEncoder.class);
          assertThat(context).hasSingleBean(ForyJsonDecoder.class);
          assertThat(context).hasSingleBean(CodecCustomizer.class);
          assertThat(context).doesNotHaveBean(ServerHttpMessageConvertersCustomizer.class);
        });
  }

  @Test
  void backsOffForUserMvcCustomizer() {
    ServerHttpMessageConvertersCustomizer customizer = builder -> {};
    servletContextRunner
        .withBean(
            "foryJsonHttpMessageConvertersCustomizer",
            ServerHttpMessageConvertersCustomizer.class,
            () -> customizer)
        .run(
            context ->
                assertThat(context.getBean(ServerHttpMessageConvertersCustomizer.class))
                    .isSameAs(customizer));
  }

  @Test
  void doesNotConfigureOutsideWebApplication() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ForyJsonAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(ForyJson.class));
  }

  @Test
  void usesSpringFramework7() {
    assertThat(SpringVersion.getVersion()).startsWith("7.0.");
  }

  private static <T extends HttpMessageConverter<?>> T findConverter(
      Iterable<HttpMessageConverter<?>> converters, Class<T> converterType) {
    for (HttpMessageConverter<?> converter : converters) {
      if (converterType.isInstance(converter)) {
        return converterType.cast(converter);
      }
    }
    throw new AssertionError("Missing converter " + converterType.getName());
  }
}
