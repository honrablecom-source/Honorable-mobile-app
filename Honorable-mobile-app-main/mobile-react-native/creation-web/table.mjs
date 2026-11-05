/** Plain text only: no formulas, HTML, links, remote input or spreadsheet execution. */
export function tablePlan(title, input) {
  if (typeof title !== 'string' || !title.trim() || title.length > 60 || /[\x00-\x1f\x7f]/.test(title)) throw Error('Use a table title of 1–60 characters on one line.');
  if (typeof input !== 'string' || input.length > 4096) throw Error('Use table text up to 4,096 characters.');
  const lines = input.replace(/\r\n/g, '\n').split('\n');
  while (lines.length && !lines[0].trim()) lines.shift();
  while (lines.length && !lines[lines.length - 1].trim()) lines.pop();
  if (lines.length < 2 || lines.length > 10) throw Error('Use 2–10 rows including the header.');
  const separator = lines[0].includes('\t') ? '\t' : '|';
  const rows = lines.map(line => line.split(separator).map(value => {
    if (/[\x00-\x1f\x7f]/.test(value)) throw Error('Use plain cell text without control characters.');
    return value.trim();
  }));
  const columns = rows[0].length;
  if (columns < 2 || columns > 6 || rows.some(row => row.length !== columns)) throw Error('Use 2–6 columns, with the same number of cells in every row. Separate cells with tabs or |.');
  if (rows.some(row => row.every(value => !value) || row.some(value => Array.from(value).length > 64 || /[\x00-\x1f\x7f]/.test(value)))) throw Error('Use up to 64 characters per cell and at least one nonempty cell per row.');
  const shapes = [{ type: 'text', text: title.trim(), x: 0, y: 0, fontSize: 28, fontFamily: 2, strokeColor: '#111111' }];
  let y = 56;
  rows.forEach((row, index) => {
    // Conservative space for font wrapping, including wide Unicode text. Renderer keeps labels bound.
    const length = Math.max(...row.map(value => Array.from(value).length));
    const height = Math.max(64, Math.ceil(length * 24 / 200) * 20 + 24);
    row.forEach((value, column) => {
      shapes.push({ type: 'rectangle', x: column * 220, y, width: 220, height,
        strokeColor: '#111111', backgroundColor: index === 0 ? '#eeeeee' : '#ffffff', fillStyle: 'solid', strokeWidth: 1, roughness: 0,
        ...(value ? { label: { text: value, fontSize: 16, fontFamily: 2, textAlign: 'left', verticalAlign: 'middle' } } : {}),
      });
    });
    y += height;
  });
  return shapes;
}
