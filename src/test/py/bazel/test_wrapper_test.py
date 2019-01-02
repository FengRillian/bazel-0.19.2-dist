# Copyright 2018 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import unittest

from src.test.py.bazel import test_base


class TestWrapperTest(test_base.TestBase):

  @staticmethod
  def _ReadFile(path):
    # Read the runfiles manifest.
    contents = []
    with open(path, 'rt') as f:
      contents = [line.strip() for line in f.readlines()]
    return contents

  def _FailWithOutput(self, output):
    self.fail('FAIL:\n | %s\n---' % '\n | '.join(output))

  def _CreateMockWorkspace(self):
    self.ScratchFile('WORKSPACE')
    self.ScratchFile('foo/BUILD', [
        'sh_test(',
        '    name = "passing_test.bat",',
        '    srcs = ["passing.bat"],',
        ')',
        'sh_test(',
        '    name = "failing_test.bat",',
        '    srcs = ["failing.bat"],',
        ')',
        'sh_test(',
        '    name = "printing_test.bat",',
        '    srcs = ["printing.bat"],',
        ')',
        'sh_test(',
        '    name = "runfiles_test.bat",',
        '    srcs = ["runfiles.bat"],',
        '    data = ["passing.bat"],',
        ')',
        'sh_test(',
        '    name = "sharded_test.bat",',
        '    srcs = ["sharded.bat"],',
        '    shard_count = 2,',
        ')',
        'sh_test(',
        '    name = "unexported_test.bat",',
        '    srcs = ["unexported.bat"],',
        '    shard_count = 2,',
        ')',
    ])
    self.ScratchFile('foo/passing.bat', ['@exit /B 0'], executable=True)
    self.ScratchFile('foo/failing.bat', ['@exit /B 1'], executable=True)
    self.ScratchFile(
        'foo/printing.bat', [
            '@echo lorem ipsum',
            '@echo HOME=%HOME%',
            '@echo TEST_SRCDIR=%TEST_SRCDIR%',
            '@echo TEST_TMPDIR=%TEST_TMPDIR%',
            '@echo USER=%USER%',
        ],
        executable=True)
    self.ScratchFile(
        'foo/runfiles.bat', [
            '@echo MF=%RUNFILES_MANIFEST_FILE%',
            '@echo ONLY=%RUNFILES_MANIFEST_ONLY%',
            '@echo DIR=%RUNFILES_DIR%',
        ],
        executable=True)
    self.ScratchFile(
        'foo/sharded.bat', [
            '@echo STATUS=%TEST_SHARD_STATUS_FILE%',
            '@echo INDEX=%TEST_SHARD_INDEX% TOTAL=%TEST_TOTAL_SHARDS%',
        ],
        executable=True)
    self.ScratchFile(
        'foo/unexported.bat', [
            '@echo GOOD=%HOME%',
            '@echo BAD=%TEST_UNDECLARED_OUTPUTS_MANIFEST%',
        ],
        executable=True)

  def _AssertPassingTest(self, flag):
    exit_code, _, stderr = self.RunBazel([
        'test',
        '//foo:passing_test.bat',
        '-t-',
        flag,
    ])
    self.AssertExitCode(exit_code, 0, stderr)

  def _AssertFailingTest(self, flag):
    exit_code, _, stderr = self.RunBazel([
        'test',
        '//foo:failing_test.bat',
        '-t-',
        flag,
    ])
    self.AssertExitCode(exit_code, 3, stderr)

  def _AssertPrintingTest(self, flag):
    exit_code, stdout, stderr = self.RunBazel([
        'test',
        '//foo:printing_test.bat',
        '-t-',
        '--test_output=all',
        flag,
    ])
    self.AssertExitCode(exit_code, 0, stderr)
    lorem = False
    for line in stderr + stdout:
      if line.startswith('lorem ipsum'):
        lorem = True
      elif line.startswith('HOME='):
        home = line[len('HOME='):]
      elif line.startswith('TEST_SRCDIR='):
        srcdir = line[len('TEST_SRCDIR='):]
      elif line.startswith('TEST_TMPDIR='):
        tmpdir = line[len('TEST_TMPDIR='):]
      elif line.startswith('USER='):
        user = line[len('USER='):]
    if not lorem:
      self._FailWithOutput(stderr + stdout)
    if not home:
      self._FailWithOutput(stderr + stdout)
    if not os.path.isabs(home):
      self._FailWithOutput(stderr + stdout)
    if not os.path.isdir(srcdir):
      self._FailWithOutput(stderr + stdout)
    if not os.path.isfile(os.path.join(srcdir, 'MANIFEST')):
      self._FailWithOutput(stderr + stdout)
    if not os.path.isabs(srcdir):
      self._FailWithOutput(stderr + stdout)
    if not os.path.isdir(tmpdir):
      self._FailWithOutput(stderr + stdout)
    if not os.path.isabs(tmpdir):
      self._FailWithOutput(stderr + stdout)
    if not user:
      self._FailWithOutput(stderr + stdout)

  def _AssertRunfiles(self, flag):
    exit_code, stdout, stderr = self.RunBazel([
        'test',
        '//foo:runfiles_test.bat',
        '-t-',
        '--test_output=all',
        # Ensure Bazel does not create a runfiles tree.
        '--experimental_enable_runfiles=no',
        flag,
    ])
    self.AssertExitCode(exit_code, 0, stderr)
    mf = mf_only = rf_dir = None
    for line in stderr + stdout:
      if line.startswith('MF='):
        mf = line[len('MF='):]
      elif line.startswith('ONLY='):
        mf_only = line[len('ONLY='):]
      elif line.startswith('DIR='):
        rf_dir = line[len('DIR='):]

    if mf_only != '1':
      self._FailWithOutput(stderr + stdout)

    if not os.path.isfile(mf):
      self._FailWithOutput(stderr + stdout)
    mf_contents = TestWrapperTest._ReadFile(mf)
    # Assert that the data dependency is listed in the runfiles manifest.
    if not any(
        line.split(' ', 1)[0].endswith('foo/passing.bat')
        for line in mf_contents):
      self._FailWithOutput(mf_contents)

    if not os.path.isdir(rf_dir):
      self._FailWithOutput(stderr + stdout)

  def _AssertShardedTest(self, flag):
    exit_code, stdout, stderr = self.RunBazel([
        'test',
        '//foo:sharded_test.bat',
        '-t-',
        '--test_output=all',
        flag,
    ])
    self.AssertExitCode(exit_code, 0, stderr)
    status = None
    index_lines = []
    for line in stderr + stdout:
      if line.startswith('STATUS='):
        status = line[len('STATUS='):]
      elif line.startswith('INDEX='):
        index_lines.append(line)
    if not status:
      self._FailWithOutput(stderr + stdout)
    # Test test-setup.sh / test wrapper only ensure that the directory of the
    # shard status file exist, not that the file itself does too.
    if not os.path.isdir(os.path.dirname(status)):
      self._FailWithOutput(stderr + stdout)
    if sorted(index_lines) != ['INDEX=0 TOTAL=2', 'INDEX=1 TOTAL=2']:
      self._FailWithOutput(stderr + stdout)

  def _AssertUnexportsEnvvars(self, flag):
    exit_code, stdout, stderr = self.RunBazel([
        'test',
        '//foo:unexported_test.bat',
        '-t-',
        '--test_output=all',
        flag,
    ])
    self.AssertExitCode(exit_code, 0, stderr)
    good = bad = None
    for line in stderr + stdout:
      if line.startswith('GOOD='):
        good = line[len('GOOD='):]
      elif line.startswith('BAD='):
        bad = line[len('BAD='):]
    if not good or bad:
      self._FailWithOutput(stderr + stdout)

  def testTestExecutionWithTestSetupSh(self):
    self._CreateMockWorkspace()
    flag = '--nowindows_native_test_wrapper'
    self._AssertPassingTest(flag)
    self._AssertFailingTest(flag)
    self._AssertPrintingTest(flag)
    self._AssertRunfiles(flag)
    self._AssertShardedTest(flag)
    self._AssertUnexportsEnvvars(flag)

  def testTestExecutionWithTestWrapperExe(self):
    self._CreateMockWorkspace()
    # As of 2018-09-11, the Windows native test runner can run simple tests and
    # export a few envvars, though it does not completely set up the test's
    # environment yet.
    flag = '--windows_native_test_wrapper'
    self._AssertPassingTest(flag)
    self._AssertFailingTest(flag)
    self._AssertPrintingTest(flag)
    self._AssertRunfiles(flag)
    self._AssertShardedTest(flag)
    self._AssertUnexportsEnvvars(flag)


if __name__ == '__main__':
  unittest.main()
