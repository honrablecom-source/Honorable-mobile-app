import test from "node:test";
import assert from "node:assert/strict";

await import("../src/unadded/name-collector.js");
const { collect } = globalThis.NovelLensUnadded;

test("collects repeated unknown proper names but excludes known characters and sentence words", () => {
  const root = { innerText: "After waking, Sunny met Cassie. Sunny spoke to Cassie. Sunless watched Sunny." };
  assert.deepEqual(collect(root, ["Sunless"]).map((item) => item.name), ["Sunny", "Cassie"]);
});

test("does not queue a single capitalized word as a character", () => {
  assert.deepEqual(collect({ innerText: "Eventually the door opened. Rain fell outside." }), []);
});
