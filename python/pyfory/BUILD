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

package(default_visibility = ["//visibility:public"])

load("@nanobind_bazel//:build_defs.bzl", "nanobind_extension")
load("//bazel:cython_library.bzl", "pyx_library")

pyx_library(
    name = "buffer",
    srcs = glob([
        "includes/*.pxd",
        "buffer.pxd",
        "buffer.pyx",
        "__init__.py",
    ]),
    cc_kwargs = dict(
        linkstatic = 1,
    ),
    deps = [
        "//cpp/fory/util:fory_util",
    ],
    visibility = ["//visibility:public"],
)

pyx_library(
    name = "mmh3",
    srcs = glob([
        "lib/mmh3/*.pxd",
        "lib/mmh3/*.pyx",
        "lib/mmh3/__init__.py",
    ]),
    cc_kwargs = dict(
        linkstatic = 1,
    ),
    deps = [
        "//cpp/fory/thirdparty:libmmh3",
    ],
    visibility = ["//visibility:public"],
)

pyx_library(
    name = "_format",
    srcs = glob(
        [
            "__init__.py",
            "includes/*.pxd",
            "buffer.pxd",
            "*.pxi",
            "format/_format.pyx",
            "format/__init__.py",
            "format/*.pxi",
        ],
        allow_empty = True,
    ),
    cc_kwargs = dict(
        linkstatic = 1,
    ),
    deps = [
        "//cpp/fory:fory",
    ],
    visibility = ["//visibility:public"],
)

nanobind_extension(
    name = "serialization",
    srcs = ["serialization_nanobind.cc"],
    linkopts = select({
        "@platforms//os:macos": [
            "-undefined",
            "dynamic_lookup",
        ],
        "//conditions:default": [],
    }),
    deps = [
        "//cpp/fory/python:_pyfory",
        "//cpp/fory/type:fory_type",
        "//cpp/fory/util:fory_util",
        "@abseil-cpp//absl/container:flat_hash_map",
    ],
    visibility = ["//visibility:public"],
)
