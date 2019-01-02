// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.buildeventstream.transports;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.buildeventstream.ArtifactGroupNamer;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventArtifactUploaderFactoryMap;
import com.google.devtools.build.lib.buildeventstream.BuildEventContext;
import com.google.devtools.build.lib.buildeventstream.BuildEventProtocolOptions;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildStarted;
import com.google.devtools.build.lib.buildeventstream.BuildEventTransport;
import com.google.devtools.build.lib.buildeventstream.PathConverter;
import com.google.devtools.common.options.OptionsParser;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/** Tests {@link BuildEventTransportFactory}. */
@RunWith(JUnit4.class)
public class BuildEventTransportFactoryTest {

  private static final Function<Object, Class<?>> GET_CLASS = Object::getClass;

  private static final BuildEventStreamProtos.BuildEvent BUILD_EVENT_AS_PROTO =
      BuildEventStreamProtos.BuildEvent.newBuilder()
          .setStarted(BuildStarted.newBuilder().setCommand("build"))
          .build();

  private OptionsParser optionsParser;

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Mock public BuildEvent buildEvent;

  @Mock public PathConverter pathConverter;
  @Mock public ArtifactGroupNamer artifactGroupNamer;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    when(buildEvent.asStreamProto(Matchers.<BuildEventContext>any()))
        .thenReturn(BUILD_EVENT_AS_PROTO);
    optionsParser =
        OptionsParser.newOptionsParser(
            BuildEventStreamOptions.class, BuildEventProtocolOptions.class);
  }

  @After
  public void validateMocks() {
    Mockito.validateMockitoUsage();
  }

  private BuildEventArtifactUploaderFactoryMap localFilesOnly() {
    return new BuildEventArtifactUploaderFactoryMap.Builder().build();
  }

  @Test
  public void testCreatesTextFormatFileTransport() throws Exception {
    File textFile = tmp.newFile();
    optionsParser.parse("--build_event_text_file=" + textFile.getAbsolutePath());
    ImmutableSet<BuildEventTransport> transports =
        BuildEventTransportFactory.createFromOptions(optionsParser, localFilesOnly(), (e) -> {});
    assertThat(FluentIterable.from(transports).transform(GET_CLASS))
        .containsExactly(TextFormatFileTransport.class);
    sendEventsAndClose(buildEvent, transports);
    assertThat(textFile.exists()).isTrue();
  }

  @Test
  public void testCreatesBinaryFormatFileTransport() throws Exception {
    File binaryFile = tmp.newFile();
    optionsParser.parse("--build_event_binary_file=" + binaryFile.getAbsolutePath());
    ImmutableSet<BuildEventTransport> transports =
        BuildEventTransportFactory.createFromOptions(optionsParser, localFilesOnly(), (e) -> {});
    assertThat(FluentIterable.from(transports).transform(GET_CLASS))
        .containsExactly(BinaryFormatFileTransport.class);
    sendEventsAndClose(buildEvent, transports);
    assertThat(binaryFile.exists()).isTrue();
  }

  @Test
  public void testCreatesAllTransports() throws Exception {
    File textFile = tmp.newFile();
    File binaryFile = tmp.newFile();
    optionsParser.parse(
        "--build_event_text_file=" + textFile.getAbsolutePath(),
        "--build_event_binary_file=" + binaryFile.getAbsolutePath());
    ImmutableSet<BuildEventTransport> transports =
        BuildEventTransportFactory.createFromOptions(optionsParser, localFilesOnly(), (e) -> {});
    assertThat(FluentIterable.from(transports).transform(GET_CLASS))
        .containsExactly(TextFormatFileTransport.class, BinaryFormatFileTransport.class);
    sendEventsAndClose(buildEvent, transports);
    assertThat(textFile.exists()).isTrue();
    assertThat(binaryFile.exists()).isTrue();
  }

  @Test
  public void testCreatesNoTransports() throws IOException {
    ImmutableSet<BuildEventTransport> transports =
        BuildEventTransportFactory.createFromOptions(optionsParser, localFilesOnly(), (e) -> {});
    assertThat(transports).isEmpty();
  }

  private void sendEventsAndClose(BuildEvent event, Iterable<BuildEventTransport> transports)
      throws InterruptedException, ExecutionException {
    for (BuildEventTransport transport : transports) {
      transport.sendBuildEvent(event, artifactGroupNamer);
      transport.close().get();
    }
  }
}
