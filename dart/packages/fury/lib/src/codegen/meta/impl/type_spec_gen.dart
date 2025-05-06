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
import 'package:fury/src/codegen/meta/impl/type_immutable.dart';
import 'package:fury/src/codegen/meta/lib_import_pack.dart';
import 'package:fury/src/codegen/tool/codegen_tool.dart';
import 'package:fury/src/const/obj_type.dart';

class TypeSpecGen extends GenExport{
  final TypeImmutable immutablePart;
  late bool nullable;
  final List<TypeSpecGen> genericsArgs; // An empty list indicates no generic arguments

  String? _fullName;
  String? _fullNameNoLastNull;
  String? _shortName;

  TypeSpecGen(
    this.immutablePart,
    this.nullable,
    this.genericsArgs,
  );

  bool get independent => immutablePart.independent;

  String getFullName(LibImportPack imports) {
    if (_fullName != null) return _fullName!;
    _fullName = _equipFullTypeName(imports);
    return _fullName!;
  }

  String getFullNameNoLastNull(LibImportPack imports){
    if (_fullNameNoLastNull != null) return _fullNameNoLastNull!;
    if (!nullable) {
      return getFullName(imports);
    }
    _fullNameNoLastNull = _equipFullTypeName(imports, false);
    return _fullNameNoLastNull!;
  }

  String getShortName(LibImportPack imports) {
    if (_shortName != null) return _shortName!;
    String? prefix = imports.getPrefixByLibId(immutablePart.typeLibId);
    StringBuffer buf = StringBuffer();
    if (prefix != null){
      buf.write(prefix);
      buf.write(".");
    }
    buf.write(immutablePart.name);
    _shortName = buf.toString();
    return _shortName!;
  }

  String _equipFullTypeName(LibImportPack imports,[bool containLastNull = true]){
    String? importPrefix = imports.getPrefixByLibId(immutablePart.typeLibId);
    // print("equip full type name: $name");
    StringBuffer buf = StringBuffer();
    if (importPrefix != null){
      buf.write(importPrefix);
      buf.write(".");
    }
    buf.write(immutablePart.name);
    if (genericsArgs.isNotEmpty){
      buf.write("<");
      for (int i = 0; i < genericsArgs.length - 1; ++i){
        buf.write(genericsArgs[i].getFullName(imports));
        buf.write(", ");
      }
      buf.write(genericsArgs.last.getFullName(imports));
      buf.write(">");
    }
    if (nullable && containLastNull){
      buf.write("?");
    }
    return buf.toString();
  }

  @override
  void genCodeReqImportsInfo(StringBuffer buf, LibImportPack imports, String? dartCorePrefixWithPoint, [int indentLevel = 0]) {
    int totalIndent = indentLevel * CodegenStyle.indent;
    int nextTotalIndent = totalIndent + CodegenStyle.indent;

    // declare part
    CodegenTool.writeIndent(buf, totalIndent);
    buf.write("TypeSpec(\n");

    // FuryTypeSpec::type
    String? prefix = imports.getPrefixByLibId(immutablePart.typeLibId);
    CodegenTool.writeIndent(buf, nextTotalIndent);
    if (prefix != null){
      buf.write(prefix);
      buf.write(".");
    }
    buf.write(immutablePart.name);
    buf.write(",\n");

    // FuryTypeSpec::objType part
    CodegenTool.writeIndent(buf, nextTotalIndent);
    buf.write("ObjType.");
    buf.write(immutablePart.objType.name);
    buf.write(",\n");

    // // FuryTypeSpec::type part
    // String? importPrefix = prefixReference.getPrefixByLibId(immutablePart.typeLibId);
    // GenCodeTool.writeIndent(buf, nextTotalIndent);
    // if (importPrefix != null){
    //   buf.write(importPrefix);
    //   buf.write(".");
    // }
    // buf.write(immutablePart.name);
    // buf.write(",\n");

    // FuryTypeSpec::nullable part
    CodegenTool.writeIndent(buf, nextTotalIndent);
    buf.write(nullable ? "true,\n" : "false,\n");

    CodegenTool.writeIndent(buf, nextTotalIndent);
    buf.write(immutablePart.certainForSer ? "true,\n" : "false,\n");

    if (immutablePart.objType != ObjType.NAMED_ENUM) {
      // FuryTypeSpec::enumSpec part
      CodegenTool.writeIndent(buf, nextTotalIndent);
      buf.write("null,\n");
    }else {
      if (prefix != null){
        buf.write(prefix);
        buf.write(".");
      }
      buf.write('\$');
      buf.write(immutablePart.name);
      buf.write(",\n");
    }

    // FuryTypeSpec::genericsArgs part
    CodegenTool.writeIndent(buf, nextTotalIndent);
    buf.write("const [\n");
    for (var arg in genericsArgs){
      arg.genCodeReqImportsInfo(buf, imports, dartCorePrefixWithPoint,indentLevel + 1);
    }
    CodegenTool.writeIndent(buf, nextTotalIndent);
    buf.write("],\n");

    // end part
    CodegenTool.writeIndent(buf, totalIndent);
    buf.write("),\n");
  }
}
