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

"""Tests for FDL package options and qualified type names."""

import pytest
from pathlib import Path

from fory_compiler.parser.lexer import Lexer
from fory_compiler.parser.parser import Parser
from fory_compiler.generators.java import JavaGenerator
from fory_compiler.generators.go import GoGenerator
from fory_compiler.generators.base import GeneratorOptions


class TestDottedPackageName:
    """Tests for dotted package name parsing."""

    def test_simple_package(self):
        """Test parsing a simple package name."""
        source = '''
        package foo;
        message Bar {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.package == "foo"

    def test_dotted_package(self):
        """Test parsing a dotted package name."""
        source = '''
        package foo.bar;
        message Baz {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.package == "foo.bar"

    def test_deeply_dotted_package(self):
        """Test parsing a deeply nested package name."""
        source = '''
        package com.example.payment.v1;
        message Payment {
            string id = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.package == "com.example.payment.v1"


class TestFileOptions:
    """Tests for file-level option parsing."""

    def test_java_package_option(self):
        """Test parsing java_package option."""
        source = '''
        package payment;
        option java_package = "com.mycorp.payment.v1";
        message Payment {
            string id = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.package == "payment"
        assert schema.get_option("java_package") == "com.mycorp.payment.v1"

    def test_go_package_option(self):
        """Test parsing go_package option."""
        source = '''
        package payment;
        option go_package = "github.com/mycorp/apis/gen/payment/v1;paymentv1";
        message Payment {
            string id = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.package == "payment"
        assert schema.get_option("go_package") == "github.com/mycorp/apis/gen/payment/v1;paymentv1"

    def test_multiple_options(self):
        """Test parsing multiple file-level options."""
        source = '''
        package payment;
        option java_package = "com.mycorp.payment.v1";
        option go_package = "github.com/mycorp/payment/v1;paymentv1";
        message Payment {
            string id = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.get_option("java_package") == "com.mycorp.payment.v1"
        assert schema.get_option("go_package") == "github.com/mycorp/payment/v1;paymentv1"

    def test_option_with_boolean_value(self):
        """Test parsing option with boolean value."""
        source = '''
        package test;
        option deprecated = true;
        message Foo {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.get_option("deprecated") is True

    def test_option_with_integer_value(self):
        """Test parsing option with integer value."""
        source = '''
        package test;
        option version = 1;
        message Foo {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        assert schema.get_option("version") == 1


class TestQualifiedTypeNames:
    """Tests for package-qualified type references."""

    def test_qualified_type_in_field(self):
        """Test using qualified type names in field definitions."""
        source = '''
        package myapp;
        message SearchResponse {
            message Result {
                string url = 1;
            }
        }
        message SearchRequest {
            SearchResponse.Result cached_result = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()
        errors = schema.validate()

        assert len(errors) == 0
        request = schema.messages[1]
        assert request.fields[0].field_type.name == "SearchResponse.Result"


class TestJavaPackageGeneration:
    """Tests for Java package generation with java_package option."""

    def test_java_package_option_used(self):
        """Test that java_package option is used in generated Java code."""
        source = '''
        package payment;
        option java_package = "com.mycorp.payment.v1";
        message Payment {
            string id = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        options = GeneratorOptions(output_dir=Path("/tmp"))
        generator = JavaGenerator(schema, options)

        files = generator.generate()

        # Check that files are in the java_package path
        payment_file = next(f for f in files if "Payment.java" in f.path)
        assert "com/mycorp/payment/v1/Payment.java" == payment_file.path
        assert "package com.mycorp.payment.v1;" in payment_file.content

    def test_java_package_fallback_to_fdl_package(self):
        """Test fallback to FDL package when java_package is not specified."""
        source = '''
        package com.example.models;
        message User {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        options = GeneratorOptions(output_dir=Path("/tmp"))
        generator = JavaGenerator(schema, options)

        files = generator.generate()

        user_file = next(f for f in files if "User.java" in f.path)
        assert "com/example/models/User.java" == user_file.path
        assert "package com.example.models;" in user_file.content


class TestGoPackageGeneration:
    """Tests for Go package generation with go_package option."""

    def test_go_package_with_semicolon(self):
        """Test go_package option with explicit package name."""
        source = '''
        package payment;
        option go_package = "github.com/mycorp/apis/gen/payment/v1;paymentv1";
        message Payment {
            string id = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        options = GeneratorOptions(output_dir=Path("/tmp"))
        generator = GoGenerator(schema, options)

        import_path, package_name = generator.get_go_package_info()
        assert import_path == "github.com/mycorp/apis/gen/payment/v1"
        assert package_name == "paymentv1"

        files = generator.generate()
        go_file = files[0]
        assert "package paymentv1" in go_file.content

    def test_go_package_without_semicolon(self):
        """Test go_package option without explicit package name."""
        source = '''
        package payment;
        option go_package = "github.com/mycorp/apis/payment/v1";
        message Payment {
            string id = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        options = GeneratorOptions(output_dir=Path("/tmp"))
        generator = GoGenerator(schema, options)

        import_path, package_name = generator.get_go_package_info()
        assert import_path == "github.com/mycorp/apis/payment/v1"
        assert package_name == "v1"

    def test_go_package_fallback_to_fdl_package(self):
        """Test fallback to FDL package when go_package is not specified."""
        source = '''
        package com.example.models;
        message User {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        options = GeneratorOptions(output_dir=Path("/tmp"))
        generator = GoGenerator(schema, options)

        import_path, package_name = generator.get_go_package_info()
        assert import_path is None
        assert package_name == "models"


class TestNamespaceConsistency:
    """Tests for namespace consistency across languages."""

    def test_java_uses_fdl_package_for_namespace(self):
        """Test that Java uses FDL package for type namespace, not java_package."""
        source = '''
        package myapp.models;
        option java_package = "com.mycorp.generated.models";
        message User {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        options = GeneratorOptions(output_dir=Path("/tmp"))
        generator = JavaGenerator(schema, options)

        files = generator.generate()
        registration_file = next(f for f in files if "Registration" in f.path)

        # Should use myapp.models (FDL package) for namespace, not com.mycorp.generated.models
        assert '"myapp.models"' in registration_file.content

    def test_go_uses_fdl_package_for_namespace(self):
        """Test that Go uses FDL package for type namespace, not go_package."""
        source = '''
        package myapp.models;
        option go_package = "github.com/mycorp/generated;genmodels";
        message User {
            string name = 1;
        }
        '''
        lexer = Lexer(source)
        parser = Parser(lexer.tokenize())
        schema = parser.parse()

        options = GeneratorOptions(output_dir=Path("/tmp"))
        generator = GoGenerator(schema, options)

        files = generator.generate()
        go_file = files[0]

        # Should use myapp.models (FDL package) for namespace
        assert '"myapp.models.User"' in go_file.content


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
