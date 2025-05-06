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

import 'package:analyzer/dart/element/element.dart';
import 'package:fury/src/codegen/analyze/interface/enum_analyzer.dart';
import 'package:fury/src/codegen/meta/impl/enum_spec_gen.dart';

class EnumAnalyzerImpl implements EnumAnalyzer{

  const EnumAnalyzerImpl();

  @override
  EnumSpecGen analyze(EnumElement enumElement) {
    String packageName = enumElement.location!.components[0];

    List<String> enumValues = enumElement.fields
    .where((e) => e.isEnumConstant)
    .map((e) => e.name)
    .toList();

    return EnumSpecGen(
      enumElement.name,
      packageName,
      enumValues,
    );
  }
}