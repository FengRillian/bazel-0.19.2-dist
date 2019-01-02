// Copyright 2014 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.rules.proto;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skylarkbuildapi.ProtoSourcesProviderApi;

// TODO(carmi): Rename the class to ProtoInfoProvider.
/**
 * Configured target classes that implement this class can contribute .proto files to the
 * compilation of proto_library rules.
 */
@AutoValue
@Immutable
@AutoCodec
public abstract class ProtoSourcesProvider
    implements TransitiveInfoProvider, ProtoSourcesProviderApi<Artifact> {
  /** The name of the field in Skylark used to access this class. */
  public static final String SKYLARK_NAME = "proto";

  @AutoCodec.Instantiator
  public static ProtoSourcesProvider create(
      NestedSet<Artifact> transitiveImports,
      NestedSet<Artifact> transitiveProtoSources,
      ImmutableList<Artifact> directProtoSources,
      NestedSet<Artifact> checkDepsProtoSources,
      Artifact directDescriptorSet,
      NestedSet<Artifact> transitiveDescriptorSets,
      NestedSet<String> transitiveProtoPathFlags,
      String protoSourceRoot) {
    return new AutoValue_ProtoSourcesProvider(
        transitiveImports,
        transitiveProtoSources,
        directProtoSources,
        checkDepsProtoSources,
        directDescriptorSet,
        transitiveDescriptorSets,
        transitiveProtoPathFlags,
        protoSourceRoot);
  }

  /**
   * Transitive imports including weak dependencies This determines the order of "-I" arguments to
   * the protocol compiler, and that is probably important
   */
  @Override
  public abstract NestedSet<Artifact> getTransitiveImports();

  /** Returns the proto sources for this rule and all its dependent protocol buffer rules. */
  @Override
  // TODO(bazel-team): The difference between transitive imports and transitive proto sources
  // should never be used by Skylark or by an Aspect. One of these two should be removed,
  // preferably soon, before Skylark users start depending on them.
  public abstract NestedSet<Artifact> getTransitiveProtoSources();

  /** Returns the proto sources from the 'srcs' attribute. */
  @Override
  public abstract ImmutableList<Artifact> getDirectProtoSources();

  /**
   * Returns the proto sources from the 'srcs' attribute. If the library is a proxy library that has
   * no sources, return the sources from the direct deps.
   *
   * <p>This must be a set to avoid collecting the same source twice when depending on 2 proxy
   * proto_library's that depend on the same proto_library.
   */
  @Override
  public abstract NestedSet<Artifact> getCheckDepsProtoSources();

  /**
   * Be careful while using this artifact - it is the parsing of the transitive set of .proto files.
   * It's possible to cause a O(n^2) behavior, where n is the length of a proto chain-graph.
   * (remember that proto-compiler reads all transitive .proto files, even when producing the
   * direct-srcs descriptor set)
   */
  @Override
  public abstract Artifact directDescriptorSet();

  /**
   * Be careful while using this artifact - it is the parsing of the transitive set of .proto files.
   * It's possible to cause a O(n^2) behavior, where n is the length of a proto chain-graph.
   * (remember that proto-compiler reads all transitive .proto files, even when producing the
   * direct-srcs descriptor set)
   */
  @Override
  public abstract NestedSet<Artifact> transitiveDescriptorSets();

  /**
   * Directories of .proto sources collected from the transitive closure. These flags will be passed
   * to {@code protoc} in the specified order, via the {@code --proto_path} flag.
   */
  @Override
  public abstract NestedSet<String> getTransitiveProtoPathFlags();

  /** The {@code proto_source_root} of the current library. */
  public abstract String getProtoSourceRoot();

  ProtoSourcesProvider() {}
}
