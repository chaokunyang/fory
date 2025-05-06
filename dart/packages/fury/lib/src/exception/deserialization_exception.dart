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

import 'package:fury/src/exception/fury_exception.dart';

import 'package:fury/src/const/obj_type.dart';

abstract class DeserializationException extends FuryException {
  final String? _where;

  DeserializationException([this._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    if (_where != null) {
      buf.write('where: ');
      buf.writeln(_where);
    }
  }
}

class DeserializationConflictException extends DeserializationException{
  final String _readSetting;
  final String _nowFurySetting;

  DeserializationConflictException(this._readSetting, this._nowFurySetting, [super._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('the fury instance setting: ');
    buf.writeln(_nowFurySetting);
    buf.write('while the read setting: ');
    buf.writeln(_readSetting);
  }
}

class UnsupportedFeatureException extends DeserializationException{

  final Object _read;
  final List<Object> _supported;
  final String _whatFeature;

  UnsupportedFeatureException(this._read, this._supported, this._whatFeature, [super._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('unsupported ');
    buf.write(_whatFeature);
    buf.write(' for type: ');
    buf.writeln(_read);
    buf.write('supported: ');
    buf.writeAll(_supported, ', ');
    buf.write('\n');
  }
}

class DeserializationRangeException extends FuryException{
  final int index;
  final List<Object> candidates;

  DeserializationRangeException(this.index, this.candidates,);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('the index $index is out of range, the candidates are: ');
    buf.write('[');
    buf.writeAll(candidates, ', ');
    buf.write(']\n');
    buf.write('This data may have inconsistencies on the other side');
  }
}

class InvalidParamException extends DeserializationException{
  final String _invalidParam;
  final String _validParams;

  InvalidParamException(this._invalidParam, this._validParams, [super._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('the invalid param: ');
    buf.writeln(_invalidParam);
    buf.write('while the valid params: ');
    buf.writeln(_validParams);
  }
}

class FuryMismatchException extends DeserializationException{
  final Object readValue;
  final Object expected;
  final String specification;

  FuryMismatchException(
      this.readValue,
      this.expected,
      this.specification,
      );

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('FuryMismatchException: ');
    buf.write(specification);
    buf.write('\nread value: ');
    buf.write(readValue);
    buf.write(' ,while expected: ');
    buf.write(expected);
    buf.write('\n');
  }
}

class UnsupportedTypeException extends FuryException{
  final ObjType _objType;

  UnsupportedTypeException(this._objType,);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('unsupported type: ');
    buf.writeln(_objType);
  }
}
