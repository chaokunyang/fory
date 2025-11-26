load("@rules_cc//cc:defs.bzl", "cc_library", "cc_test")

cc_library(
    name = "fory_row_format",
    srcs = glob(["*.cc"], exclude=["*test.cc"]),
    hdrs = glob(["*.h"]),
    strip_include_prefix = "/cpp",
    deps = [
      "//cpp/fory/util:fory_util",
      "//cpp/fory/type:fory_type"
    ],
    visibility = ["//visibility:public"],
)

cc_test(
    name = "row_test",
    srcs = ["row_test.cc"],
    deps = [
        ":fory_row_format",
        "@com_google_googletest//:gtest",
    ],
)
