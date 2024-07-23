load("@rules_java//java:defs.bzl", "java_binary")

package(default_visibility = ["//visibility:public"])

DEPS = [
    "dependencies/gson-2.11.0.jar",
    "dependencies/hunspell-2.2.0.jar",
    "dependencies/jfasttext-0.5-jar-with-dependencies.jar",
]

java_binary(
    name = "FastSpell",
    srcs = glob(["src/main/java/com/vectara/*.java"]),
    main_class = "com.vectara.FastSpell",
    resources = glob(["src/main/resources/**"]),
    deps = DEPS,
)
