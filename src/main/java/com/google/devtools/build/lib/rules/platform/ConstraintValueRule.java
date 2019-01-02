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
import com.google.devtools.build.lib.analysis.platform.ConstraintSettingInfo;
import com.google.devtools.build.lib.packages.BuildType;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.util.FileTypeSet;

/** Rule definition for {@link ConstraintValue}. */
public class ConstraintValueRule implements RuleDefinition {
  public static final String RULE_NAME = "constraint_value";
  public static final String CONSTRAINT_SETTING_ATTR = "constraint_setting";

  @Override
  public RuleClass build(RuleClass.Builder builder, RuleDefinitionEnvironment env) {
    return builder
        /* <!-- #BLAZE_RULE(constraint_value).ATTRIBUTE(constraint_setting) -->
        The constraint_setting rule this value is applied to.
        <!-- #END_BLAZE_RULE.ATTRIBUTE --> */
        .add(
            attr(CONSTRAINT_SETTING_ATTR, BuildType.LABEL)
                .mandatory()
                .allowedRuleClasses(ConstraintSettingRule.RULE_NAME)
                .allowedFileTypes(FileTypeSet.NO_FILE)
                .mandatoryProviders(
                    ImmutableList.of(ConstraintSettingInfo.PROVIDER.id())))
        .build();
  }

  @Override
  public Metadata getMetadata() {
    return Metadata.builder()
        .name(RULE_NAME)
        .ancestors(PlatformBaseRule.class)
        .factoryClass(ConstraintValue.class)
        .build();
  }
}
/*<!-- #BLAZE_RULE (NAME = constraint_value, TYPE = OTHER, FAMILY = Platform)[GENERIC_RULE] -->

<p>This rule defines a specific value of a constraint, which can be used to define execution
platforms.

<!-- #END_BLAZE_RULE -->*/
