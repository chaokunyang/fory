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

# To build Python C/C++ extension on Windows, we need to link to python import library pythonXY.lib
# See https://docs.python.org/3/extending/windows.html
cc_import(
    name="%{VARIETY_NAME}_lib",
    interface_library=select({
        "//:windows": ":%{VARIETY_NAME}_import_lib",
        # A placeholder for Unix platforms which makes --no_build happy.
        "//conditions:default": "not-existing.lib",
    }),
    system_provided=1,
)

cc_library(
    name="%{VARIETY_NAME}_headers",
    hdrs=[":%{VARIETY_NAME}_include"],
    deps=select({
        "//:windows": [":%{VARIETY_NAME}_lib"],
        "//conditions:default": [],
    }),
    includes=["%{VARIETY_NAME}_include"],
)

%{PYTHON_INCLUDE_GENRULE}
%{PYTHON_IMPORT_LIB_GENRULE}
