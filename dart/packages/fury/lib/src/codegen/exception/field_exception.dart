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

import 'package:fury/src/codegen/exception/constraint_violation_exception.dart';

abstract class FieldException extends FuryConstraintViolation {
  final String _libPath;
  final String _className;
  final List<String> _invalidFields;

  FieldException(this._libPath, this._className, this._invalidFields, super._constraint, [super.where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('related class: ');
    buf.write(_libPath);
    buf.write('@');
    buf.write(_className);
    buf.write('\n');
    buf.write('invalidFields: ');
    buf.writeAll(_invalidFields, ', ');
    buf.write('\n');
  }

  @override
  String toString() {
    StringBuffer buf = StringBuffer();
    giveExceptionMessage(buf);
    return buf.toString();
  }
}

enum FieldAccessErrorType{
  noWayToAssign("This field needs to be assigned a value because it's includedFromFury, but it's not a constructor parameter and can't be assigned via a setter."),
  noWayToGet("This field needs to be read because it's includedFromFury, but it's not public and it can't be read via a getter."),
  notIncludedButConsDemand("This field is included in the constructor, but it's not includedFromFury. ");

  final String warning;

  const FieldAccessErrorType(this.warning);
}

class FieldAccessException extends FieldException {
  final FieldAccessErrorType errorType;

  FieldAccessException(
      String libPath,
      String clsName,
      List<String> fieldNames,
      this.errorType,
      ):super (
    libPath,
    clsName,
    fieldNames,
    errorType.warning,
  );
}