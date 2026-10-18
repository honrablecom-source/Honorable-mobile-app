const path = require('node:path');
const fs = require('node:fs');
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');
const {withNativeWind} = require('nativewind/metro');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = {
  // Windows SUBST keeps native build paths short, but Node may resolve a package
  // through the physical drive. Metro must see both paths to hash that file.
  watchFolders: fs.realpathSync.native(__dirname) === __dirname
    ? []
    : [fs.realpathSync.native(__dirname)],
  resolver: {
    resolveRequest(context, moduleName, platform) {
      // Match the project-root alias used by TypeScript and Jest at runtime.
      const resolvedName = moduleName.startsWith('@/')
        ? path.resolve(__dirname, moduleName.slice(2))
        : moduleName;
      return context.resolveRequest(context, resolvedName, platform);
    },
  },
};

module.exports = withNativeWind(mergeConfig(getDefaultConfig(__dirname), config), {input: './global.css', inlineRem: 16});
