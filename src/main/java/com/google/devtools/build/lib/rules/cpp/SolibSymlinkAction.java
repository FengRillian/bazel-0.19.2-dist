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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.AbstractAction;
import com.google.devtools.build.lib.actions.ActionAnalysisMetadata;
import com.google.devtools.build.lib.actions.ActionExecutionContext;
import com.google.devtools.build.lib.actions.ActionExecutionException;
import com.google.devtools.build.lib.actions.ActionKeyContext;
import com.google.devtools.build.lib.actions.ActionOwner;
import com.google.devtools.build.lib.actions.ActionResult;
import com.google.devtools.build.lib.actions.Actions;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.ArtifactRoot;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.config.BuildConfiguration;
import com.google.devtools.build.lib.concurrent.ThreadSafety.Immutable;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec;
import com.google.devtools.build.lib.skyframe.serialization.autocodec.AutoCodec.VisibleForSerialization;
import com.google.devtools.build.lib.util.Fingerprint;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.io.IOException;

/**
 * Creates mangled symlinks in the solib directory for all shared libraries. Libraries that have a
 * potential to contain SONAME field rely on the mangled symlink to the parent directory instead.
 *
 * <p>Such symlinks are used by the linker to ensure that all rpath entries can be specified
 * relative to the $ORIGIN.
 */
@AutoCodec
@Immutable
public final class SolibSymlinkAction extends AbstractAction {
  private final Artifact symlink;

  @VisibleForSerialization
  SolibSymlinkAction(
      ActionOwner owner, Artifact primaryInput, Artifact primaryOutput) {
    super(owner, ImmutableList.of(primaryInput), ImmutableList.of(primaryOutput));

    Preconditions.checkArgument(Link.SHARED_LIBRARY_FILETYPES.matches(primaryInput.getFilename()));
    this.symlink = Preconditions.checkNotNull(primaryOutput);
  }

  @Override
  public ActionResult execute(ActionExecutionContext actionExecutionContext)
      throws ActionExecutionException {
    Path mangledPath = actionExecutionContext.getInputPath(symlink);
    try {
      mangledPath.createSymbolicLink(actionExecutionContext.getInputPath(getPrimaryInput()));
    } catch (IOException e) {
      throw new ActionExecutionException(
          "failed to create _solib symbolic link '"
              + symlink.prettyPrint()
              + "' to target '"
              + getPrimaryInput()
              + "'",
          e,
          this,
          false);
    }
    return ActionResult.EMPTY;
  }

  @Override
  protected void computeKey(ActionKeyContext actionKeyContext, Fingerprint fp) {
    fp.addPath(symlink.getExecPath());
    fp.addPath(getPrimaryInput().getExecPath());
  }

  @Override
  public String getMnemonic() {
    return "SolibSymlink";
  }

  @Override
  protected String getRawProgressMessage() {
    return null;
  }

  /**
   * Replaces shared library artifact with mangled symlink and creates related symlink action. For
   * artifacts that should retain filename (e.g. libraries with SONAME tag), link is created to the
   * parent directory instead.
   *
   * <p>This action is performed to minimize number of -rpath entries used during linking process
   * (by essentially "collecting" as many shared libraries as possible in the single directory),
   * since we will be paying quadratic price for each additional entry on the -rpath.
   *
   * @param ruleContext rule context, that requested symlink.
   * @param solibDir String giving the solib directory
   * @param library Shared library artifact that needs to be mangled.
   * @param preserveName whether to preserve the name of the library
   * @param prefixConsumer whether to prefix the output artifact name with the label of the consumer
   * @return mangled symlink artifact.
   */
  public static Artifact getDynamicLibrarySymlink(
      final RuleContext ruleContext,
      String solibDir,
      final Artifact library,
      boolean preserveName,
      boolean prefixConsumer,
      BuildConfiguration configuration) {
    PathFragment mangledName =
        getMangledName(
            ruleContext,
            solibDir,
            library.getRootRelativePath(),
            preserveName,
            prefixConsumer,
            configuration.getFragment(CppConfiguration.class));
    return getDynamicLibrarySymlinkInternal(
        ruleContext, library, mangledName, configuration);
  }

