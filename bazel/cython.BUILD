# Adapted from tensorflow/third_party/cython.BUILD
# Using filegroup to export Cython files for direct Python invocation
# This avoids rules_python hermetic toolchain issues in containers.

# Export all Cython files needed to run the compiler
filegroup(
    name = "cython_srcs",
    srcs = glob(
        [
            "Cython/**/*.py",
            "Cython/**/*.pyx",
            "Cython/**/*.pxd",
            "Cython/**/*.h",
            "Cython/**/*.c",
            "cython.py",
        ],
        exclude = ["**/Tests/**"],
        allow_empty = True,
    ),
    visibility = ["//visibility:public"],
)

# Export cython.py separately for $(location) usage
exports_files(["cython.py"], visibility = ["//visibility:public"])
