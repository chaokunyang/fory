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

package org.apache.fory.graalvm.kotlin;

import org.apache.fory.integration.kotlin.json.corpus.KotlinJsonCorpus;
import org.apache.fory.integration.kotlin.json.corpus.PlatformAccount;
import org.apache.fory.integration.kotlin.json.corpus.PlatformCorpusChecks;
import org.apache.fory.integration.kotlin.json.corpus.PlatformJsonModule;
import org.apache.fory.integration.kotlin.json.corpus.PlatformJavaProfileMixin;
import org.apache.fory.integration.kotlin.json.corpus.PlatformKotlinProfileMixin;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.annotation.ForyJsonProvider;
import org.apache.fory.json.kotlin.ForyJsonKotlin;

/** Native Image acceptance application for provider-selected Kotlin JSON capabilities. */
public final class Main {
  private Main() {}

  public static void main(String[] args) {
    check(
        KotlinJsonProvider.class.isAnnotationPresent(ForyJsonProvider.class),
        "Native configuration provider is not reachable");

    ForyJson json =
        ForyJsonKotlin.builder()
            .withModule(PlatformJsonModule.INSTANCE)
            .registerMixin(PlatformJavaProfileMixin.class)
            .registerMixin(PlatformKotlinProfileMixin.class)
            .withAsyncCompilation(false)
            .build();
    PlatformCorpusChecks.verifyPlatformCases(json);
    PlatformCorpusChecks.verifyFailureCases(json);
    testUnavailableCapabilities(json);
    System.out.println("Fory Kotlin JSON Native Image succeed");
  }

  private static void testUnavailableCapabilities(ForyJson selected) {
    expectFailure(
        () ->
            selected.fromJson(
                KotlinJsonCorpus.caseJson("rejected-object"),
                KotlinJsonCorpus.missingCompanionType()),
        "A Kotlin model without a KSP companion was accepted");
    expectFailure(
        () ->
            selected.fromJson(
                KotlinJsonCorpus.caseJson("rejected-box"), KotlinJsonCorpus.unreachedBoxType()),
        "An unreached exact generic binding was accepted");

    ForyJson withoutKotlin = ForyJson.builder().withAsyncCompilation(false).build();
    expectFailure(
        () ->
            withoutKotlin.fromJson(
                KotlinJsonCorpus.caseJson("account-default"), KotlinJsonCorpus.accountType()),
        "A configuration without the Kotlin module accepted an immutable Kotlin model");

    ForyJson unselected =
        ForyJsonKotlin.builder()
            .withModule(PlatformJsonModule.INSTANCE)
            .withAsyncCompilation(false)
            .withFieldMode(true)
            .build();
    expectFailure(
        () ->
            unselected.fromJson(
                KotlinJsonCorpus.caseJson("account-default"), KotlinJsonCorpus.accountType()),
        "A configuration not selected by the provider used an interpreted Kotlin codec");

    PlatformAccount account =
        selected.fromJson(
            KotlinJsonCorpus.caseJson("account-default"), KotlinJsonCorpus.accountType());
    check(
        account.equals(new PlatformAccount(1, "default", "corpus-default")),
        "A failed Native capability lookup polluted the selected configuration");
  }

  private static void expectFailure(Runnable operation, String message) {
    try {
      operation.run();
    } catch (ForyJsonException expected) {
      return;
    }
    throw new AssertionError(message);
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }
}
