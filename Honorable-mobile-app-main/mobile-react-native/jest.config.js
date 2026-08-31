module.exports = {
  preset: '@react-native/jest-preset',
  transformIgnorePatterns: [
    'node_modules/(?!((@)?react-native|@react-native(-community)?|@react-navigation|nativewind|react-native-css-interop|@rn-primitives|lucide-react-native|react-native-reanimated|react-native-worklets)/)',
  ],
  moduleNameMapper: {'^@/(.*)$': '<rootDir>/$1', '^react-native-reanimated$':'<rootDir>/__mocks__/reanimated.js','^lucide-react-native$':'<rootDir>/__mocks__/lucide.js', '\\.css$': '<rootDir>/__mocks__/styleMock.js'},
};
