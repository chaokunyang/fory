# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""FlatBuffers frontend."""

from fory_compiler.frontend.base import BaseFrontend, FrontendError
from fory_compiler.frontend.fbs.lexer import Lexer, LexerError
from fory_compiler.frontend.fbs.parser import Parser, ParseError
from fory_compiler.frontend.fbs.translator import FbsTranslator
from fory_compiler.ir.ast import Schema


class FBSFrontend(BaseFrontend):
    """Frontend for FlatBuffers (.fbs)."""

    extensions = [".fbs"]

    def parse(self, source: str, filename: str = "<input>") -> Schema:
        try:
            lexer = Lexer(source, filename)
            tokens = lexer.tokenize()
            parser = Parser(tokens, filename)
            fbs_schema = parser.parse()
        except (LexerError, ParseError) as exc:
            raise FrontendError(exc.message, filename, exc.line, exc.column) from exc

        translator = FbsTranslator(fbs_schema)
        schema = translator.translate()

        return schema


__all__ = ["FBSFrontend", "Lexer", "Parser", "LexerError", "ParseError"]
