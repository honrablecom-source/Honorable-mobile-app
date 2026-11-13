#!/usr/bin/env node
"use strict";
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFileSync } = require("node:child_process");
const CPU = { 0x0100000c: "arm64", 0x01000007: "x86_64" };
function fail(message) {
  throw Error(message);
}
function requireThat(value, message) {
  if (!value) fail(message);
}
/** Verify modern 64-bit app slices, including their actual LC_BUILD_VERSION platform. */
function parseMachO(bytes) {
  requireThat(
    Buffer.isBuffer(bytes) && bytes.length >= 32,
    "Executable is not a complete Mach-O"
  );
  const thin = (offset, size) => {
    requireThat(
      Number.isSafeInteger(offset) &&
        Number.isSafeInteger(size) &&
        offset >= 0 &&
        size >= 32 &&
        offset + size <= bytes.length,
      "Mach-O slice exceeds executable"
    );
    const b = bytes.subarray(offset, offset + size);
    requireThat(
      b.readUInt32LE(0) === 0xfeedfacf,
      "Expected a 64-bit little-endian Mach-O"
    );
    const architecture = CPU[b.readUInt32LE(4)];
    requireThat(architecture, "Unsupported executable architecture");
    requireThat(
      b.readUInt32LE(12) === 2,
      "Mach-O must be an application executable"
    );
    const count = b.readUInt32LE(16),
      length = b.readUInt32LE(20);
    requireThat(
      count > 0 && count <= 4096 && length <= size - 32,
      "Invalid Mach-O command table"
    );
    let cursor = 32,
      platform = null,
      signature = false;
    for (let i = 0; i < count; i++) {
      requireThat(cursor + 8 <= 32 + length, "Truncated Mach-O load command");
      const command = b.readUInt32LE(cursor),
        n = b.readUInt32LE(cursor + 4);
      requireThat(
        n >= 8 && n % 4 === 0 && cursor + n <= 32 + length,
        "Invalid Mach-O load command length"
      );
      if (command === 0x32) {
        requireThat(
          n >= 24 && platform === null,
          "Invalid or duplicate build-version command"
        );
        platform = b.readUInt32LE(cursor + 8);
      }
      if (command === 0x1d) signature = true;
      cursor += n;
    }
    requireThat(
      cursor === 32 + length && [2, 7].includes(platform),
      "Executable has no iOS device/simulator build platform"
    );
    return { architecture, platform, codeSignatureCommand: signature };
  };
  const magic = bytes.readUInt32BE(0);
  if (magic !== 0xcafebabe && magic !== 0xcafebabf)
    return [thin(0, bytes.length)];
  const count = bytes.readUInt32BE(4),
    wide = magic === 0xcafebabf,
    stride = wide ? 32 : 20;
  requireThat(
    count > 0 && count <= 8 && 8 + count * stride <= bytes.length,
    "Invalid universal Mach-O table"
  );
  const ranges = [],
    slices = [];
  for (let i = 0; i < count; i++) {
    const entry = 8 + i * stride,
      offset = wide
        ? Number(bytes.readBigUInt64BE(entry + 8))
        : bytes.readUInt32BE(entry + 8),
      size = wide
        ? Number(bytes.readBigUInt64BE(entry + 16))
        : bytes.readUInt32BE(entry + 12);
    requireThat(
      offset >= 8 + count * stride &&
        !ranges.some(([start, end]) => offset < end && offset + size > start),
      "Overlapping Mach-O slices"
    );
    const slice = thin(offset, size);
    requireThat(
      CPU[bytes.readUInt32BE(entry)] === slice.architecture,
      "Universal architecture differs from its slice"
    );
    ranges.push([offset, offset + size]);
    slices.push(slice);
  }
  return slices;
}
const readPlist = (file) =>
  JSON.parse(
    execFileSync("/usr/bin/plutil", ["-convert", "json", "-o", "-", file], {
      encoding: "utf8",
      maxBuffer: 1024 * 1024,
    })
  );
