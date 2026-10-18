const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readSdkConfig} = require('./android-sdk-config.cjs');
const rn = 'compileSdkVersion = 37\ntargetSdkVersion = 36\nbuildToolsVersion = "37.0.0"\nndkVersion = "27.1.12297006"\n';
const compose = 'compileSdk = 35\n';
test('CI installs both app platforms and the declared build tools and NDK', () => {
  assert.deepEqual(readSdkConfig(rn, compose).packages, ['platform-tools', 'platforms;android-37.0',
    'platforms;android-35', 'build-tools;37.0.0', 'ndk;27.1.12297006']);
});
test('SDK changes propagate without lowering the application SDK', () => {
  const config = readSdkConfig(rn.replace('= 37', '= 38').replace('37.0.0', '38.0.0'), compose);
  assert.equal(config.compileSdk, 38);
  assert.equal(config.buildTools, '38.0.0');
  assert.ok(config.packages.includes('platforms;android-38.0'));
});
test('Windows line endings and shared platforms are supported', () => {
  const config = readSdkConfig(rn.replaceAll('\n', '\r\n'), 'compileSdk = 37\r\n');
  assert.equal(config.packages.filter(p => p === 'platforms;android-37.0').length, 1);
});
test('missing or malformed toolchain values fail instead of installing a guessed SDK', () => {
  assert.throws(() => readSdkConfig(rn.replace('compileSdkVersion = 37', '// compileSdkVersion = 37'), compose), /Cannot read/);
  assert.throws(() => readSdkConfig(rn.replace('37.0.0', '37.0.0;unexpected'), compose), /Cannot read/);
  assert.throws(() => readSdkConfig(rn, '// compileSdk = 35'), /Cannot read/);
});
test('incompatible target SDK fails explicitly', () => {
  assert.throws(() => readSdkConfig(rn.replace('= 36', '= 38'), compose), /targetSdk exceeds/);
});
