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

package com.facebook.buck.apple;

import com.facebook.buck.cli.BuckConfig;
import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBinaryDescription;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPreprocessMode;
import com.facebook.buck.cxx.DefaultCxxPlatforms;
import com.facebook.buck.cxx.InferBuckConfig;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.io.FakeExecutableFinder;
import com.facebook.buck.model.Flavor;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class holding pre-made fake Apple rule descriptions for use in tests.
 */
public class FakeAppleRuleDescriptions {
  // Utility class, do not instantiate.
  private FakeAppleRuleDescriptions() { }

  private static final AppleSdkPaths DEFAULT_MACOSX_SDK_PATHS =
      AppleSdkPaths.builder()
          .setDeveloperPath(Paths.get("."))
          .addToolchainPaths(Paths.get("Toolchains/XcodeDefault.xctoolchain"))
          .setPlatformPath(Paths.get("Platforms/MacOSX.platform"))
          .setSdkPath(Paths.get("Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"))
          .build();

  private static final AppleSdkPaths DEFAULT_IPHONEOS_SDK_PATHS =
      AppleSdkPaths.builder()
          .setDeveloperPath(Paths.get("."))
          .addToolchainPaths(Paths.get("Toolchains/XcodeDefault.xctoolchain"))
          .setPlatformPath(Paths.get("Platforms/iPhoneOS.platform"))
          .setSdkPath(Paths.get("Platforms/iPhoneOS.platform/Developer/SDKs/iPhoneOS.sdk"))
          .build();

  private static final AppleSdk DEFAULT_MACOSX_SDK =
      AppleSdk.builder()
          .setApplePlatform(ApplePlatform.MACOSX)
          .setName("macosx")
          .setArchitectures(ImmutableList.of("x86_64"))
          .setVersion("10.10")
          .setToolchains(ImmutableList.<AppleToolchain>of())
          .build();

  private static final AppleSdk DEFAULT_IPHONEOS_SDK =
      AppleSdk.builder()
          .setApplePlatform(ApplePlatform.IPHONEOS)
          .setName("iphoneos")
          .setArchitectures(ImmutableList.of("i386", "x86_64"))
          .setVersion("8.0")
          .setToolchains(ImmutableList.<AppleToolchain>of())
          .build();

  private static final ExecutableFinder EXECUTABLE_FINDER = new FakeExecutableFinder(
      ImmutableSet.of(
          Paths.get("Toolchains/XcodeDefault.xctoolchain/usr/bin/clang"),
          Paths.get("Toolchains/XcodeDefault.xctoolchain/usr/bin/clang++"),
          Paths.get("Toolchains/XcodeDefault.xctoolchain/usr/bin/dsymutil"),
          Paths.get("Toolchains/XcodeDefault.xctoolchain/usr/bin/lipo"),
          Paths.get("Toolchains/XcodeDefault.xctoolchain/usr/bin/ranlib"),
          Paths.get("Toolchains/XcodeDefault.xctoolchain/usr/bin/strip"),
          Paths.get("Platforms/iPhoneOS.platform/Developer/usr/bin/libtool"),
          Paths.get("Platforms/iPhoneOS.platform/Developer/usr/bin/ar"),
          Paths.get("usr/bin/actool"),
          Paths.get("usr/bin/ibtool"),
          Paths.get("usr/bin/lldb"),
          Paths.get("Tools/otest"),
          Paths.get("usr/bin/xctest")));

  private static final AppleCxxPlatform DEFAULT_IPHONEOS_I386_PLATFORM =
      AppleCxxPlatforms.buildWithExecutableChecker(
          DEFAULT_IPHONEOS_SDK,
          "8.0",
          "i386",
          DEFAULT_IPHONEOS_SDK_PATHS,
          FakeBuckConfig.builder().build(),
          EXECUTABLE_FINDER);

  private static final AppleCxxPlatform DEFAULT_IPHONEOS_X86_64_PLATFORM =
      AppleCxxPlatforms.buildWithExecutableChecker(
          DEFAULT_IPHONEOS_SDK,
          "8.0",
          "x86_64",
          DEFAULT_IPHONEOS_SDK_PATHS,
          FakeBuckConfig.builder().build(),
          EXECUTABLE_FINDER);


  private static final AppleCxxPlatform DEFAULT_MACOSX_X86_64_PLATFORM =
      AppleCxxPlatforms.buildWithExecutableChecker(
          DEFAULT_MACOSX_SDK,
          "8.0",
          "x86_64",
          DEFAULT_MACOSX_SDK_PATHS,
          FakeBuckConfig.builder().build(),
          EXECUTABLE_FINDER);

  private static final BuckConfig DEFAULT_BUCK_CONFIG = FakeBuckConfig.builder().build();

  private static final CxxPlatform DEFAULT_PLATFORM = DefaultCxxPlatforms.build(
      Platform.MACOS,
      new CxxBuckConfig(DEFAULT_BUCK_CONFIG));

