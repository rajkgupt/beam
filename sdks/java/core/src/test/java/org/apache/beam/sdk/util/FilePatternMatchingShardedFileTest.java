/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.beam.sdk.io.LocalResources;
import org.apache.beam.sdk.io.fs.ResolveOptions.StandardResolveOptions;
import org.apache.beam.vendor.guava.v32_1_2_jre.com.google.common.io.Files;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FilePatternMatchingShardedFile}. */
@RunWith(JUnit4.class)
public class FilePatternMatchingShardedFileTest {
  @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();
  @Rule public ExpectedException thrown = ExpectedException.none();
  private final Sleeper fastClock =
      millis -> {
        // No sleep.
      };
  private final BackOff backOff = FilePatternMatchingShardedFile.BACK_OFF_FACTORY.backoff();
  private String filePattern;

  @Before
  public void setup() throws IOException {
    // TODO: Java core test failing on windows, https://github.com/apache/beam/issues/20472
    assumeFalse(SystemUtils.IS_OS_WINDOWS);
    filePattern =
        LocalResources.fromFile(tmpFolder.getRoot(), true)
            .resolve("*", StandardResolveOptions.RESOLVE_FILE)
            .toString();
  }

  @Test
  public void testPreconditionFilePathIsNull() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(containsString("Expected valid file path, but received"));
    new FilePatternMatchingShardedFile(null);
  }

  @Test
  public void testPreconditionFilePathIsEmpty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(containsString("Expected valid file path, but received"));
    new FilePatternMatchingShardedFile("");
  }

  @Test
  public void testReadMultipleShards() throws Exception {
    String contents1 = "To be or not to be, ",
        contents2 = "it is not a question.",
        contents3 = "should not be included";

    File tmpFile1 = tmpFolder.newFile("result-000-of-002");
    File tmpFile2 = tmpFolder.newFile("result-001-of-002");
    File tmpFile3 = tmpFolder.newFile("tmp");
    Files.asCharSink(tmpFile1, StandardCharsets.UTF_8).write(contents1);
    Files.asCharSink(tmpFile2, StandardCharsets.UTF_8).write(contents2);
    Files.asCharSink(tmpFile3, StandardCharsets.UTF_8).write(contents3);

    filePattern =
        LocalResources.fromFile(tmpFolder.getRoot(), true)
            .resolve("result-*", StandardResolveOptions.RESOLVE_FILE)
            .toString();
    FilePatternMatchingShardedFile shardedFile = new FilePatternMatchingShardedFile(filePattern);

    assertThat(shardedFile.readFilesWithRetries(), containsInAnyOrder(contents1, contents2));
  }

  @Test
  public void testReadMultipleShardsWithoutShardNumber() throws Exception {
    String contents1 = "To be or not to be, ";
    String contents2 = "it is not a question.";

    File tmpFile1 = tmpFolder.newFile("result");
    File tmpFile2 = tmpFolder.newFile("tmp");
    Files.asCharSink(tmpFile1, StandardCharsets.UTF_8).write(contents1);
    Files.asCharSink(tmpFile2, StandardCharsets.UTF_8).write(contents2);

    FilePatternMatchingShardedFile shardedFile = new FilePatternMatchingShardedFile(filePattern);

    assertThat(shardedFile.readFilesWithRetries(), containsInAnyOrder(contents1, contents2));
  }

  @Test
  public void testReadEmpty() throws Exception {
    File emptyFile = tmpFolder.newFile("result-000-of-001");
    Files.asCharSink(emptyFile, StandardCharsets.UTF_8).write("");
    FilePatternMatchingShardedFile shardedFile = new FilePatternMatchingShardedFile(filePattern);

    assertThat(shardedFile.readFilesWithRetries(), empty());
  }

  @Test
  public void testReadWithRetriesFailsSinceFilesystemError() throws Exception {
    File tmpFile = tmpFolder.newFile();
    Files.asCharSink(tmpFile, StandardCharsets.UTF_8).write("Test for file checksum verifier.");
    FilePatternMatchingShardedFile shardedFile =
        spy(new FilePatternMatchingShardedFile(filePattern));
    doThrow(IOException.class).when(shardedFile).readLines(anyCollection());

    thrown.expect(IOException.class);
    thrown.expectMessage(containsString("Unable to read file(s) after retrying"));
    shardedFile.readFilesWithRetries(fastClock, backOff);
  }

  @Test
  public void testReadWithRetriesFailsWhenOutputDirEmpty() throws Exception {
    FilePatternMatchingShardedFile shardedFile = new FilePatternMatchingShardedFile(filePattern);

    thrown.expect(IOException.class);
    thrown.expectMessage(containsString("Unable to read file(s) after retrying"));
    shardedFile.readFilesWithRetries(fastClock, backOff);
  }
}
