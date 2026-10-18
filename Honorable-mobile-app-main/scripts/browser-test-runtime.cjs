// Prefer the project's pinned browser harness; NODE_PATH can provide an existing runtime.
let playwright;
try { playwright = require('../ui-previews/node_modules/playwright'); }
catch (error) { if (error.code !== 'MODULE_NOT_FOUND') throw error; playwright = require('playwright'); }
const browserExecutable = process.env.HONORABLE_BROWSER_EXECUTABLE;
module.exports = {
  chromium: playwright.chromium,
  browserOptions: browserExecutable ? {executablePath: browserExecutable} : {},
};
