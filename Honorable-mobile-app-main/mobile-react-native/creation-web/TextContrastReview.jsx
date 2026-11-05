import React from 'react';
import { reviewTextColors, formatContrastRatio } from './text-contrast.mjs';

/** Comparison is explicit and read-only; it never guesses the actual background behind text. */
export function TextContrastReview({ elements, expanded, onToggle, background, onBackgroundChange, onLocate }) {
  let rows = [], error = '';
  if (expanded) { try { rows = reviewTextColors(elements, background); } catch (failure) { error = failure.message; } }
  return <section aria-label="Text color contrast">
    <button aria-expanded={expanded} onClick={onToggle}>Review text colors</button>
    {expanded && <>
      <h3>Compare against a solid background</h3>
      <label>Comparison background<input maxLength={9} value={background} onChange={event => onBackgroundChange(event.target.value)} placeholder="#ffffff" /></label>
      <p>4.5:1 is a normal-text color reference. This is not an accessibility certification.</p>
      <details><summary style={{ minHeight: 44, padding: '10px 0', boxSizing: 'border-box', fontSize: 14 }}>What this review compares</summary>
        <p>Compares all visible text colors, including text opacity, against this chosen background. Object filters do not limit this review. It does not sample photos, shapes, patterns or the actual background behind each word.</p>
        <p>Results use the unrounded ratio. Preview the final size, font, images and effects yourself.</p>
      </details>
      {error ? <p role="status">{error}</p> : <>
        <p>{rows.length} visible text colors · {rows.filter(row => row.ratio !== null && !row.meetsReference).length} below the reference.</p>
        {!rows.length && <p>Add visible text to compare its color.</p>}
        <ol>{rows.map(row => <li key={row.id}>
          <p><strong>Text {row.number}: {row.label || 'Empty text'}</strong><br />{row.ratio === null ? row.error : `${formatContrastRatio(row.ratio)}:1 · ${row.meetsReference ? 'At or above' : 'Below'} 4.5:1 reference`}</p>
          <button onClick={() => onLocate(row.id)} aria-label={`Locate reviewed text ${row.number}`}>Find on canvas</button>
        </li>)}</ol>
      </>}
    </>}
  </section>;
}
