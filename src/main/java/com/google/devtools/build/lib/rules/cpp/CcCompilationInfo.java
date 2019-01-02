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

package com.google.devtools.build.lib.rules.cpp;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.AnalysisUtils;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.TransitiveInfoProvider;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.packages.NativeInfo;
import com.google.devtools.build.lib.packages.NativeProvider;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import com.google.devtools.build.lib.skylarkbuildapi.cpp.CcCompilationInfoApi;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.FunctionSignature;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.SkylarkNestedSet;
import com.google.devtools.build.lib.syntax.SkylarkType;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.Collection;
import javax.annotation.Nullable;

/** Wrapper for every C++ compilation provider. */
@Immutable
@AutoCodec
public final class CcCompilationInfo extends NativeInfo implements CcCompilationInfoApi {
  private static final FunctionSignature.WithValues<Object, SkylarkType> SIGNATURE =
      FunctionSignature.WithValues.create(
          FunctionSignature.of(
              /* numMandatoryPositionals= */ 0,
              /* numOptionalPositionals= */ 0,
              /* numMandatoryNamedOnly= */ 0,
              /* starArg= */ false,
              /* kwArg= */ false,
              "headers",
              "system_includes",
              "defines"),
          /* defaultValues= */ ImmutableList.of(Runtime.NONE, Runtime.NONE, Runtime.NONE),
          /* types= */ ImmutableList.of(
              SkylarkType.of(SkylarkNestedSet.class),
              SkylarkType.of(SkylarkNestedSet.class),
              SkylarkType.of(SkylarkNestedSet.class)));

  @Nullable
  private static Object nullIfNone(Object object) {
    return nullIfNone(object, Object.class);
  }

  @Nullable
  private static <T> T nullIfNone(Object object, Class<T> type) {
    return object != Runtime.NONE ? type.cast(object) : null;
  }

  public static final NativeProvider<CcCompilationInfo> PROVIDER =
      new NativeProvider<CcCompilationInfo>(
          CcCompilationInfo.class, "CcCompilationInfo", SIGNATURE) {
        @Override
        @SuppressWarnings("unchecked")
        protected CcCompilationInfo createInstanceFromSkylark(
            Object[] args, Environment env, Location loc) throws EvalException {
          CcCommon.checkLocationWhitelisted(
              env.getSemantics(),
              loc,
              env.getGlobals().getTransitiveLabel().getPackageIdentifier().toString());
          CcCompilationInfo.Builder ccCompilationInfoBuilder = CcCompilationInfo.Builder.create();
          CcCompilationContext.Builder ccCompilationContext =
              new CcCompilationContext.Builder(/* ruleContext= */ null);
          int i = 0;
          SkylarkNestedSet headers = (SkylarkNestedSet) nullIfNone(args[i++]);
          if (headers != null) {
            ccCompilationContext.addDeclaredIncludeSrcs(headers.getSet(Artifact.class));
          }
          SkylarkNestedSet systemIncludes = (SkylarkNestedSet) nullIfNone(args[i++]);
          if (systemIncludes != null) {
            ccCompilationContext.addSystemIncludeDirs(
                systemIncludes
                    .getSet(String.class)
                    .toList()
                    .stream()
                    .map(x -> PathFragment.create(x))
                    .collect(ImmutableList.toImmutableList()));
          }
          SkylarkNestedSet defines = (SkylarkNestedSet) nullIfNone(args[i++]);
          if (defines != null) {
            ccCompilationContext.addDefines(defines.getSet(String.class));
          }
          ccCompilationInfoBuilder.setCcCompilationContext(ccCompilationContext.build());
          return ccCompilationInfoBuilder.build();
        }
      };

  private final CcCompilationContext ccCompilationContext;

  @AutoCodec.Instantiator
  @VisibleForSerialization
  CcCompilationInfo(CcCompilationContext ccCompilationContext) {
    super(PROVIDER);
    this.ccCompilationContext = ccCompilationContext;
  }

  public static CcCompilationInfo merge(Collection<CcCompilationInfo> ccCompilationInfos) {
    CcCompilationContext.Builder builder =
        new CcCompilationContext.Builder(/* ruleContext= */ null);
    builder.mergeDependentCcCompilationContexts(
        ccCompilationInfos
            .stream()
            .map(CcCompilationInfo::getCcCompilationContext)
            .collect(ImmutableList.toImmutableList()));
    return (new CcCompilationInfo.Builder()).setCcCompilationContext(builder.build()).build();
  }

  @Override
  public SkylarkNestedSet getSkylarkDefines() {
    return SkylarkNestedSet.of(
        String.class, NestedSetBuilder.wrap(Order.STABLE_ORDER, ccCompilationContext.getDefines()));
  }

  @Override
  public SkylarkNestedSet getSkylarkHeaders() {
    return SkylarkNestedSet.of(Artifact.class, ccCompilationContext.getDeclaredIncludeSrcs());
  }

  @Override
  public SkylarkNestedSet getSkylarkDeclaredIncludeDirs() {
    return SkylarkNestedSet.of(
        String.class,
        NestedSetBuilder.wrap(
            Order.STABLE_ORDER,
            ccCompilationContext
                .getSystemIncludeDirs()
                .stream()
                .map(PathFragment::getPathString)
                .collect(ImmutableList.toImmutableList())));
  }

  public CcCompilationContext getCcCompilationContext() {
    return ccCompilationContext;
  }

  /** A Builder for {@link CcCompilationInfo}. */
  public static class Builder {
    CcCompilationContext ccCompilationContext;

    public static CcCompilationInfo.Builder create() {
      return new CcCompilationInfo.Builder();
    }

    public <P extends TransitiveInfoProvider> Builder setCcCompilationContext(
        CcCompilationContext ccCompilationContext) {
      Preconditions.checkState(this.ccCompilationContext == null);
      this.ccCompilationContext = ccCompilationContext;
      return this;
    }

    public CcCompilationInfo build() {
      return new CcCompilationInfo(ccCompilationContext);
    }
  }

  public static ImmutableList<CcCompilationContext> getCcCompilationContexts(
      Iterable<? extends TransitiveInfoCollection> deps) {
    ImmutableList.Builder<CcCompilationContext> ccCompilationContextsBuilder =
        ImmutableList.builder();
    for (CcCompilationInfo ccCompilationInfo :
        AnalysisUtils.getProviders(deps, CcCompilationInfo.PROVIDER)) {
      CcCompilationContext ccCompilationContext = ccCompilationInfo.getCcCompilationContext();
      if (ccCompilationContext != null) {
        ccCompilationContextsBuilder.add(ccCompilationContext);
      }
    }
    return ccCompilationContextsBuilder.build();
  }

  @Override
  public boolean equals(Object otherObject) {
    if (!(otherObject instanceof CcCompilationInfo)) {
      return false;
    }
    CcCompilationInfo other = (CcCompilationInfo) otherObject;
    if (this == other) {
      return true;
    }
    if (!this.ccCompilationContext.equals(other.ccCompilationContext)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(ccCompilationContext);
  }
}
