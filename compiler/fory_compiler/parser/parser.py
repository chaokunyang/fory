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

"""Recursive descent parser for FDL."""

from typing import List, Optional

from fory_compiler.parser.lexer import Lexer, Token, TokenType
from fory_compiler.parser.ast import (
    Schema,
    Message,
    Enum,
    Field,
    EnumValue,
    FieldType,
    PrimitiveType,
    NamedType,
    ListType,
    MapType,
    PRIMITIVE_TYPES,
)


class ParseError(Exception):
    """Error during parsing."""

    def __init__(self, message: str, line: int, column: int):
        self.message = message
        self.line = line
        self.column = column
        super().__init__(f"Line {line}, Column {column}: {message}")


class Parser:
    """Recursive descent parser for FDL."""

    def __init__(self, tokens: List[Token]):
        self.tokens = tokens
        self.pos = 0

    @classmethod
    def from_source(cls, source: str, filename: str = "<input>") -> "Parser":
        """Create a parser from source code."""
        lexer = Lexer(source, filename)
        tokens = lexer.tokenize()
        return cls(tokens)

    def at_end(self) -> bool:
        """Check if we've reached the end of tokens."""
        return self.current().type == TokenType.EOF

    def current(self) -> Token:
        """Get the current token."""
        if self.pos >= len(self.tokens):
            return self.tokens[-1]  # Return EOF
        return self.tokens[self.pos]

    def previous(self) -> Token:
        """Get the previous token."""
        return self.tokens[self.pos - 1]

    def peek(self, offset: int = 0) -> Token:
        """Peek at a token without consuming it."""
        pos = self.pos + offset
        if pos >= len(self.tokens):
            return self.tokens[-1]  # Return EOF
        return self.tokens[pos]

    def check(self, token_type: TokenType) -> bool:
        """Check if the current token has the given type."""
        return self.current().type == token_type

    def match(self, *types: TokenType) -> bool:
        """If current token matches any of the types, consume and return True."""
        for token_type in types:
            if self.check(token_type):
                self.advance()
                return True
        return False

    def advance(self) -> Token:
        """Consume and return the current token."""
        token = self.current()
        if not self.at_end():
            self.pos += 1
        return token

    def consume(self, token_type: TokenType, message: str = None) -> Token:
        """Consume a token of the expected type, or raise an error."""
        if self.check(token_type):
            return self.advance()
        token = self.current()
        if message is None:
            message = f"Expected {token_type.name}, got {token.type.name}"
        raise ParseError(message, token.line, token.column)

    def error(self, message: str) -> ParseError:
        """Create a parse error at the current position."""
        token = self.current()
        return ParseError(message, token.line, token.column)

    def parse(self) -> Schema:
        """Parse the entire input and return a Schema."""
        package = None
        enums = []
        messages = []

        while not self.at_end():
            if self.check(TokenType.PACKAGE):
                if package is not None:
                    raise self.error("Duplicate package declaration")
                package = self.parse_package()
            elif self.check(TokenType.ENUM):
                enums.append(self.parse_enum())
            elif self.check(TokenType.MESSAGE):
                messages.append(self.parse_message())
            else:
                raise self.error(f"Unexpected token: {self.current().value}")

        return Schema(package, enums, messages)

    def parse_package(self) -> str:
        """Parse a package declaration: package foo.bar;"""
        self.consume(TokenType.PACKAGE)

        # Package name can be dotted: foo.bar.baz
        parts = [self.consume(TokenType.IDENT).value]
        while self.current().type == TokenType.IDENT or (
            self.current().type == TokenType.IDENT
        ):
            # Check for dot by looking at the raw character
            # Since we don't have a DOT token, we need to handle this differently
            # For now, just take a single identifier
            break

        self.consume(TokenType.SEMI, "Expected ';' after package name")
        return ".".join(parts)

    def parse_enum(self) -> Enum:
        """Parse an enum: enum Color @101 { ... }"""
        start = self.current()
        self.consume(TokenType.ENUM)
        name = self.consume(TokenType.IDENT, "Expected enum name").value

        # Optional type ID: @101
        type_id = None
        if self.match(TokenType.TYPE_ID):
            type_id = int(self.previous().value)

        self.consume(TokenType.LBRACE, "Expected '{' after enum name")

        values = []
        while not self.check(TokenType.RBRACE):
            values.append(self.parse_enum_value())

        self.consume(TokenType.RBRACE, "Expected '}' after enum values")

        return Enum(
            name=name,
            type_id=type_id,
            values=values,
            line=start.line,
            column=start.column,
        )

    def parse_enum_value(self) -> EnumValue:
        """Parse an enum value: NAME = 0;"""
        start = self.current()
        name = self.consume(TokenType.IDENT, "Expected enum value name").value
        self.consume(TokenType.EQUALS, "Expected '=' after enum value name")
        value_token = self.consume(TokenType.INT, "Expected integer value")
        value = int(value_token.value)
        self.consume(TokenType.SEMI, "Expected ';' after enum value")

        return EnumValue(
            name=name,
            value=value,
            line=start.line,
            column=start.column,
        )

    def parse_message(self) -> Message:
        """Parse a message: message Dog @102 { ... }"""
        start = self.current()
        self.consume(TokenType.MESSAGE)
        name = self.consume(TokenType.IDENT, "Expected message name").value

        # Optional type ID: @102
        type_id = None
        if self.match(TokenType.TYPE_ID):
            type_id = int(self.previous().value)

        self.consume(TokenType.LBRACE, "Expected '{' after message name")

        fields = []
        while not self.check(TokenType.RBRACE):
            fields.append(self.parse_field())

        self.consume(TokenType.RBRACE, "Expected '}' after message fields")

        return Message(
            name=name,
            type_id=type_id,
            fields=fields,
            line=start.line,
            column=start.column,
        )

    def parse_field(self) -> Field:
        """Parse a field: optional ref repeated Type name = 1;"""
        start = self.current()

        # Parse modifiers
        optional = self.match(TokenType.OPTIONAL)
        ref = self.match(TokenType.REF)
        repeated = self.match(TokenType.REPEATED)

        # Parse type
        field_type = self.parse_type()

        # Wrap in ListType if repeated
        if repeated:
            field_type = ListType(field_type)

        # Parse field name
        name = self.consume(TokenType.IDENT, "Expected field name").value

        # Parse field number
        self.consume(TokenType.EQUALS, "Expected '=' after field name")
        number_token = self.consume(TokenType.INT, "Expected field number")
        number = int(number_token.value)

        self.consume(TokenType.SEMI, "Expected ';' after field declaration")

        return Field(
            name=name,
            field_type=field_type,
            number=number,
            optional=optional,
            ref=ref,
            line=start.line,
            column=start.column,
        )

    def parse_type(self) -> FieldType:
        """Parse a type: int32, string, map<K, V>, or a named type."""
        if self.check(TokenType.MAP):
            return self.parse_map_type()

        if not self.check(TokenType.IDENT):
            raise self.error(f"Expected type name, got {self.current().type.name}")

        type_name = self.consume(TokenType.IDENT).value

        # Check if it's a primitive type
        if type_name in PRIMITIVE_TYPES:
            return PrimitiveType(PRIMITIVE_TYPES[type_name])

        # Otherwise it's a named type (reference to message or enum)
        return NamedType(type_name)

    def parse_map_type(self) -> MapType:
        """Parse a map type: map<KeyType, ValueType>"""
        self.consume(TokenType.MAP)
        self.consume(TokenType.LANGLE, "Expected '<' after 'map'")

        key_type = self.parse_type()

        self.consume(TokenType.COMMA, "Expected ',' between map key and value types")

        value_type = self.parse_type()

        self.consume(TokenType.RANGLE, "Expected '>' after map value type")

        return MapType(key_type, value_type)


def parse(source: str, filename: str = "<input>") -> Schema:
    """Parse FDL source code and return a Schema."""
    parser = Parser.from_source(source, filename)
    return parser.parse()
