import test from 'node:test';
import assert from 'node:assert/strict';
import { GifReader } from 'omggif';
import {
  checkedGifOptions,
  checkedGif,
  createGifSession,
  sendGif,
  MAX_GIF_BYTES,
} from '../model-gif.mjs';
import { startGifWorker } from '../model-gif-worker-client.mjs';
function pixels(frame = 0) {
  const p = new Uint8Array(16 * 16 * 4).fill(255);
  for (let y = 4; y < 8; y++)
    for (let x = frame; x < frame + 4; x++) {
      const offset = (y * 16 + x) * 4;
      p[offset + 1] = 0;
      p[offset + 2] = 0;
    }
  return p;
}
function encoded() {
  const session = createGifSession({ width: 16, height: 16, duration: 1 });
  for (let i = 0; i < 10; i++) session.frame(i, pixels(i));
  return session.finish();
}
test('real gifenc output decodes independently with exact motion, timing and loop metadata', () => {
  const bytes = encoded(),
    reader = new GifReader(bytes);
  assert.equal(reader.width, 16);
  assert.equal(reader.height, 16);
  assert.equal(reader.numFrames(), 10);
  assert.equal(reader.loopCount(), 0);
  for (let i = 0; i < 10; i++) assert.equal(reader.frameInfo(i).delay, 10);
  const first = new Uint8Array(16 * 16 * 4),
    last = new Uint8Array(first.length);
  reader.decodeAndBlitFrameRGBA(0, first);
  reader.decodeAndBlitFrameRGBA(9, last);
  assert.deepEqual(
    [...first.subarray(4 * 16 * 4, 4 * 16 * 4 + 4)],
    [255, 0, 0, 255],
  );
  assert.deepEqual(
    [...last.subarray(4 * 16 * 4, 4 * 16 * 4 + 4)],
    [255, 255, 255, 255],
  );
  assert.deepEqual(
    [...last.subarray((4 * 16 + 9) * 4, (4 * 16 + 9) * 4 + 4)],
    [255, 0, 0, 255],
  );
});
test('dimensions, duration, alpha, missing/duplicate/out-of-order frames and repeated finish refuse', () => {
  for (const patch of [
    { width: 0 },
    { height: 385 },
    { duration: 0 },
    { duration: 7 },
    { duration: 1.5 },
  ])
    assert.throws(() =>
      checkedGifOptions({ width: 16, height: 16, duration: 1, ...patch }),
    );
  const s = createGifSession({ width: 16, height: 16, duration: 1 });
  assert.throws(() => s.finish());
  assert.throws(() => s.frame(1, pixels()));
  assert.throws(() => s.frame(0, new Uint8Array(4)));
  const alpha = pixels();
  alpha[3] = 0;
  assert.throws(() => s.frame(0, alpha));
  s.frame(0, pixels());
  assert.throws(() => s.frame(0, pixels()));
  for (let i = 1; i < 10; i++) s.frame(i, pixels(i));
  s.finish();
  assert.throws(() => s.finish());
  assert.throws(() => s.frame(10, pixels()));
});
test('GIF binary transfer is exact across native chunks and failure releases only admitted request', () => {
  const base = encoded(),
    comment = [];
  for (let i = 0; i < 140; i++) comment.push(255, ...new Array(255).fill(65));
  const bytes = new Uint8Array([
    ...base.subarray(0, -1),
    0x21,
    0xfe,
    ...comment,
    0,
    0x3b,
  ]);
  assert.equal(new GifReader(bytes).numFrames(), 10);
  const chunks = [];
  let done = 0,
    released = 0;
  const bridge = {
    beginGifShare: (id, n) => id === 3 && n === bytes.length,
    appendGifShareChunk: (_, value) => {
      chunks.push(Buffer.from(value, 'base64'));
      return true;
    },
    finishGifShare: () => done++,
    releaseGifShare: () => released++,
  };
  sendGif(bridge, 3, bytes);
  assert(Buffer.concat(chunks).equals(Buffer.from(bytes)));
  assert(chunks.length > 1);
  assert.equal(done, 1);
  bridge.appendGifShareChunk = () => false;
  assert.throws(() => sendGif(bridge, 3, bytes));
  assert.equal(released, 1);
  bridge.beginGifShare = () => false;
  assert.throws(() => sendGif(bridge, 3, bytes));
  assert.equal(released, 1);
  assert.throws(() => sendGif({}, 0, bytes));
});
test('truncated, wrong-header, wrong-dimension and oversized GIF cannot reach sharing', () => {
  const bytes = encoded();
  assert.throws(() => checkedGif(bytes.subarray(0, -1)));
  const wrong = bytes.slice();
  wrong[0] = 0;
  assert.throws(() => checkedGif(wrong));
  wrong.set(bytes);
  wrong[6] = 0;
  wrong[7] = 0;
  assert.throws(() => checkedGif(wrong));
  assert.throws(() => checkedGif(new Uint8Array(MAX_GIF_BYTES + 1)));
});
test('worker client encodes one owned frame at a time and returns valid independently decoded output', async () => {
  let fake;
  const factory = () =>
    (fake = {
      session: null,
      terminated: false,
      postMessage(data) {
        queueMicrotask(() => {
          if (this.terminated) return;
          let result;
          if (data.type === 'init') {
            this.session = createGifSession(data.options);
            result = { id: data.id, type: 'ready' };
          } else if (data.type === 'frame') {
            result = {
              id: data.id,
              type: 'frame',
              count: this.session.frame(data.number, data.pixels),
            };
          } else
            result = {
              id: data.id,
              type: 'done',
              bytes: this.session.finish().buffer,
            };
          this.onmessage({ data: result });
        });
      },
      terminate() {
        this.terminated = true;
      },
    });
  const client = startGifWorker(
    'fixture',
    { width: 16, height: 16, duration: 1 },
    null,
    factory,
  );
  await client.ready;
  for (let i = 0; i < 10; i++) await client.frame(i, pixels(i).buffer);
  const bytes = await client.finish();
  assert.equal(new GifReader(bytes).numFrames(), 10);
  assert(fake.terminated);
  await assert.rejects(() => client.frame(0, pixels().buffer));
});
test('abort terminates an in-flight worker; late replies cannot complete or return partial GIF', async () => {
  let fake;
  const controller = new AbortController(),
    client = startGifWorker(
      'fixture',
      { width: 16, height: 16, duration: 1 },
      controller.signal,
      () =>
        (fake = {
          postMessage(data) {
            if (data.type === 'init')
              queueMicrotask(() =>
                this.onmessage({ data: { id: data.id, type: 'ready' } }),
              );
            else this.last = data;
          },
          terminate() {
            this.terminated = true;
          },
        }),
    );
  await client.ready;
  const waiting = client.frame(0, pixels().buffer),
    refusal = assert.rejects(waiting, /stopped/);
  controller.abort();
  await refusal;
  assert(fake.terminated);
  fake.onmessage({ data: { id: fake.last.id, type: 'frame', count: 1 } });
  await assert.rejects(() => client.finish(), /stopped/);
  const stopped = new AbortController();
  stopped.abort();
  assert.throws(() =>
    startGifWorker(
      'fixture',
      { width: 16, height: 16, duration: 1 },
      stopped.signal,
      () => {},
    ),
  );
});
