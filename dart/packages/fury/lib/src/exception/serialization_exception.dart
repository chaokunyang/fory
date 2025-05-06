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

abstract class SerializationException extends FuryException {
  final String? _where;

  SerializationException([this._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    if (_where != null) {
      buf.write('where: ');
      buf.writeln(_where);
    }
  }
}

class TypeIncompatibleException extends SerializationException {
  final ObjType _specified;
  final String _reason;

  TypeIncompatibleException(this._specified, this._reason, [super._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('the specified type: ');
    buf.writeln(_specified);
    buf.write('while the reason: ');
    buf.writeln(_reason);
  }
}

class SerializationRangeException extends SerializationException {
  final ObjType _specified;
  final num _yourValue;

  SerializationRangeException(this._specified, this._yourValue, [super._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('the specified type: ');
    buf.writeln(_specified);
    buf.write('while your value: ');
    buf.writeln(_yourValue);
  }
}

class SerializationConflictException extends SerializationException {
  final String _setting;
  final String _but;

  SerializationConflictException(this._setting, this._but, [super._where]);

  @override
  void giveExceptionMessage(StringBuffer buf) {
    super.giveExceptionMessage(buf);
    buf.write('the setting: ');
    buf.writeln(_setting);
    buf.write('while: ');
    buf.writeln(_but);
  }
}