  private static final FlavorDomain<CxxPlatform> DEFAULT_APPLE_FLAVOR_DOMAIN =
      new FlavorDomain<>(
          "Fake iPhone C/C++ Platform",
          ImmutableMap.of(
              DEFAULT_PLATFORM.getFlavor(),
              DEFAULT_PLATFORM,
              DEFAULT_IPHONEOS_I386_PLATFORM.getCxxPlatform().getFlavor(),
              DEFAULT_IPHONEOS_I386_PLATFORM.getCxxPlatform(),
              DEFAULT_IPHONEOS_X86_64_PLATFORM.getCxxPlatform().getFlavor(),
              DEFAULT_IPHONEOS_X86_64_PLATFORM.getCxxPlatform(),
              DEFAULT_MACOSX_X86_64_PLATFORM.getCxxPlatform().getFlavor(),
              DEFAULT_MACOSX_X86_64_PLATFORM.getCxxPlatform()));

  private static final ImmutableMap<Flavor, AppleCxxPlatform>
    DEFAULT_PLATFORM_FLAVORS_TO_APPLE_CXX_PLATFORMS =
      ImmutableMap.of(
          DEFAULT_IPHONEOS_I386_PLATFORM.getCxxPlatform().getFlavor(),
          DEFAULT_IPHONEOS_I386_PLATFORM,
          DEFAULT_IPHONEOS_X86_64_PLATFORM.getCxxPlatform().getFlavor(),
          DEFAULT_IPHONEOS_X86_64_PLATFORM,
          DEFAULT_MACOSX_X86_64_PLATFORM.getCxxPlatform().getFlavor(),
          DEFAULT_MACOSX_X86_64_PLATFORM);

  /**
   * A fake apple_library description with an iOS platform for use in tests.
   */
  public static final AppleLibraryDescription LIBRARY_DESCRIPTION =
    new AppleLibraryDescription(
        new CxxLibraryDescription(
            new CxxBuckConfig(DEFAULT_BUCK_CONFIG),
            new InferBuckConfig(DEFAULT_BUCK_CONFIG),
            DEFAULT_APPLE_FLAVOR_DOMAIN,
            CxxPreprocessMode.COMBINED),
        DEFAULT_APPLE_FLAVOR_DOMAIN,
        DEFAULT_PLATFORM_FLAVORS_TO_APPLE_CXX_PLATFORMS,
        DEFAULT_PLATFORM,
        CodeSignIdentityStore.fromIdentities(ImmutableList.of(CodeSignIdentity.AD_HOC)),
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.<ProvisioningProfileMetadata>of()));

  /**
   * A fake apple_binary description with an iOS platform for use in tests.
   */
  public static final AppleBinaryDescription BINARY_DESCRIPTION =
    new AppleBinaryDescription(
        new CxxBinaryDescription(
            new InferBuckConfig(DEFAULT_BUCK_CONFIG),
            DEFAULT_IPHONEOS_I386_PLATFORM.getCxxPlatform(),
            DEFAULT_APPLE_FLAVOR_DOMAIN,
            CxxPreprocessMode.COMBINED),
        DEFAULT_APPLE_FLAVOR_DOMAIN,
        DEFAULT_PLATFORM_FLAVORS_TO_APPLE_CXX_PLATFORMS,
        DEFAULT_PLATFORM,
        CodeSignIdentityStore.fromIdentities(ImmutableList.of(CodeSignIdentity.AD_HOC)),
        ProvisioningProfileStore.fromProvisioningProfiles(
            ImmutableList.<ProvisioningProfileMetadata>of()));

  /**
   * A fake apple_bundle description with an iOS platform for use in tests.
   */
  public static final AppleBundleDescription BUNDLE_DESCRIPTION =
      new AppleBundleDescription(
          BINARY_DESCRIPTION,
          LIBRARY_DESCRIPTION,
          DEFAULT_APPLE_FLAVOR_DOMAIN,
          DEFAULT_PLATFORM_FLAVORS_TO_APPLE_CXX_PLATFORMS,
          DEFAULT_PLATFORM,
          CodeSignIdentityStore.fromIdentities(ImmutableList.of(CodeSignIdentity.AD_HOC)),
          ProvisioningProfileStore.fromProvisioningProfiles(
              ImmutableList.<ProvisioningProfileMetadata>of()),
          AppleDebugFormat.DWARF_AND_DSYM);

  /**
   * A fake apple_test description with an iOS platform for use in tests.
   */
  public static final AppleTestDescription TEST_DESCRIPTION =
      new AppleTestDescription(
          new FakeAppleConfig(),
          BUNDLE_DESCRIPTION,
          LIBRARY_DESCRIPTION,
          DEFAULT_APPLE_FLAVOR_DOMAIN,
          DEFAULT_PLATFORM_FLAVORS_TO_APPLE_CXX_PLATFORMS,
          DEFAULT_PLATFORM,
          CodeSignIdentityStore.fromIdentities(ImmutableList.of(CodeSignIdentity.AD_HOC)),
          ProvisioningProfileStore.fromProvisioningProfiles(
              ImmutableList.<ProvisioningProfileMetadata>of()),
          Suppliers.ofInstance(Optional.<Path>absent()));
}
