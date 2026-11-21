import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const [modules, output] = process.argv.slice(2);
const require = createRequire(resolve(modules, '..', 'package.json'));
await require('esbuild').build({
  entryPoints: [fileURLToPath(new URL('semantic-worker.js', import.meta.url))],
  outfile: output,
  nodePaths: [resolve(modules)],
  bundle: true,
  format: 'esm',
  platform: 'browser',
  minify: true,
  legalComments: 'inline',
});
