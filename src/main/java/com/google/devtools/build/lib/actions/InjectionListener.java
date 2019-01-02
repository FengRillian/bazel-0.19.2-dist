// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.actions;

import java.io.IOException;

/**
 * An {@link InjectionListener} is notified when a remote file metadata is inserted into its
 * associated storage.
 */
public interface InjectionListener {
  void onInsert(ActionInput dest, byte[] digest, long size, int backendIndex) throws IOException;
}
