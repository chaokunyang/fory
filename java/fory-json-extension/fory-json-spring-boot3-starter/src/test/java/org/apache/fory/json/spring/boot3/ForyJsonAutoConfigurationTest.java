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

package org.apache.fory.json.spring.boot3;

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
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.core.SpringVersion;

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
  void configuresServletBeans() {
    servletContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ForyJson.class);
          assertThat(context).hasSingleBean(ForyJsonHttpMessageConverter.class);
          assertThat(context).doesNotHaveBean(ForyJsonEncoder.class);
          assertThat(context).doesNotHaveBean(ForyJsonDecoder.class);
          assertThat(context.getBean(ForyJsonHttpMessageConverter.class).getMaxInputBytes())
              .isEqualTo(ForyJsonProperties.DEFAULT_MAX_INPUT_BYTES);
        });
  }

  @Test
  void bindsMvcInputLimit() {
    servletContextRunner
        .withPropertyValues("fory.json.max-input-bytes=4096")
        .run(
            context ->
                assertThat(context.getBean(ForyJsonHttpMessageConverter.class).getMaxInputBytes())
                    .isEqualTo(4096));
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
        .withBean(ForyJsonModule.class, () -> module(installed))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ForyJson.class);
              assertThat(installed).isTrue();
            });
  }

  @Test
  void backsOffFromApplicationRuntime() {
    ForyJson applicationRuntime = ForyJson.builder().build();
    servletContextRunner
        .withBean(ForyJson.class, () -> applicationRuntime)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ForyJson.class);
              assertThat(context.getBean(ForyJson.class)).isSameAs(applicationRuntime);
            });
  }

  @Test
  void backsOffFromApplicationConverter() {
    ForyJsonHttpMessageConverter applicationConverter =
        new ForyJsonHttpMessageConverter(ForyJson.builder().build(), 1024);
    servletContextRunner
        .withBean(ForyJsonHttpMessageConverter.class, () -> applicationConverter)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ForyJsonHttpMessageConverter.class);
              assertThat(context.getBean(ForyJsonHttpMessageConverter.class))
                  .isSameAs(applicationConverter);
            });
  }

  @Test
  void configuresReactiveBeans() {
    reactiveContextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ForyJson.class);
          assertThat(context).hasSingleBean(ForyJsonEncoder.class);
          assertThat(context).hasSingleBean(ForyJsonDecoder.class);
          assertThat(context).hasSingleBean(CodecCustomizer.class);
          assertThat(context).doesNotHaveBean(ForyJsonHttpMessageConverter.class);
        });
  }

  @Test
  void backsOffFromApplicationCodecs() {
    ForyJson runtime = ForyJson.builder().build();
    ForyJsonEncoder applicationEncoder = new ForyJsonEncoder(runtime);
    ForyJsonDecoder applicationDecoder = new ForyJsonDecoder(runtime);
    reactiveContextRunner
        .withBean(ForyJsonEncoder.class, () -> applicationEncoder)
        .withBean(ForyJsonDecoder.class, () -> applicationDecoder)
        .run(
            context -> {
              assertThat(context.getBean(ForyJsonEncoder.class)).isSameAs(applicationEncoder);
              assertThat(context.getBean(ForyJsonDecoder.class)).isSameAs(applicationDecoder);
            });
  }

  @Test
  void doesNotConfigureOutsideWebApplication() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ForyJsonAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(ForyJson.class));
  }

  @Test
  void doesNotConfigureWithoutBoot3ConverterApi() {
    servletContextRunner
        .withClassLoader(
            new FilteredClassLoader(
                "org.springframework.boot.autoconfigure.http.HttpMessageConverters"))
        .run(context -> assertThat(context).doesNotHaveBean(ForyJson.class));
  }

  @Test
  void usesSpringFramework62() {
    assertThat(SpringVersion.getVersion()).startsWith("6.2.");
  }

  private static ForyJsonModule module(AtomicBoolean installed) {
    return context -> installed.set(true);
  }
}
