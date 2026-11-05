module.exports = {
  root: true,
  parser: 'espree',
  env: {browser: true, node: true, es2022: true},
  parserOptions: {ecmaVersion: 2022, sourceType: 'module', ecmaFeatures: {jsx: true}},
  extends: ['eslint:recommended'],
  plugins: ['react', 'react-hooks'],
  globals: {NOBLE_CANVAS_FORMATS: 'readonly', NOBLE_HAS_NATIVE_REVIEWS: 'readonly', NOBLE_NATIVE_PHOTO_UI: 'readonly', NOBLE_NATIVE_VIDEO_TOOLS: 'readonly', NOBLE_NATIVE_VIDEO_GROUPS: 'readonly', NOBLE_NATIVE_STORY_UI: 'readonly', NOBLE_SELECTIVE_LABELS: 'readonly', NOBLE_SELECTIVE_INFO: 'readonly', NOBLE_MOTION_UI: 'readonly', NOBLE_NATIVE_DRAWING_UI: 'readonly'},
  rules: {'react/jsx-uses-react': 'error', 'react/jsx-uses-vars': 'error', 'react-hooks/rules-of-hooks': 'error'},
};
