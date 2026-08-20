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

import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonBuilder;
import org.apache.fory.json.ForyJsonModule;
import org.apache.fory.json.spring.ForyJsonDecoder;
import org.apache.fory.json.spring.ForyJsonEncoder;
import org.apache.fory.json.spring.ForyJsonHttpMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring Boot 3.5 auto-configuration for Fory JSON on Spring MVC and WebFlux. */
@AutoConfiguration(
    beforeName =
        "org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration")
@ConditionalOnWebApplication
@ConditionalOnClass(
    name = {
      "org.apache.fory.json.ForyJson",
      "org.springframework.boot.autoconfigure.http.HttpMessageConverters"
    })
@EnableConfigurationProperties(ForyJsonProperties.class)
public final class ForyJsonAutoConfiguration {
  /** Creates the application Fory JSON runtime when the application did not provide one. */
  @Bean
  @ConditionalOnMissingBean
  public ForyJson foryJson(
      ApplicationContext applicationContext, ObjectProvider<ForyJsonModule> modules) {
    ForyJsonBuilder builder =
        ForyJson.builder().withClassLoader(applicationContext.getClassLoader());
    modules.orderedStream().forEach(builder::withModule);
    return builder.build();
  }

  /** Spring MVC-specific Fory JSON beans. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = Type.SERVLET)
  @ConditionalOnClass(
      name = {
        "org.springframework.web.servlet.DispatcherServlet",
        "org.springframework.http.converter.HttpMessageConverter"
      })
  static class MvcConfiguration {
    /** Creates the Fory JSON converter collected by Spring Boot 3 MVC configuration. */
    @Bean
    @ConditionalOnMissingBean
    ForyJsonHttpMessageConverter foryJsonHttpMessageConverter(
        ForyJson foryJson, ForyJsonProperties properties) {
      return new ForyJsonHttpMessageConverter(foryJson, properties.getMaxInputBytes());
    }
  }

  /** Spring WebFlux-specific Fory JSON beans. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = Type.REACTIVE)
  @ConditionalOnClass(
      name = {
        "org.springframework.web.reactive.DispatcherHandler",
        "org.springframework.boot.web.codec.CodecCustomizer"
      })
  static class WebFluxConfiguration {
    /** Creates the Fory JSON encoder when the application did not provide one. */
    @Bean
    @ConditionalOnMissingBean
    ForyJsonEncoder foryJsonEncoder(ForyJson foryJson) {
      return new ForyJsonEncoder(foryJson);
    }

    /** Creates the Fory JSON decoder when the application did not provide one. */
    @Bean
    @ConditionalOnMissingBean
    ForyJsonDecoder foryJsonDecoder(ForyJson foryJson) {
      return new ForyJsonDecoder(foryJson);
    }

    /** Replaces Spring WebFlux's default Jackson JSON codecs with Fory JSON. */
    @Bean
    @ConditionalOnMissingBean(name = "foryJsonCodecCustomizer")
    CodecCustomizer foryJsonCodecCustomizer(ForyJsonEncoder encoder, ForyJsonDecoder decoder) {
      return configurer -> {
        configurer.defaultCodecs().jackson2JsonEncoder(encoder);
        configurer.defaultCodecs().jackson2JsonDecoder(decoder);
      };
    }
  }
}
