---
layout: documentation
title: Toolchains
---

# Toolchains

- [Overview](#overview)
- [Toolchain Resolution](#toolchain-resolution)
- [Defining a toolchain](#defining-a-toolchain)
   - [Creating a toolchain rule](#creating-a-toolchain-rule)
   - [Creating a toolchain definition](#creating-a-toolchain-definition)
   - [Registering a toolchain](#registering-a-toolchain)
- [Using a toolchain in a new rule](#using-a-toolchain-in-a-rule)
- [Debugging a toolchain](#debugging-a-toolchain)

## Overview

A *Bazel toolchain* is a configuration
[provider](skylark/rules.html#providers)
that tells a build rule what build tools, such as compilers and linkers, to use
and how to configure them using parameters defined by the rule's creator.

When a build runs, Bazel performs toolchain resolution based on the specified
[execution and target platforms](platforms.html)
to determine and apply the
toolchain most appropriate to that build. It does so by matching the
[constraints](platforms.html#defining-a-platform)
specified in the project's
`BUILD` file(s) with the constraints specified in the toolchain definition.

## Toolchain Resolution

Inputs to the resolution mechanism include the required toolchain types (including
none) and the target platform. The resolution mechanism outputs a single execution
platform and a toolchain type-to-toolchain map. Toolchain resolution works as follows:

1. Collect all available execution platforms, including the host. This is an ordered list.
   - Execution platforms are collected from the following sources:
     1. Any extra execution platforms given on the command line via the
     [`--extra_execution_platforms`](https://docs.bazel.build/versions/master/command-line-reference.html#flag--extra_execution_platforms)
     flag.
     2. Any execution platforms in the `WORKSPACE` file, via the
     [`register_execution_platforms`](https://docs.bazel.build/versions/master/skylark/lib/globals.html#register_execution_platforms)
     function.
     3. The host platform.
2. Collect all available toolchains. This is also an ordered list.
   - Toolchains are collected from the following sources:
     1. Any extra toolchains given on the command line via the
     [`--extra_toolchains`](https://docs.bazel.build/versions/master/command-line-reference.html#flag--extra_toolchains)
     flag.
     2. Any toolchains in the `WORKSPACE` file, via the
     [`register_toolchains`](https://docs.bazel.build/versions/master/skylark/lib/globals.html#register_toolchains)
     function.
3. For each toolchain type and execution platform, select the first toolchain that matches the execution platform and target platform.
4. With the full set of toolchains and execution platforms for each type, select the first execution platform that can satisfy all toolchain types.

Execution platforms listed first are preferred, and toolchains listed first are preferred.
Every configured target has the same execution platform for all actions that target generates.

Because Bazel always selects the first matching toolchain, order the toolchains
by preference if you expect the possibility of multiple matches.

**Note:** Some Bazel rules do not yet support toolchain resolution.

## Defining a toolchain

Defining a toolchain requires the following:

*  **Toolchain rule** - a rule invoked in a custom build or test rule that
   specifies the build tool configuration options particular to the toolchain
   and supported
   [platforms](platforms.html)
   (for example, [`go_toolchain`](https://github.com/bazelbuild/rules_go/blob/master/go/private/go_toolchain.bzl)).
   This rule must return a
   [`ToolchainInfo` provider](skylark/lib/platform_common.html#ToolchainInfo).
   The toolchain rule is lazily instantiated by Bazel on an as-needed basis.
   Because of this, a toolchain rule's dependencies can be as complex as needed,
   including reliance on remote repositories, without affecting builds that do
   not use them.

*  **Toolchain definition** - tells Bazel which
   [platform constraints](platforms.html#defining-a-platform)
   apply to the toolchain using the `toolchain()` rule. This rule must specify a
   unique toolchain type label, which is used as input during toolchain
   resolution.

*  **Toolchain registration** - makes the toolchain available to a Bazel project
   using the `register_toolchains()` function in the project's `WORKSPACE` file.

### Creating a toolchain rule

Toolchain rules are rules that create and return providers. To define a
toolchain rule, first determine the information that the new rule will require.

In the example below, we are adding support for a new programming language, so
we need to specify paths to the compiler and the system libraries, plus a flag
that determines the CPU architecture for which Bazel builds the output. Let's
call this file `my_toolchain.bzl`.

```python
def _my_toolchain_impl(ctx):
  toolchain = platform_common.ToolchainInfo(
    compiler = ctx.attr.compiler,
    system_lib = ctx.attr.system_lib,
    arch_flags = ctx.attr.arch_flags,
  )
  return [toolchain]

my_toolchain = rule(
  _my_toolchain_impl,
  attrs = {
    'compiler': attr.string(),
    'system_lib': attr.string(),
    'arch_flags': attr.string_list(),
  })
```

An example invocation of the rule that should go in a `BUILD` file looks as
follows:

```python
load('//path/to:my_toolchain.bzl', 'my_toolchain')

my_toolchain(
  name = 'linux_toolchain_impl',
  compiler = '@remote_linux_repo//compiler:compiler_binary',
  system_lib = '@remote_linux_repo//library:system_library',
  arch_flags = [
    '--arch=Linux',
    '--debug_everything',
  ]
)

my_toolchain(
  name = 'darwin_toolchain_impl',
  compiler = '@remote_darwin_repo//compiler:compiler_binary',
  system_lib = '@remote_darwin_repo//library:system_library',
  arch_flags = [
    '--arch=Darwin',
    #'--debug_everything', # --debug_everything currently broken on Darwin
  ]
)
```

### Creating a toolchain definition

The toolchain definition is an instance of the `toolchain()` rule that specifies
the toolchain type, execution and target constraints, and the label of the
actual rule-specific toolchain. The use of the `toolchain()` rule enables the
lazy loading of toolchains.

Below is an example toolchain definition which should go in a `BUILD` file:

```python
toolchain_type(name = 'my_toolchain_type')

toolchain(
  name = 'linux_toolchain',
  toolchain_type = '//path/to:my_toolchain_type',
  exec_compatible_with = [
    '@bazel_tools//platforms:linux',
    '@bazel_tools//platforms:x86_64'],
  target_compatible_with = [
    '@bazel_tools//platforms:linux',
    '@bazel_tools//platforms:x86_64'],
  toolchain = ':linux_toolchain_impl',
)
```

### Registering a toolchain

Once the toolchain rule and definition exist, register the toolchain to make
Bazel aware of it. You can register a toolchain either via the project's
`WORKSPACE` file or specify it in the `--extra_toolchains` flag.

Below is an example toolchain registration in a `WORKSPACE` file:

```python
register_toolchains(
  '//path/to:linux_toolchain',
  '//path/to:darwin_toolchain',
)
```

## Using a toolchain in a rule

To use a toolchain in a rule, add the toolchain type to the rule
definition. For example add the following in your `my_toolchain.bzl`:

```python
my_library = rule(
  ...
  toolchains = ['//path/to:my_toolchain_type'],
  implementation = _my_library_impl,
  ...)
```

When using the `ctx.toolchains` rule, Bazel checks the execution and target
platforms, and select the first toolchain that matches. The rule implementation
which should go in `my_toolchain.bzl` can then access the toolchain as follows:

```python
def _my_library_impl(ctx):
  toolchain = ctx.toolchains['//path/to:my_toolchain_type']
  command = '%s -l %s %s' % (toolchain.compiler, toolchain.system_lib, toolchain.arch_flags)
  ...
```

## Debugging a toolchain

When adding toolchain support to an existing rule, use the
`--toolchain_resolution_debug` flag to make toolchain resolution verbose. Bazel
will output names of toolchains it is checking and skipping during the
resolution process.
