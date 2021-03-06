/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.go;

import com.facebook.buck.model.BuildTargets;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.ExternalTestRunnerRule;
import com.facebook.buck.rules.ExternalTestRunnerTestSpec;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.Label;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TestRule;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.test.TestCaseSummary;
import com.facebook.buck.test.TestResultSummary;
import com.facebook.buck.test.TestResults;
import com.facebook.buck.test.TestRunningOptions;
import com.facebook.buck.test.result.type.ResultType;
import com.google.common.base.Charsets;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class GoTest extends NoopBuildRule implements TestRule, HasRuntimeDeps,
    ExternalTestRunnerRule {
  private static final Pattern TEST_START_PATTERN = Pattern.compile(
      "^=== RUN\\s+(?<name>.*)$");
  private static final Pattern TEST_FINISHED_PATTERN = Pattern.compile(
      "^--- (?<status>PASS|FAIL|SKIP): (?<name>.+) \\((?<duration>\\d+\\.\\d+)(?: seconds|s)\\)$");
  // Extra time to wait for the process to exit on top of the test timeout
  private static final int PROCESS_TIMEOUT_EXTRA_MS = 5000;

  private final GoBinary testMain;

  private final ImmutableSet<Label> labels;
  private final Optional<Long> testRuleTimeoutMs;
  private final ImmutableSet<String> contacts;
  @AddToRuleKey
  private final boolean runTestsSeparately;

  public GoTest(
      BuildRuleParams buildRuleParams,
      SourcePathResolver resolver,
      GoBinary testMain,
      ImmutableSet<Label> labels,
      ImmutableSet<String> contacts,
      Optional<Long> testRuleTimeoutMs,
      boolean runTestsSeparately) {
    super(buildRuleParams, resolver);
    this.testMain = testMain;
    this.labels = labels;
    this.contacts = contacts;
    this.testRuleTimeoutMs = testRuleTimeoutMs;
    this.runTestsSeparately = runTestsSeparately;
  }

  @Override
  public boolean hasTestResultFiles(ExecutionContext executionContext) {
    return getProjectFilesystem().isFile(getPathToTestResults());
  }

  @Override
  public ImmutableList<Step> runTests(
      BuildContext buildContext,
      ExecutionContext executionContext,
      TestRunningOptions options,
      TestReportingCallback testReportingCallback) {
    Optional<Long> processTimeoutMs = testRuleTimeoutMs.isPresent() ?
        Optional.of(testRuleTimeoutMs.get() + PROCESS_TIMEOUT_EXTRA_MS) :
        Optional.<Long>absent();

    ImmutableList.Builder<String> args = ImmutableList.<String>builder();
    args.addAll(testMain.getExecutableCommand().getCommandPrefix(getResolver()));
    args.add("-test.v");
    if (testRuleTimeoutMs.isPresent()) {
      args.add("-test.timeout", testRuleTimeoutMs.get() + "ms");
    }

    return ImmutableList.of(
        new MakeCleanDirectoryStep(getProjectFilesystem(), getPathToTestOutputDirectory()),
        new GoTestStep(
            getProjectFilesystem(),
            args.build(),
            testMain.getExecutableCommand().getEnvironment(getResolver()),
            getPathToTestExitCode(),
            processTimeoutMs,
            getPathToTestResults()));
  }

  private ImmutableList<TestResultSummary> parseTestResults() throws IOException {
    ImmutableList.Builder<TestResultSummary> summariesBuilder = ImmutableList.builder();
    try (BufferedReader reader = Files.newBufferedReader(
        getProjectFilesystem().resolve(getPathToTestResults()), Charsets.UTF_8)) {
      Optional<String> currentTest = Optional.absent();
      List<String> stdout = Lists.newArrayList();
      String line;
      while ((line = reader.readLine()) != null) {
        Matcher matcher;
        if ((matcher = TEST_START_PATTERN.matcher(line)).matches()) {
          currentTest = Optional.of(matcher.group("name"));
        } else if ((matcher = TEST_FINISHED_PATTERN.matcher(line)).matches()) {
          if (!currentTest.or("").equals(matcher.group("name"))) {
            throw new RuntimeException(String.format(
                "Error parsing test output: test case end '%s' does not match start '%s'",
                matcher.group("name"), currentTest.or("")));
          }

          ResultType result = ResultType.FAILURE;
          if ("PASS".equals(matcher.group("status"))) {
            result = ResultType.SUCCESS;
          } else if ("SKIP".equals(matcher.group("status"))) {
            result = ResultType.ASSUMPTION_VIOLATION;
          }

          double timeTaken = 0.0;
          try {
            timeTaken = Float.parseFloat(matcher.group("duration"));
          } catch (NumberFormatException ex) {
            Throwables.propagate(ex);
          }

          summariesBuilder.add(new TestResultSummary(
                  "go_test",
                  matcher.group("name"),
                  result,
                  (long) (timeTaken * 1000),
                  "",
                  "",
                  Joiner.on(System.lineSeparator()).join(stdout),
                  ""
              ));

          currentTest = Optional.absent();
          stdout.clear();
        } else {
          stdout.add(line);
        }
      }

      if (currentTest.isPresent()) {
        // This can happen in case of e.g. a panic.
        summariesBuilder.add(new TestResultSummary(
                "go_test",
                currentTest.get(),
                ResultType.FAILURE,
                0,
                "incomplete",
                "",
                Joiner.on(System.lineSeparator()).join(stdout),
                ""
            ));
      }
    }
    return summariesBuilder.build();
  }

  @Override
  public Callable<TestResults> interpretTestResults(
      ExecutionContext executionContext, boolean isUsingTestSelectors, final boolean isDryRun) {
    return new Callable<TestResults>() {
      @Override
      public TestResults call() throws Exception {
        ImmutableList<TestCaseSummary> summaries = ImmutableList.of();
        if (!isDryRun) {
          summaries = ImmutableList.of(new TestCaseSummary(
              getBuildTarget().getFullyQualifiedName(),
              parseTestResults()));
        }
        return TestResults.of(
            getBuildTarget(),
            summaries,
            contacts,
            FluentIterable.from(labels).transform(Functions.toStringFunction()).toSet());
      }
    };
  }

  @Override
  public ImmutableSet<Label> getLabels() {
    return labels;
  }

  @Override
  public ImmutableSet<String> getContacts() {
    return contacts;
  }

  /**
   * @return A set of rules that this test rule will be testing.
   */
  @Override
  public ImmutableSet<BuildRule> getSourceUnderTest() {
    return ImmutableSet.<BuildRule>of();
  }

  @Override
  public Path getPathToTestOutputDirectory() {
    return BuildTargets.getGenPath(
        getBuildTarget(),
        "__test_%s_output__");
  }

  protected Path getPathToTestResults() {
    return getPathToTestOutputDirectory().resolve("results");
  }

  protected Path getPathToTestExitCode() {
    return getPathToTestOutputDirectory().resolve("exitCode");
  }

  @Override
  public boolean runTestSeparately() {
    return runTestsSeparately;
  }

  @Override
  public boolean supportsStreamingTests() {
    return false;
  }

  @Override
  public ImmutableSortedSet<BuildRule> getRuntimeDeps() {
    return ImmutableSortedSet.<BuildRule>of(testMain);
  }

  @Override
  public ExternalTestRunnerTestSpec getExternalTestRunnerSpec(
      ExecutionContext executionContext,
      TestRunningOptions testRunningOptions) {
    return ExternalTestRunnerTestSpec.builder()
        .setTarget(getBuildTarget())
        .setType("go")
        .putAllEnv(testMain.getExecutableCommand().getEnvironment(getResolver()))
        .addAllCommand(testMain.getExecutableCommand().getCommandPrefix(getResolver()))
        .addAllLabels(getLabels())
        .addAllContacts(getContacts())
        .build();
  }

}
