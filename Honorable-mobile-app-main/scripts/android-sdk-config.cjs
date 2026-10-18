const fs = require('node:fs');
const path = require('node:path');

function readSdkConfig(rnGradle, composeGradle) {
  function read(source, expression, name) {
    const match = source.match(expression);
    if (!match) throw new Error('Cannot read Android ' + name + '; update the SDK reader for the new Gradle format.');
    return match[1];
  }
  const compileSdk = Number(read(rnGradle, /^\s*compileSdkVersion\s*=\s*(\d+)\s*$/m, 'compileSdkVersion'));
  const targetSdk = Number(read(rnGradle, /^\s*targetSdkVersion\s*=\s*(\d+)\s*$/m, 'targetSdkVersion'));
  const buildTools = read(rnGradle, /^\s*buildToolsVersion\s*=\s*"(\d+\.\d+\.\d+)"\s*$/m, 'buildToolsVersion');
  const ndk = read(rnGradle, /^\s*ndkVersion\s*=\s*"(\d+\.\d+\.\d+)"\s*$/m, 'ndkVersion');
  const composeSdk = Number(read(composeGradle, /^\s*compileSdk\s*=\s*(\d+)\s*$/m, 'Compose compileSdk'));
  if (targetSdk > compileSdk) throw new Error('Android targetSdk exceeds compileSdk.');
  // API 37+ platform packages use an explicit minor version, including .0.
  const platform = sdk => 'platforms;android-' + sdk + (sdk >= 37 ? '.0' : '');
  return {
    compileSdk, targetSdk, buildTools, ndk, composeSdk,
    packages: [...new Set(['platform-tools', platform(compileSdk),
      platform(composeSdk), 'build-tools;' + buildTools, 'ndk;' + ndk])],
  };
}

if (require.main === module) {
  const root = path.resolve(__dirname, '..');
  const config = readSdkConfig(
    fs.readFileSync(path.join(root, 'mobile-react-native/android/build.gradle'), 'utf8'),
    fs.readFileSync(path.join(root, 'android-app/app/build.gradle.kts'), 'utf8'),
  );
  const argument = process.argv[2];
  if (argument === '--packages') console.log(config.packages.join('\n'));
  else if (argument === '--build-tools') console.log(config.buildTools);
  else if (!argument) console.log(JSON.stringify(config, null, 2));
  else throw new Error('Unknown Android SDK reader argument.');
}
module.exports = {readSdkConfig};
