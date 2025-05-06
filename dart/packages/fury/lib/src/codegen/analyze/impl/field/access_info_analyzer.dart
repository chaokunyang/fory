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

import 'dart:collection';
import 'package:fury/src/codegen/analyze/analyzer.dart';
import 'package:fury/src/codegen/analyze/annotation/location_level_ensure.dart';
import 'package:fury/src/codegen/const/location_level.dart';
import 'package:fury/src/codegen/entity/contructor_params.dart';
import 'package:fury/src/codegen/entity/location_mark.dart';
import 'package:fury/src/codegen/exception/field_exception.dart' show FieldAccessErrorType, FieldAccessException;
import 'package:fury/src/codegen/meta/impl/field_spec_immutable.dart';
import 'package:fury/src/codegen/meta/impl/fields_spec_gen.dart';
import 'package:fury/src/dev_annotation/maybe_modified.dart';

class AccessInfoAnalyzer {

  const AccessInfoAnalyzer();

  // The premise is that fields have already been sorted
  FieldsSpecGen  _analyzeConsAndFields(
    ConstructorParams consParams,
    List<FieldSpecImmutable> fields,
    bool fieldsSorted,
    @LocationEnsure(LocationLevel.clsLevel) LocationMark locationMark,
  ){
    assert(locationMark.ensureClassLevel);
    assert(fieldsSorted);

    Map<String, int> fieldNameToIndex = HashMap();
    for (int i = 0; i < fields.length; ++i){
      fieldNameToIndex[fields[i].name] = i;
    }
    List<bool> setThroughConsFlags = List.filled(fields.length, false);
    int? index;
    for (var consParam in consParams.iterator){
      index = fieldNameToIndex[consParam.name];
      // Based on the constraints for the Constructor mentioned earlier, it is impossible not to find the index here.
      assert(index != null, "Field ${consParam.name} not found in fields");
      if (!(fields[index!].includeFromFury)){
        // Indicates that this field is not in the fields list or does not need to be analyzed
        // However, if consParam is optional, it's okay
        if (consParam.optional) {
          consParam.setNotInclude();
          continue;
        }
        throw FieldAccessException(
          locationMark.libPath,
          locationMark.clsName,
          [fields[index].name],
          FieldAccessErrorType.notIncludedButConsDemand,
        );
      }
      consParam.setFieldIndex(index);  // It can be found that each consParam corresponds to a field and will all be assigned an index
      setThroughConsFlags[index] = true;
    }

    return FieldsSpecGen(fields, fieldsSorted, setThroughConsFlags);
  }


  FieldsSpecGen checkAndSetTheAccessInfo(
    @mayBeModified ConstructorParams? consParams,
    List<FieldSpecImmutable> fieldImmutables,
    bool fieldsSorted,
    @LocationEnsure(LocationLevel.clsLevel) LocationMark locationMark,
  ){
    assert(locationMark.ensureClassLevel);
    if (consParams == null){
      // indicate no constructor
      return FieldsSpecGen(fieldImmutables, fieldsSorted, List.filled(fieldImmutables.length, false));
    }
    /*-----------sort field--------------------------------------------------------------*/
    if (!fieldsSorted){
      // indicate sorting has not been performed
      Analyzer.fieldsSorter.sortFieldsByName(fieldImmutables);
      fieldsSorted = true;
    }
    /*------------set consParams----------------------------------------------------------*/
    FieldsSpecGen fieldsSpecGen =  _analyzeConsAndFields(consParams, fieldImmutables, fieldsSorted, locationMark);
    // Check if there are fields with includeFromFury and no assignment method
    List<String> noWayFields = [];
    var immutables = fieldsSpecGen.fields;
    var flags = fieldsSpecGen.setThroughConsFlags;

    for (int i =0;i<immutables.length;++i){
      if (immutables[i].includeFromFury && !(immutables[i].canSet) && !flags[i]){
        noWayFields.add(immutables[i].name);
      }
    }
    if (noWayFields.isNotEmpty){
      // this field has no corresponding getter
      throw FieldAccessException(
        locationMark.libPath,
        locationMark.clsName,
        noWayFields,
        FieldAccessErrorType.noWayToAssign,
      );
    }
    // Check if there are fields with includeToFury and no getter method
    noWayFields.clear();
    for (int i =0;i<immutables.length;++i){
      if (immutables[i].includeToFury && !(immutables[i].canGet)){
        noWayFields.add(immutables[i].name);
      }
    }
    if (noWayFields.isNotEmpty){
      // Indicates that there are fields without getter methods
      throw FieldAccessException(
        locationMark.libPath,
        locationMark.clsName,
        noWayFields,
        FieldAccessErrorType.noWayToGet,
      );
    }
    return fieldsSpecGen;
  }
}