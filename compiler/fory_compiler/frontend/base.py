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

"""Frontend base classes for Fory IDL compilation."""

from abc import ABC, abstractmethod
from pathlib import Path
from typing import List

from fory_compiler.ir.ast import Schema


class FrontendError(Exception):
    """Error during frontend parsing or translation."""

    def __init__(self, message: str, file: str, line: int, column: int):
        super().__init__(f"{file}:{line}:{column}: {message}")
        self.message = message
        self.file = file
        self.line = line
        self.column = column


class BaseFrontend(ABC):
    """Base class for all IDL frontends."""

    extensions: List[str] = []

    @abstractmethod
    def parse(self, source: str, filename: str = "<input>") -> Schema:
        """Parse source and return a Fory IR schema."""

    def parse_file(self, path: Path) -> Schema:
        """Parse a file and return a Fory IR schema."""
        return self.parse(path.read_text(), str(path))

    def supports_file(self, path: Path) -> bool:
        """Return True if this frontend handles the file extension."""
        return path.suffix.lower() in self.extensions
