import test from "node:test";
import assert from "node:assert/strict";
import { parseWikiDetails } from "../src/wiki/mediawiki-client.js";

test("removes citation noise, rejects reference-list summaries, and preserves relationship separators", () => {
  const parsed = {
    text: `<table><tr><th>Masters</th><td>Amiran † Bast † Dale<sup class="reference">[19]</sup></td></tr></table>
      <p>17 (Chapter 29) 18 (Chapter 309) 19 (Chapter 599) 20 (Chapter 751)</p>
      <p>Sunny is a resourceful survivor whose choices shape the central conflict and the people around him.</p>`,
    revid: 1
  };
  const character = { id: "shadow-slave:1082", wikiPageId: 1082, displayName: "Sunny", pageTitle: "Sunny", aliases: [] };
  const result = parseWikiDetails(parsed, null, character);
  assert.equal(result.summary, "Sunny is a resourceful survivor whose choices shape the central conflict and the people around him.");
  assert.equal(result.fields.Masters, "Amiran • Bast • Dale");
  assert.doesNotMatch(result.fields.Masters, /19|†/);
});
