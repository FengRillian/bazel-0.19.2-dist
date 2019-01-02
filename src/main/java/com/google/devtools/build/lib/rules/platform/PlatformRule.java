// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.rules.platform;

import static com.google.devtools.build.lib.packages.Attribute.attr;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.analysis.RuleDefinition;
import com.google.devtools.build.lib.analysis.RuleDefinitionEnvironment;
import com.google.devtools.build.lib.analysis.platform.ConstraintValueInfo;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.util.FileTypeSet;

/** Rule definition for {@link Platform}. */
public class PlatformRule implements RuleDefinition {
  public static final String RULE_NAME = "platform";
  public static final String CONSTRAINT_VALUES_ATTR = "constraint_values";
  public static final String REMOTE_EXECUTION_PROPS_ATTR = "remote_execution_properties";
  static final String HOST_PLATFORM_ATTR = "host_platform";
  static final String TARGET_PLATFORM_ATTR = "target_platform";
  static final String CPU_CONSTRAINTS_ATTR = "cpu_constraints";
  static final String OS_CONSTRAINTS_ATTR = "os_constraints";

  @Override
  public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
    /* <!-- #BLAZE_RULE(platform).NAME -->
    <!-- #END_BLAZE_RULE.NAME --> */
    return builder
        /* <!-- #BLAZE_RULE(platform).ATTRIBUTE(constraint_values) -->
        The constraint_values that define this platform.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(
            attr(CONSTRAINT_VALUES_ATTR, BuildType.LABEL_LIST)
                .allowedFileTypes(FileTypeSet.NO_FILE)
                .mandatoryProviders(ImmutableList.of(ConstraintValueInfo.PROVIDER.id())))

        /* <!-- #BLAZE_RULE(platform).ATTRIBUTE(remote_execution_properties) -->
        A string used to configure a remote execution platform. Actual builds make no attempt to
        interpret this, it is treated as opaque data that can be used by a specific SpawnRunner.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(attr(REMOTE_EXECUTION_PROPS_ATTR, Type.STRING))

        // Undocumented. Indicates that this platform should auto-configure the platform constraints
        // based on the current host OS and CPU settings.
        .add(
            attr(HOST_PLATFORM_ATTR, Type.BOOLEAN)
                .value(false)
                .undocumented("Should only be used by internal packages."))
        // Undocumented. Indicates that this platform should auto-configure the platform constraints
        // based on the current OS and CPU settings.
        .add(
            attr(TARGET_PLATFORM_ATTR, Type.BOOLEAN)
                .value(false)
                .undocumented("Should only be used by internal packages."))
        // Undocumented. Indicates to the rule which constraint_values to use for automatic CPU
        // mapping.
        .add(
            attr(CPU_CONSTRAINTS_ATTR, BuildType.LABEL_LIST)
                .allowedFileTypes(FileTypeSet.NO_FILE)
                .mandatoryProviders(ImmutableList.of(ConstraintValueInfo.PROVIDER.id()))
                .undocumented("Should only be used by internal packages."))
        // Undocumented. Indicates to the rule which constraint_values to use for automatic CPU
        // mapping.
        .add(
            attr(OS_CONSTRAINTS_ATTR, BuildType.LABEL_LIST)
                .allowedFileTypes(FileTypeSet.NO_FILE)
                .mandatoryProviders(ImmutableList.of(ConstraintValueInfo.PROVIDER.id()))
                .undocumented("Should only be used by internal packages."))
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return Metadata.builder()
        .name(RULE_NAME)
        .ancestors(PlatformBaseRule.class)
        .factoryClass(Platform.class)
        .build();
  }
}
/*<!-- #BLAZE_RULE (NAME = platform, TYPE = OTHER, FAMILY = Platform)[GENERIC_RULE] -->

<p>This rule defines a platform, as a collection of constraint_values.

<h4 id="platform_examples">Examples</h4>
<p>
  This defines two possible platforms, each targeting a different CPU type.
</p>
<pre class="code">
constraint_setting(name="cpu")
constraint_value(
    name="arm64",
    constraint_setting=":cpu")
constraint_value(
    name="k8",
    constraint_setting=":cpu")
platform(
    name="mobile_device",
    constraint_values = [
        ":arm64",
    ])
platform(
    name="devel",
    constraint_values = [
        ":k8",
    ])
</pre>

<!-- #END_BLAZE_RULE -->*/
