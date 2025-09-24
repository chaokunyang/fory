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

"""Bazel rules for nanobind extensions."""

def nanobind_extension(name, srcs, deps = [], copts = [], linkopts = [], **kwargs):
    """Create a nanobind Python extension module.

    Args:
        name: Name of the extension module
        srcs: Source files (.cpp, .h)
        deps: Dependencies (including C++ libraries)
        copts: Compiler options
        linkopts: Linker options
        **kwargs: Additional arguments passed to cc_binary
    """

    # Platform-specific linker options
    platform_linkopts = select({
        "@platforms//os:macos": ["-undefined", "dynamic_lookup"],
        "@platforms//os:linux": ["-Wl,-Bsymbolic"],
        "//conditions:default": [],
    })

    # Default compiler options for nanobind
    default_copts = [
        "-fvisibility=hidden",
        "-std=c++17",
        "-O3",
        "-DNANOBIND_BUILD",
        "-ffast-math",
        "-march=native",  # Enable native CPU optimizations
    ]

    native.cc_binary(
        name = name,
        srcs = srcs,
        copts = default_copts + copts,
        linkopts = platform_linkopts + linkopts,
        linkshared = True,
        deps = deps + [
            "@nanobind//:nanobind",
            "@local_config_python//:python_headers",
        ],
        **kwargs
    )

def nanobind_library(name, srcs, hdrs = [], deps = [], copts = [], **kwargs):
    """Create a C++ library that can be used with nanobind extensions.

    Args:
        name: Name of the library
        srcs: Source files (.cpp)
        hdrs: Header files (.h, .hpp)
        deps: Dependencies
        copts: Compiler options
        **kwargs: Additional arguments passed to cc_library
    """

    default_copts = [
        "-std=c++17",
        "-O3",
        "-ffast-math",
        "-march=native",
        "-DNANOBIND_BUILD",
    ]

    native.cc_library(
        name = name,
        srcs = srcs,
        hdrs = hdrs,
        copts = default_copts + copts,
        deps = deps + [
            "@nanobind//:nanobind",
        ],
        **kwargs
    )