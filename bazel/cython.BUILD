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

# Adapted from tensorflow/third_party/cython.BUILD

py_library(
    name="cython_lib",
    srcs=glob(
        ["Cython/**/*.py"],
        exclude=[
            "**/Tests/*.py",
        ],
    ) + ["cython.py"],
    data=glob([
        "Cython/**/*.pyx",
        "Cython/Utility/*.*",
        "Cython/Includes/**/*.pxd",
    ]),
    srcs_version="PY2AND3",
    visibility=["//visibility:public"],
)

# May not be named "cython", since that conflicts with Cython/ on OSX
py_binary(
    name="cython_binary",
    srcs=["cython.py"],
    main="cython.py",
    srcs_version="PY2AND3",
    visibility=["//visibility:public"],
    deps=["cython_lib"],
)
