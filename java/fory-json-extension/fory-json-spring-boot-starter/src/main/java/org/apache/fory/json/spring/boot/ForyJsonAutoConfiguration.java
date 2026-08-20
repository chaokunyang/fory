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
import org.springframework.boot.http.codec.CodecCustomizer;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auto-configuration for Fory JSON with Spring Boot 4. */
@AutoConfiguration
@ConditionalOnClass(ForyJson.class)
@ConditionalOnWebApplication
@EnableConfigurationProperties(ForyJsonProperties.class)
public final class ForyJsonAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  ForyJson foryJson(ApplicationContext applicationContext, ObjectProvider<ForyJsonModule> modules) {
    ForyJsonBuilder builder =
        ForyJson.builder().withClassLoader(applicationContext.getClassLoader());
    modules.orderedStream().forEach(builder::withModule);
    return builder.build();
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = Type.SERVLET)
  @ConditionalOnClass(ServerHttpMessageConvertersCustomizer.class)
  static class MvcConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "foryJsonHttpMessageConvertersCustomizer")
    ServerHttpMessageConvertersCustomizer foryJsonHttpMessageConvertersCustomizer(
        ForyJson foryJson, ForyJsonProperties properties) {
      ForyJsonHttpMessageConverter converter =
          new ForyJsonHttpMessageConverter(foryJson, properties.getMaxInputBytes());
      return builder -> builder.withJsonConverter(converter);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnWebApplication(type = Type.REACTIVE)
  @ConditionalOnClass(CodecCustomizer.class)
  static class WebFluxConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ForyJsonEncoder foryJsonEncoder(ForyJson foryJson) {
      return new ForyJsonEncoder(foryJson);
    }

    @Bean
    @ConditionalOnMissingBean
    ForyJsonDecoder foryJsonDecoder(ForyJson foryJson) {
      return new ForyJsonDecoder(foryJson);
    }

    @Bean
    @ConditionalOnMissingBean(name = "foryJsonCodecCustomizer")
    CodecCustomizer foryJsonCodecCustomizer(ForyJsonEncoder encoder, ForyJsonDecoder decoder) {
      return configurer -> {
        configurer.defaultCodecs().jacksonJsonEncoder(encoder);
        configurer.defaultCodecs().jacksonJsonDecoder(decoder);
      };
    }
  }
}