function verifyBundle({
  appDir,
  kind,
  archiveDir = null,
  sourceSha,
  plistReader = readPlist,
}) {
  requireThat(
    ["device", "simulator"].includes(kind),
    "Choose device or simulator artifact kind"
  );
  requireThat(
    typeof sourceSha === "string" && /^[0-9a-f]{40}$/i.test(sourceSha),
    "A full source commit SHA is required"
  );
  const root = fs.realpathSync(appDir),
    info = plistReader(path.join(root, "Info.plist"));
  const executable = info.CFBundleExecutable;
  requireThat(
    typeof executable === "string" &&
      /^[A-Za-z0-9_.-]+$/.test(executable) &&
      ![".", ".."].includes(executable),
    "Invalid bundle executable name"
  );
  requireThat(
    info.CFBundleIdentifier === "com.honorablemobile",
    "Unexpected iOS bundle identifier"
  );
  const platform = kind === "device" ? "iPhoneOS" : "iPhoneSimulator",
    sdk = kind === "device" ? "iphoneos" : "iphonesimulator";
  requireThat(
    Array.isArray(info.CFBundleSupportedPlatforms) &&
      info.CFBundleSupportedPlatforms.length === 1 &&
      info.CFBundleSupportedPlatforms[0] === platform &&
      info.DTPlatformName === sdk,
    "Bundle platform differs from requested artifact"
  );
  const binary = path.join(root, executable),
    bundle = path.join(root, "main.jsbundle");
  for (const file of [binary, bundle])
    requireThat(
      fs.lstatSync(file).isFile() && fs.statSync(file).size > 1024,
      "Missing, linked or empty executable/JS bundle"
    );
  requireThat(
    !fs.existsSync(path.join(root, "embedded.mobileprovision")),
    "Unexpected provisioning profile in unsigned artifact"
  );
  const binaryBytes = fs.readFileSync(binary),
    slices = parseMachO(binaryBytes);
  requireThat(
    slices.every((slice) => slice.platform === (kind === "device" ? 2 : 7)),
    "Mach-O build platform differs from bundle metadata"
  );
  if (kind === "device")
    requireThat(
      slices.every((slice) => slice.architecture === "arm64"),
      "Device archive must contain only arm64 code"
    );
  if (archiveDir) {
    requireThat(
      kind === "device",
      "Only a device app can be verified as a device archive"
    );
    const archive = fs.realpathSync(archiveDir),
      archiveInfo = plistReader(path.join(archive, "Info.plist")),
      relative = archiveInfo.ApplicationProperties?.ApplicationPath;
    requireThat(
      typeof relative === "string" &&
        /^Applications\/[A-Za-z0-9_.-]+\.app$/.test(relative),
      "Archive application path is invalid"
    );
    requireThat(
      fs.realpathSync(path.join(archive, "Products", relative)) === root,
      "Archive does not contain the verified app"
    );
  }
  const sha = (bytes) =>
    crypto.createHash("sha256").update(bytes).digest("hex");
  return {
    sourceSha,
    kind,
    bundleIdentifier: info.CFBundleIdentifier,
    version: info.CFBundleShortVersionString || null,
    build: info.CFBundleVersion || null,
    minimumOS: info.MinimumOSVersion || null,
    platform: sdk,
    slices,
    executableBytes: binaryBytes.length,
    executableSha256: sha(binaryBytes),
    jsBundleBytes: fs.statSync(bundle).size,
    jsBundleSha256: sha(fs.readFileSync(bundle)),
    signing:
      "Workflow disables signing; distribution identity and installation are not verified",
    validationScope:
      "React Native shell compilation and artifact structure only",
    nativeFeatureParityVerified: false,
    deviceTested: false,
    storeReady: false,
  };
}
if (require.main === module) {
  try {
    const args = process.argv.slice(2),
      options = {};
    requireThat(args.length % 2 === 0, "Use named argument/value pairs");
    for (let i = 0; i < args.length; i += 2) {
      requireThat(
        ["--app", "--kind", "--archive", "--source-sha", "--out"].includes(
          args[i]
        ) && options[args[i]] === undefined,
        "Unknown or duplicate argument"
      );
      options[args[i]] = args[i + 1];
    }
    requireThat(options["--out"], "An output receipt path is required");
    const result = verifyBundle({
      appDir: options["--app"],
      kind: options["--kind"],
      archiveDir: options["--archive"],
      sourceSha: options["--source-sha"],
    });
    fs.writeFileSync(options["--out"], JSON.stringify(result, null, 2) + "\n");
    console.log(
      `Verified ${result.kind} artifact structure; native parity/device/store checks remain unverified.`
    );
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
module.exports = { parseMachO, verifyBundle };
