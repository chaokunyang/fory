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

"""CLI entry point for the FDL compiler."""

import argparse
import sys
from pathlib import Path
from typing import List, Optional

from fory_compiler.parser.lexer import Lexer, LexerError
from fory_compiler.parser.parser import Parser, ParseError
from fory_compiler.generators.base import GeneratorOptions
from fory_compiler.generators import GENERATORS


def parse_args(args: Optional[List[str]] = None) -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        prog="fory",
        description="FDL (Fory Definition Language) compiler",
    )

    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # compile command
    compile_parser = subparsers.add_parser(
        "compile",
        help="Compile FDL files to language-specific code",
    )

    compile_parser.add_argument(
        "files",
        nargs="+",
        type=Path,
        metavar="FILE",
        help="FDL files to compile",
    )

    compile_parser.add_argument(
        "--lang",
        type=str,
        default="all",
        help="Comma-separated list of target languages (java,python,cpp,rust,go). Default: all",
    )

    compile_parser.add_argument(
        "--output",
        "-o",
        type=Path,
        default=Path("./generated"),
        help="Output directory. Default: ./generated",
    )

    compile_parser.add_argument(
        "--package",
        type=str,
        default=None,
        help="Override package name from FDL file",
    )

    return parser.parse_args(args)


def get_languages(lang_arg: str) -> List[str]:
    """Parse the language argument into a list of languages."""
    if lang_arg == "all":
        return list(GENERATORS.keys())

    languages = [l.strip().lower() for l in lang_arg.split(",")]

    # Validate languages
    invalid = [l for l in languages if l not in GENERATORS]
    if invalid:
        print(f"Error: Unknown language(s): {', '.join(invalid)}", file=sys.stderr)
        print(f"Available: {', '.join(GENERATORS.keys())}", file=sys.stderr)
        sys.exit(1)

    return languages


def compile_file(
    file_path: Path,
    languages: List[str],
    output_dir: Path,
    package_override: Optional[str] = None,
) -> bool:
    """Compile a single FDL file."""
    print(f"Compiling {file_path}...")

    # Read source
    try:
        source = file_path.read_text()
    except OSError as e:
        print(f"Error reading {file_path}: {e}", file=sys.stderr)
        return False

    # Parse
    try:
        lexer = Lexer(source, str(file_path))
        tokens = lexer.tokenize()
        parser = Parser(tokens)
        schema = parser.parse()
    except (LexerError, ParseError) as e:
        print(f"Error: {e}", file=sys.stderr)
        return False

    # Validate schema
    errors = schema.validate()
    if errors:
        for error in errors:
            print(f"Error: {error}", file=sys.stderr)
        return False

    # Generate code for each language
    for lang in languages:
        lang_output = output_dir / lang
        options = GeneratorOptions(
            output_dir=lang_output,
            package_override=package_override,
        )

        generator_class = GENERATORS[lang]
        generator = generator_class(schema, options)
        files = generator.generate()
        generator.write_files(files)

        for f in files:
            print(f"  Generated: {lang_output / f.path}")

    return True


def cmd_compile(args: argparse.Namespace) -> int:
    """Handle the compile command."""
    languages = get_languages(args.lang)

    # Create output directory
    args.output.mkdir(parents=True, exist_ok=True)

    success = True
    for file_path in args.files:
        if not file_path.exists():
            print(f"Error: File not found: {file_path}", file=sys.stderr)
            success = False
            continue

        if not compile_file(file_path, languages, args.output, args.package):
            success = False

    return 0 if success else 1


def main(args: Optional[List[str]] = None) -> int:
    """Main entry point."""
    parsed = parse_args(args)

    if parsed.command is None:
        print("Usage: fory <command> [options]", file=sys.stderr)
        print("Commands: compile", file=sys.stderr)
        print("Use 'fory <command> --help' for more information", file=sys.stderr)
        return 1

    if parsed.command == "compile":
        return cmd_compile(parsed)

    return 0


if __name__ == "__main__":
    sys.exit(main())
