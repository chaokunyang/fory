# Adapted from tensorflow/third_party/py/ and grpc/third_party/py/

package(default_visibility=["//visibility:public"])

config_setting(
    name="windows",
    values={"cpu": "x64_windows"},
    visibility=["//visibility:public"],
)

# With Bazel 8 and bzlmod, we simplify the Python configuration.
# Python 3 is now the only supported version.

cc_library(
    name = "python_lib",
    deps = ["//_python3:_python3_lib"],
)

cc_library(
    name = "python_headers",
    deps = ["//_python3:_python3_headers"],
)
