"use strict";
const test = require("node:test"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  os = require("node:os"),
  path = require("node:path");
const { parseMachO, readPlist, verifyBundle } = require("./verify-ios-artifact.cjs");
function thin(cpu = 0x0100000c, platform = 2) {
  const b = Buffer.alloc(2048);
  b.writeUInt32LE(0xfeedfacf, 0);
  b.writeUInt32LE(cpu, 4);
  b.writeUInt32LE(2, 12);
  b.writeUInt32LE(1, 16);
  b.writeUInt32LE(24, 20);
  b.writeUInt32LE(0x32, 32);
  b.writeUInt32LE(24, 36);
  b.writeUInt32LE(platform, 40);
  return b;
}
function fat(wide = false) {
  const slices = [thin(0x0100000c, 7), thin(0x01000007, 7)],
    b = Buffer.alloc(12288);
  b.writeUInt32BE(wide ? 0xcafebabf : 0xcafebabe, 0);
  b.writeUInt32BE(2, 4);
  slices.forEach((slice, i) => {
    const offset = 4096 * (i + 1),
      entry = 8 + i * (wide ? 32 : 20);
    b.writeUInt32BE(slice.readUInt32LE(4), entry);
    if (wide) {
      b.writeBigUInt64BE(BigInt(offset), entry + 8);
      b.writeBigUInt64BE(BigInt(slice.length), entry + 16);
    } else {
      b.writeUInt32BE(offset, entry + 8);
      b.writeUInt32BE(slice.length, entry + 12);
    }
    slice.copy(b, offset);
  });
  return b;
}
function fixture(t, kind = "device") {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "noble-ios-receipt-"));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const archive = path.join(root, "Noble.xcarchive");
  let app = path.join(archive, "Products", "Applications", "HonorableMobile.app");
  fs.mkdirSync(app, { recursive: true });
  app = fs.realpathSync(app);
  fs.writeFileSync(path.join(app, "Info.plist"), "fixture");
  fs.writeFileSync(path.join(archive, "Info.plist"), "fixture");
  fs.writeFileSync(
    path.join(app, "HonorableMobile"),
    thin(0x0100000c, kind === "device" ? 2 : 7)
  );
  fs.writeFileSync(path.join(app, "main.jsbundle"), "source".repeat(500));
  const info = {
    CFBundleExecutable: "HonorableMobile",
    CFBundleIdentifier: "com.honorablemobile",
    CFBundleSupportedPlatforms: [
      kind === "device" ? "iPhoneOS" : "iPhoneSimulator",
    ],
    DTPlatformName: kind === "device" ? "iphoneos" : "iphonesimulator",
    MinimumOSVersion: "15.1",
  };
  const options = {
    appDir: app,
    kind,
    sourceSha: "a".repeat(40),
    plistReader: (file) =>
      file === path.join(app, "Info.plist")
        ? info
        : {
            ApplicationProperties: {
              ApplicationPath: "Applications/HonorableMobile.app",
            },
          },
  };
  return { root, archive, app, info, options };
}
test("thin device and simulator bytes preserve architecture and actual build platform", () => {
  assert.equal(parseMachO(thin())[0].platform, 2);
  assert.equal(parseMachO(thin(0x01000007, 7))[0].architecture, "x86_64");
});
test("both universal table formats verify every simulator slice", () => {
  for (const wide of [false, true])
    assert.deepEqual(
      parseMachO(fat(wide)).map((s) => s.architecture),
      ["arm64", "x86_64"]
    );
});
test("malformed, missing or mismatched load commands and unsupported binary types fail closed", () => {
  for (const mutate of [
    (b) => b.writeUInt32LE(0, 0),
    (b) => b.writeUInt32LE(6, 12),
    (b) => b.writeUInt32LE(0, 16),
    (b) => b.writeUInt32LE(3, 36),
    (b) => b.writeUInt32LE(1, 40),
    (b) => b.writeUInt32LE(0x25, 32),
    (b) => b.writeUInt32LE(0x123, 4),
  ]) {
    const b = thin();
    mutate(b);
    assert.throws(() => parseMachO(b));
  }
  assert.throws(() => parseMachO(Buffer.alloc(4)));
});
test("universal slices cannot overlap or disagree with the table architecture", () => {
  let b = fat();
  b.writeUInt32BE(4096, 8 + 20 + 8);
  assert.throws(() => parseMachO(b));
  b = fat();
  b.writeUInt32BE(0x01000007, 8);
  assert.throws(() => parseMachO(b));
  b = fat(true);
  b.writeBigUInt64BE(2n ** 63n, 16);
  assert.throws(() => parseMachO(b));
});
test("verified device archive records source and byte hashes without claiming installation or parity", (t) => {
  const f = fixture(t);
  const receipt = verifyBundle({ ...f.options, archiveDir: f.archive });
  assert.equal(receipt.platform, "iphoneos");
  assert.equal(receipt.executableSha256.length, 64);
  assert.equal(receipt.jsBundleSha256.length, 64);
  assert.equal(receipt.nativeFeatureParityVerified, false);
  assert.equal(receipt.storeReady, false);
  assert.equal(receipt.deviceTested, false);
});
test("simulator metadata cannot disguise device machine code, or vice versa", (t) => {
  const f = fixture(t);
  fs.writeFileSync(path.join(f.app, "HonorableMobile"), thin(0x0100000c, 7));
  assert.throws(() => verifyBundle(f.options), /build platform/);
  fs.writeFileSync(path.join(f.app, "HonorableMobile"), thin(0x01000007, 2));
  assert.throws(() => verifyBundle(f.options), /only arm64/);
});
test("wrong IDs, escaped executable names and absent source identity are rejected", (t) => {
  const f = fixture(t);
  f.info.CFBundleIdentifier = "other";
  assert.throws(() => verifyBundle(f.options));
  f.info.CFBundleIdentifier = "com.honorablemobile";
  f.info.CFBundleExecutable = "../private";
  assert.throws(() => verifyBundle(f.options));
  f.info.CFBundleExecutable = "HonorableMobile";
  assert.throws(() => verifyBundle({ ...f.options, sourceSha: "main" }));
});
test("missing JS, linked executables and unexpected provisioning profiles cannot pass", (t) => {
  const f = fixture(t),
    bundle = path.join(f.app, "main.jsbundle"),
    binary = path.join(f.app, "HonorableMobile");
  fs.writeFileSync(bundle, "");
  assert.throws(() => verifyBundle(f.options));
  fs.writeFileSync(bundle, "source".repeat(500));
  fs.writeFileSync(path.join(f.app, "embedded.mobileprovision"), "unexpected");
  assert.throws(() => verifyBundle(f.options));
  fs.unlinkSync(path.join(f.app, "embedded.mobileprovision"));
  fs.unlinkSync(binary);
  fs.symlinkSync(__filename, binary);
  assert.throws(() => verifyBundle(f.options));
});
test("archive path must identify the exact verified application", (t) => {
  const f = fixture(t),
    reader = f.options.plistReader;
  assert.throws(() =>
    verifyBundle({
      ...f.options,
      archiveDir: f.archive,
      plistReader: (file) =>
        file === path.join(f.app, "Info.plist")
          ? f.info
          : { ApplicationProperties: { ApplicationPath: "../Other.app" } },
    })
  );
  assert.throws(() =>
    verifyBundle({
      ...f.options,
      kind: "simulator",
      archiveDir: f.archive,
      plistReader: reader,
    })
  );
});

