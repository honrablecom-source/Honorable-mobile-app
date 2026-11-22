import {EventEmitter} from 'node:events';
import {createInterface} from 'node:readline';

// The native Windows host and this service communicate over inherited pipes.
const channel = new EventEmitter();
channel.postMessage = message => process.stdout.write(JSON.stringify(message) + '\n');
process.parentPort = channel;
const input = createInterface({input:process.stdin});
input.on('line', line => {
  try {
    channel.emit('message', {data:JSON.parse(line)});
  } catch {
    // A malformed message cannot satisfy a pending editor request.
  }
});
input.on('close', () => process.exit());
try {
  await import('./studio/full-editors/preview.mjs');
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
