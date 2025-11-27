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

# Adapted from tensorflow/third_party/py/ and grpc/third_party/py/

package(default_visibility=["//visibility:public"])

config_setting(
    name="windows",
    values={"cpu": "x64_windows"},
    visibility=["//visibility:public"],
)

config_setting(
    name="python3",
    flag_values = {"@rules_python//python:python_version": "PY3"}
)

cc_library(
    name = "python_lib",
    deps = select({
        ":python3": ["//_python3:_python3_lib"],
        "//conditions:default": ["not-existing.lib"],
    })
)

cc_library(
    name = "python_headers",
    deps = select({
        ":python3": ["//_python3:_python3_headers"],
        "//conditions:default": ["not-existing.headers"],
    })
)