test("filesystem aliases keep plist identity after canonicalizing the app path", (t) => {
  const f = fixture(t);
  const alias = path.join(f.root, "Aliased.app");
  fs.symlinkSync(f.app, alias, "dir");
  const receipt = verifyBundle({ ...f.options, appDir: alias, archiveDir: f.archive });
  assert.equal(receipt.platform, "iphoneos");
  assert.equal(receipt.slices[0].architecture, "arm64");
});

// Real plist serialization exercises the production reader, including the archive date
// that the former plutil-to-JSON adapter could not represent.
function writeRealPlists(f, binary, applicationPath = "Applications/HonorableMobile.app") {
  require("node:child_process").execFileSync("python3", ["-c", `
import datetime, json, plistlib, sys
fmt = plistlib.FMT_BINARY if sys.argv[4] == "binary" else plistlib.FMT_XML
with open(sys.argv[1], "wb") as target:
    plistlib.dump(json.loads(sys.argv[3]), target, fmt=fmt)
archive = {"CreationDate": datetime.datetime(2026, 11, 13, 22, 0, 0),
           "ApplicationProperties": {"ApplicationPath": sys.argv[5]},
           "FixtureData": b"generated", "Name": "Noble"}
with open(sys.argv[2], "wb") as target:
    plistlib.dump(archive, target, fmt=fmt)
`, path.join(f.app, "Info.plist"), path.join(f.archive, "Info.plist"),
    JSON.stringify(f.info), binary ? "binary" : "xml", applicationPath], {stdio: "pipe"});
}
for (const binary of [false, true])
  test(`production reader verifies real ${binary ? "binary" : "XML"} archive metadata with CreationDate`, (t) => {
    const f = fixture(t); writeRealPlists(f, binary);
    const info = readPlist(path.join(f.archive, "Info.plist"));
    assert.deepEqual(info.CreationDate, {$plistDate: "2026-11-13T22:00:00"});
    const {plistReader, ...options} = f.options;
    const receipt = verifyBundle({...options, archiveDir: f.archive});
    assert.equal(receipt.kind, "device"); assert.equal(receipt.deviceTested, false);
    assert.equal(receipt.nativeFeatureParityVerified, false); assert.equal(receipt.storeReady, false);
    writeRealPlists(f, binary, "../escaped.app");
    assert.throws(() => verifyBundle({...options, archiveDir: f.archive}), /Archive application path/);
  });
test("production plist reader rejects malformed or oversized metadata", (t) => {
  const f = fixture(t); const file = path.join(f.archive, "Info.plist");
  assert.throws(() => readPlist(file));
  fs.writeFileSync(file, Buffer.alloc(1048577)); assert.throws(() => readPlist(file));
});