  /**
   * Version of {@link #getDynamicLibrarySymlink} for the special case of C++ runtime libraries.
   * These are handled differently than other libraries: neither their names nor directories are
   * mangled, i.e. libstdc++.so.6 is symlinked from _solib_[arch]/libstdc++.so.6
   */
  public static Artifact getCppRuntimeSymlink(
      RuleContext ruleContext,
      Artifact library,
      String toolchainProvidedSolibDir,
      String solibDirOverride,
      BuildConfiguration configuration) {
    PathFragment solibDir =
        PathFragment.create(
            solibDirOverride != null ? solibDirOverride : toolchainProvidedSolibDir);
    PathFragment symlinkName = solibDir.getRelative(library.getRootRelativePath().getBaseName());
    return getDynamicLibrarySymlinkInternal(ruleContext, library, symlinkName, configuration);
  }

  /**
   * Internal implementation that takes a pre-determined symlink name; supports both the
   * generic {@link #getDynamicLibrarySymlink} and the specialized {@link #getCppRuntimeSymlink}.
   */
  private static Artifact getDynamicLibrarySymlinkInternal(RuleContext ruleContext,
      Artifact library, PathFragment symlinkName, BuildConfiguration configuration) {
    Preconditions.checkArgument(Link.SHARED_LIBRARY_FILETYPES.matches(library.getFilename()));
    Preconditions.checkArgument(!library.getRootRelativePath().getSegment(0).startsWith("_solib_"));

    // Ignore libraries that are already represented by the symlinks.
    ArtifactRoot root = configuration.getBinDirectory(ruleContext.getRule().getRepository());
    Artifact symlink = ruleContext.getShareableArtifact(symlinkName, root);
    ruleContext.registerAction(
        new SolibSymlinkAction(
            ruleContext.getActionOwner(), library, symlink));
    return symlink;
  }

  /**
   * Returns the name of the symlink that will be created for a library, given its name.
   *
   * @param ruleContext rule context that requests symlink
   * @param solibDir a String giving the solib directory
   * @param libraryPath the root-relative path of the library
   * @param preserveName true if filename should be preserved
   * @param prefixConsumer true if the result should be prefixed with the label of the consumer
   * @returns root relative path name
   */
  public static PathFragment getMangledName(
      RuleContext ruleContext,
      String solibDir,
      PathFragment libraryPath,
      boolean preserveName,
      boolean prefixConsumer,
      CppConfiguration cppConfiguration) {
    String escapedRulePath = Actions.escapedPath(
        "_" + ruleContext.getLabel());
    String soname = getDynamicLibrarySoname(libraryPath, preserveName);
    PathFragment solibDirPath = PathFragment.create(solibDir);
    if (preserveName) {
      String escapedLibraryPath =
          Actions.escapedPath("_" + libraryPath.getParentDirectory().getPathString());
      PathFragment mangledDir =
          solibDirPath.getRelative(
              prefixConsumer ? escapedRulePath + "__" + escapedLibraryPath : escapedLibraryPath);
      return mangledDir.getRelative(soname);
    } else {
      return solibDirPath.getRelative(prefixConsumer ? escapedRulePath + "__" + soname : soname);
    }
  }

  /**
   * Compute the SONAME to use for a dynamic library. This name is basically the
   * name of the shared library in its final symlinked location.
   *
   * @param libraryPath name of the shared library that needs to be mangled
   * @param preserveName true if filename should be preserved, false - mangled
   * @return soname to embed in the dynamic library
   */
  public static String getDynamicLibrarySoname(PathFragment libraryPath,
                                               boolean preserveName) {
    String mangledName;
    if (preserveName) {
      mangledName = libraryPath.getBaseName();
    } else {
      mangledName = "lib" + Actions.escapedPath(libraryPath.getPathString());
    }
    return mangledName;
  }

  @Override
  public boolean shouldReportPathPrefixConflict(ActionAnalysisMetadata action) {
    return false; // Always ignore path prefix conflict for the SolibSymlinkAction.
  }
}
