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

package com.google.devtools.build.lib.runtime;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.SpawnMetrics;
import java.time.Duration;

/**
 * Aggregates all the critical path components in one object. This allows us to easily access the
 * components data and have a proper toString().
 */
public class AggregatedCriticalPath {
  public static final AggregatedCriticalPath EMPTY =
      new AggregatedCriticalPath(Duration.ZERO, SpawnMetrics.EMPTY, ImmutableList.of());

  private final Duration totalTime;
  private final SpawnMetrics aggregatedSpawnMetrics;
  private final ImmutableList<CriticalPathComponent> criticalPathComponents;

  public AggregatedCriticalPath(
      Duration totalTime,
      SpawnMetrics aggregatedSpawnMetrics,
      ImmutableList<CriticalPathComponent> criticalPathComponents) {
    this.totalTime = totalTime;
    this.aggregatedSpawnMetrics = aggregatedSpawnMetrics;
    this.criticalPathComponents = criticalPathComponents;
  }

  /** Total wall time spent running the critical path actions. */
  public Duration totalTime() {
    return totalTime;
  }

  public SpawnMetrics getSpawnMetrics() {
    return aggregatedSpawnMetrics;
  }

  /** Returns a list of all the component stats for the critical path. */
  public ImmutableList<CriticalPathComponent> components() {
    return criticalPathComponents;
  }

  @Override
  public String toString() {
    return toString(false);
  }

  /**
   * Returns a summary version of the critical path stats that omits stats that are not useful
   * to the user.
   */
  public String toStringSummary() {
    return toString(true);
  }

  private String toString(boolean summary) {
    StringBuilder sb = new StringBuilder("Critical Path: ");
    sb.append(String.format("%.2f", totalTime.toMillis() / 1000.0));
    sb.append("s, Remote ");
    sb.append(getSpawnMetrics().toString(totalTime(), summary));
    if (summary || criticalPathComponents.isEmpty()) {
      return sb.toString();
    }
    sb.append("\n  ");
    Joiner.on("\n  ").appendTo(sb, criticalPathComponents);
    return sb.toString();
  }
}

