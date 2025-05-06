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

import 'package:fury/src/codegen/config/codegen_style.dart';
import 'package:fury/src/codegen/meta/gen_export.dart';
import 'package:fury/src/codegen/meta/impl/type_spec_gen.dart';
import 'package:fury/src/codegen/meta/lib_import_pack.dart';
import 'package:fury/src/codegen/tool/codegen_tool.dart';

import 'package:fury/src/const/obj_type.dart';

final class TypeAdapter extends GenExport{
  final TypeSpecGen typeSpec;
  TypeAdapter(this.typeSpec);

  void _genCodeInner(
    StringBuffer buf,
    LibImportPack imports,
    TypeSpecGen spec,
    String paramName,
    [String? dartCorePrefixWithPoint]
  ) {
    ObjType objType = spec.immutablePart.objType;
    // It is not possible to be compatible with LinkedList here, because it does not implement the Dart List interface.
    // If you want to support LinkedList, changes must also be made here.
    if (objType == ObjType.LIST || objType == ObjType.SET){
      if (spec.nullable){
        buf.write('($paramName == null) ? null : ');
      }
      buf.write(spec.getFullNameNoLastNull(imports));
      buf.write('.of((');
      buf.write(paramName);
      buf.write(' as ');
      buf.write(spec.getShortName(imports));
      buf.write(').map((v)=>');
      _genCodeInner(buf, imports, spec.genericsArgs[0],'v', dartCorePrefixWithPoint);
      buf.write(')');
      buf.write(')');
      return;
    }
    if (objType == ObjType.MAP){
      if (spec.nullable){
        buf.write('($paramName == null) ? null : ');
      }
      buf.write(spec.getFullNameNoLastNull(imports));
      buf.write('.of(');
      buf.write('(');
      buf.write(paramName);
      buf.write(' as ');
      buf.write(spec.getShortName(imports));
      buf.write(').map((k,v)=>');
      if (dartCorePrefixWithPoint!=null){
        buf.write(dartCorePrefixWithPoint);
      }
      buf.write('MapEntry(');
      _genCodeInner(buf, imports, spec.genericsArgs[0],'k',dartCorePrefixWithPoint);
      buf.write(',');
      _genCodeInner(buf, imports, spec.genericsArgs[1],'v', dartCorePrefixWithPoint);
      buf.write(')');
      buf.write(')');
      buf.write(')');
      return;
    }else{
      buf.write('(');
      buf.write(paramName);
      buf.write(' as ');
      buf.write(spec.getShortName(imports));
      if (spec.nullable){
        buf.write('?');
      }
      buf.write(')');
    }
  }

  @override
  void genCodeReqImportsInfo(
    StringBuffer buf,
    LibImportPack imports,
    String? dartCorePrefixWithPoint,
    [int indentLevel = 0, String paramName = 'v']
  ) {
    int totalIndent = indentLevel * CodegenStyle.indent;
    CodegenTool.writeIndent(buf, totalIndent);
    _genCodeInner(buf, imports, typeSpec, paramName, dartCorePrefixWithPoint);
  }
}
