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

// Each field will have a setter and getter, this does not refer to the public getter and setter in the OOP concept
// Even private fields will have private getters and setters, they are just not visible externally
// Similar to if a setter is defined using the set keyword, for example
// set afield(int f) => _f = f;
// Here afield will be analyzed as a fieldElement, it only has a setter at this point
// If a getter with the same name is also declared, for example
// int get afield => _f;
// Then this fieldElement (afield) will have both a getter and a setter
// tips: In Dart, fields and getters/setters cannot have the same name
import 'package:analyzer/dart/element/element.dart';
import 'package:fury/src/codegen/analyze/analyzer.dart';
import 'package:fury/src/codegen/analyze/annotation/location_level_ensure.dart';
import 'package:fury/src/codegen/analyze/interface/field_analyzer.dart';
import 'package:fury/src/codegen/const/location_level.dart';
import 'package:fury/src/codegen/entity/either.dart';
import 'package:fury/src/codegen/entity/location_mark.dart';
import 'package:fury/src/codegen/exception/constraint_violation_exception.dart';
import 'package:fury/src/codegen/meta/impl/field_spec_immutable.dart';
import 'package:fury/src/codegen/meta/impl/type_spec_gen.dart';
import 'package:fury/src/codegen/meta/public_accessor_field.dart';
import 'package:fury/src/annotation/fury_key.dart';

class FieldAnalyzerImpl implements FieldAnalyzer{

  const FieldAnalyzerImpl();

  @override
  Either<FieldSpecImmutable, PublicAccessorField>? analyze(
    FieldElement element,
    FieldOverrideChecker fieldOverrideChecker,
    @LocationEnsure(LocationLevel.clsLevel) LocationMark locationMark,
  ) {
    assert(locationMark.ensureClassLevel);
    assert(!element.isStatic);

    String fieldName = element.name;
    /*---------Field override check--------------------------------------------*/
    if (fieldOverrideChecker(fieldName)){
      throw FieldOverridingException(
        locationMark.libPath,
        locationMark.clsName,
        [fieldName],
      );
    }
    /*---------Handle synthetic fields--------------------------------------------*/
    if(element.isSynthetic){
      // Synthetic fields, here they are synthesized getters and setters
      if (element.isPublic){
        return Either.right(
          PublicAccessorField(
            fieldName,
            element.setter != null,
            element.getter != null
          ),
        );
      }
      // indicate the composite field is private
      return null;
    }
    /*---------Location record--------------------------------------------*/
    locationMark = locationMark.copyWithFieldName(fieldName); // Note that fieldName is added here

    FuryKey key = Analyzer.keyAnnotationAnalyzer.analyze(
      element.metadata,
      locationMark,
    );

    
    if (!key.includeFromFury && !key.includeToFury){
      // If both are false, return null directly, this field is ignored
      return null;
    }

    TypeSpecGen fieldType = Analyzer.typeAnalyzer.getTypeImmutableAndTag(
      Analyzer.typeSystemAnalyzer.decideInterfaceType(element.type),
      locationMark,
    );

    return Either.left(
      FieldSpecImmutable.publicOr(
        element.isPublic,
        name: fieldName,
        typeSpec: fieldType,
        className: locationMark.clsName,
        isFinal: element.isFinal,
        isLate: element.isLate,
        hasInitializer: element.hasInitializer,
        includeFromFury: key.includeFromFury,
        includeToFury: key.includeToFury,
      ),
    );
  }
}
