# Standard constraint_setting and constraint_values to be used in platforms.

package(
    default_visibility = ["//visibility:public"],
)

# These match values in //src/main/java/com/google/devtools/build/lib/util:CPU.java
constraint_setting(name = "cpu")

constraint_value(
    name = "x86_32",
    constraint_setting = ":cpu",
)

constraint_value(
    name = "x86_64",
    constraint_setting = ":cpu",
)

constraint_value(
    name = "ppc",
    constraint_setting = ":cpu",
)

constraint_value(
    name = "arm",
    constraint_setting = ":cpu",
)

constraint_value(
    name = "aarch64",
    constraint_setting = ":cpu",
)

constraint_value(
    name = "s390x",
    constraint_setting = ":cpu",
)

# These match values in //src/main/java/com/google/devtools/build/lib/util:OS.java
constraint_setting(name = "os")

constraint_value(
    name = "osx",
    constraint_setting = ":os",
)

constraint_value(
    name = "ios",
    constraint_setting = ":os",
)

constraint_value(
    name = "freebsd",
    constraint_setting = ":os",
)

constraint_value(
    name = "android",
    constraint_setting = ":os",
)

constraint_value(
    name = "linux",
    constraint_setting = ":os",
)

constraint_value(
    name = "windows",
    constraint_setting = ":os",
)

# A constraint that can only be matched by the autoconfigured platforms.
constraint_setting(
    name = "autoconfigure_status",
    visibility = ["//visibility:private"],
)

constraint_value(
    name = "autoconfigured",
    constraint_setting = ":autoconfigure_status",
    visibility = [
        "@bazel_tools//:__subpackages__",
        "@local_config_cc//:__subpackages__",
    ],
)

# A default platform with nothing defined.
platform(name = "default_platform")

# A default platform referring to the host system. This only exists for
# internal build configurations, and so shouldn't be accessed by other packages.
platform(
    name = "host_platform",
    constraint_values = [
        ":autoconfigured",
    ],
    cpu_constraints = [
        ":x86_32",
        ":x86_64",
        ":ppc",
        ":arm",
        ":aarch64",
        ":s390x",
    ],
    host_platform = True,
    os_constraints = [
        ":osx",
        ":freebsd",
        ":linux",
        ":windows",
    ],
)

platform(
    name = "target_platform",
    constraint_values = [
        ":autoconfigured",
    ],
    cpu_constraints = [
        ":x86_32",
        ":x86_64",
        ":ppc",
        ":arm",
        ":aarch64",
        ":s390x",
    ],
    os_constraints = [
        ":osx",
        ":freebsd",
        ":linux",
        ":windows",
    ],
    target_platform = True,
)
