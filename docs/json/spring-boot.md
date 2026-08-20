---
title: Spring and Spring Boot
sidebar_position: 6.5
id: spring-boot
license: |
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
---

Fory provides Spring MVC message converters and Spring WebFlux codecs for Java 17 or later. The
default artifacts track Spring Framework 7 and Spring Boot 4; compatibility artifacts cover Spring
Framework 6.2 and Spring Boot 3.5.

## Choose one version line

Select one complete version line and keep every Fory artifact on the same version. A Spring Boot
starter includes its matching Spring adapter, so a Boot application does not add the adapter
separately.

The examples below use development snapshots. Configure the Apache snapshot repository first as
shown in [Getting Started](getting-started.md#requirements-and-installation).

| Version line  | Spring Framework | Direct Spring artifact              | Spring Boot | Starter artifact                                 |
| ------------- | ---------------- | ----------------------------------- | ----------- | ------------------------------------------------ |
| Current       | 7.x              | `org.apache.fory:fory-json-spring`  | 4.x         | `org.apache.fory:fory-json-spring-boot-starter`  |
| Compatibility | 6.2.x            | `org.apache.fory:fory-json-spring6` | 3.5.x       | `org.apache.fory:fory-json-spring-boot3-starter` |

For a direct Spring Framework 7 application, add:

```xml
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-json-spring</artifactId>
  <version>1.7.0-SNAPSHOT</version>
</dependency>
```

For a Spring Boot 4 application, add the starter instead:

```xml
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-json-spring-boot-starter</artifactId>
  <version>1.7.0-SNAPSHOT</version>
</dependency>
```

For a direct Spring Framework 6.2 application, add:

```xml
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-json-spring6</artifactId>
  <version>1.7.0-SNAPSHOT</version>
</dependency>
```

For a Spring Boot 3.5 application, add the compatibility starter instead:

```xml
<dependency>
  <groupId>org.apache.fory</groupId>
  <artifactId>fory-json-spring-boot3-starter</artifactId>
  <version>1.7.0-SNAPSHOT</version>
</dependency>
```

Do not combine the two version lines or add both starters. The adapters and starters do not choose
Spring MVC or Spring WebFlux for the application; keep the application's existing Spring web-stack
dependency.

## Spring Boot setup

The matching starter detects whether the application is using Spring MVC or Spring WebFlux and
registers the Fory integration for that server stack. It creates one thread-safe `ForyJson` bean
unless the application provides one.

### Spring MVC

A normal Spring MVC controller can read and write DTOs through Fory JSON:

```java
import org.apache.fory.json.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public final class UserController {
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public User echo(@RequestBody User user) {
    return user;
  }

  public record User(long id, @JsonProperty("display_name") String name) {}
}
```

The request body `{"id":7,"display_name":"Alice"}` produces a response with the same JSON
properties. Declared generic types such as `List<User>` and `Map<String, User>` retain their type
arguments during conversion. The MVC converter supports `application/json` and structured JSON
media types such as `application/problem+json`.

### Spring WebFlux

The WebFlux codecs support a single reactive value, a JSON array, and newline-delimited JSON
(NDJSON):

```java
import org.apache.fory.json.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public final class ReactiveUserController {
  @PostMapping(path = "/users/one", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Mono<User> echo(@RequestBody Mono<User> user) {
    return user;
  }

  @GetMapping(path = "/users/array", produces = MediaType.APPLICATION_JSON_VALUE)
  public Flux<User> array() {
    return Flux.just(new User(7, "Alice"), new User(8, "Bob"));
  }

  @GetMapping(path = "/users/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
  public Flux<User> stream() {
    return Flux.just(new User(7, "Alice"), new User(8, "Bob"));
  }

  public record User(long id, @JsonProperty("display_name") String name) {}
}
```

`Mono<User>` with `application/json` reads or writes one complete JSON value. `Flux<User>` with
`application/json` writes one JSON array, including `[]` for an empty publisher. With
`application/x-ndjson`, each `Flux` element is one JSON value followed by a newline.

For request decoding, a `Flux<User>` with `application/json` consumes one complete JSON array and
then publishes its elements. A `Flux<User>` with `application/x-ndjson` publishes each complete
line as one element; blank lines are ignored and both LF and CRLF line endings are accepted.

## Direct Spring Framework setup

Direct Spring applications create a `ForyJson` instance and register the matching adapter. The two
artifacts use the same `org.apache.fory.json.spring` package, but compile against their own Spring
Framework line.

### Spring MVC registration

Spring Framework 7 uses the server message-converter builder:

```java
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.spring.ForyJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ForyMvcConfiguration implements WebMvcConfigurer {
  private final ForyJson json = ForyJson.builder().build();

  @Override
  public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
    builder.withJsonConverter(new ForyJsonHttpMessageConverter(json));
  }
}
```

Spring Framework 6.2 adds the Fory converter before the other configured JSON converters:

```java
import java.util.List;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.spring.ForyJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ForyMvcConfiguration implements WebMvcConfigurer {
  private final ForyJson json = ForyJson.builder().build();

  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.add(0, new ForyJsonHttpMessageConverter(json));
  }
}
```

### Spring WebFlux registration

Spring Framework 7 replaces the default JSON encoder and decoder as follows:

```java
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.spring.ForyJsonDecoder;
import org.apache.fory.json.spring.ForyJsonEncoder;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class ForyWebFluxConfiguration implements WebFluxConfigurer {
  private final ForyJson json = ForyJson.builder().build();

  @Override
  public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
    configurer.defaultCodecs().jacksonJsonEncoder(new ForyJsonEncoder(json));
    configurer.defaultCodecs().jacksonJsonDecoder(new ForyJsonDecoder(json));
  }
}
```

Spring Framework 6.2 uses the corresponding Jackson 2 codec slots:

```java
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.spring.ForyJsonDecoder;
import org.apache.fory.json.spring.ForyJsonEncoder;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class ForyWebFluxConfiguration implements WebFluxConfigurer {
  private final ForyJson json = ForyJson.builder().build();

  @Override
  public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
    configurer.defaultCodecs().jackson2JsonEncoder(new ForyJsonEncoder(json));
    configurer.defaultCodecs().jackson2JsonDecoder(new ForyJsonDecoder(json));
  }
}
```

## Customize Fory JSON

Define a `ForyJson` bean to select Fory JSON builder settings. The starter then uses that bean and
does not create another one:

```java
import org.apache.fory.json.ForyJson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {
  @Bean
  ForyJson foryJson() {
    return ForyJson.builder().writeNullFields(true).build();
  }
}
```

When the starter creates the default `ForyJson`, it discovers every `ForyJsonModule` bean in Spring
order and installs those modules before building the runtime:

```java
import org.apache.fory.json.ForyJsonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ModuleConfiguration {
  @Bean
  ForyJsonModule moneyJsonModule() {
    return MoneyJsonModule.INSTANCE;
  }
}
```

If the application provides its own `ForyJson` bean, install the required modules on that bean's
builder. A built `ForyJson` is immutable, so the starter cannot add module beans afterward. See
[Modules](modules.md) for module creation and installation.

Fory annotations from `org.apache.fory.json.annotation` and installed modules control mapping.
Jackson annotations and `spring.jackson.*` properties do not configure Fory JSON.

## Configure input limits

### Spring MVC

Both starters limit a complete MVC request body with this property:

```properties
fory.json.max-input-bytes=67108864
```

The default is `67108864` bytes (64 MiB), and the value must be positive. This is a transport-body
limit, not a heap limit or a replacement for Fory JSON's `maxDepth` and `maxGraphMemoryBytes`.
Direct Spring applications can pass a different positive byte limit to
`new ForyJsonHttpMessageConverter(json, maxInputBytes)`.

### Spring WebFlux

Each Spring Boot line uses its standard codec aggregation property:

| Spring Boot | Property                                |
| ----------- | --------------------------------------- |
| 4.x         | `spring.http.codecs.max-in-memory-size` |
| 3.5.x       | `spring.codec.max-in-memory-size`       |

Spring Boot 4:

```properties title="application.properties"
spring.http.codecs.max-in-memory-size=8MB
```

Spring Boot 3.5:

```properties title="application.properties"
spring.codec.max-in-memory-size=8MB
```

Set only the property for the application's Spring Boot line. When it is absent, Spring's codec
default remains in effect; most aggregating codecs use 256 KiB. For Fory JSON, the limit applies to
one complete `Mono` value, the complete body of a JSON array decoded to `Flux`, or one top-level
NDJSON value. Direct Spring applications configure the same limit with
`ForyJsonDecoder.setMaxInMemorySize(int)`.
