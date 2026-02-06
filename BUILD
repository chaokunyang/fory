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

load("@hedron_compile_commands//:refresh_compile_commands.bzl", "refresh_compile_commands")

genrule(
    name = "cp_fory_so",
    srcs = [
        "//python/pyfory:buffer.so",
        "//python/pyfory:lib/mmh3/mmh3.so",
        "//python/pyfory:format/_format.so",
        "//python/pyfory:serialization",
    ],
    outs = [
        "cp_fory_py_generated.out",
    ],
    cmd = """
        set -e
        set -x
        WORK_DIR=$$(pwd)
        u_name=`uname -s`
        if [ "$${u_name: 0: 4}" == "MING" ] || [ "$${u_name: 0: 4}" == "MSYS" ]
        then
            cp -f $(location //python/pyfory:buffer.so) "$$WORK_DIR/python/pyfory/buffer.pyd"
            cp -f $(location //python/pyfory:lib/mmh3/mmh3.so) "$$WORK_DIR/python/pyfory/lib/mmh3/mmh3.pyd"
            cp -f $(location //python/pyfory:format/_format.so) "$$WORK_DIR/python/pyfory/format/_format.pyd"
            cp -f $(location //python/pyfory:serialization) "$$WORK_DIR/python/pyfory/serialization.pyd"
        else
            cp -f $(location //python/pyfory:buffer.so) "$$WORK_DIR/python/pyfory"
            cp -f $(location //python/pyfory:lib/mmh3/mmh3.so) "$$WORK_DIR/python/pyfory/lib/mmh3"
            cp -f $(location //python/pyfory:format/_format.so) "$$WORK_DIR/python/pyfory/format"
            cp -f $(location //python/pyfory:serialization) "$$WORK_DIR/python/pyfory"
        fi
        echo $$(date) > $@
    """,
    local = 1,
    tags = ["no-cache"],
    visibility = ["//visibility:public"],
)

refresh_compile_commands(
    name = "refresh_compile_commands",
    exclude_headers = "all",
    exclude_external_sources = True,
)
