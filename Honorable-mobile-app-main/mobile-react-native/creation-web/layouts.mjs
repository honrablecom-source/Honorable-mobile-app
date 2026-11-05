/** Local deterministic layout plans. Output is editable shapes/text, with no image or network input. */
const INK = '#111111';
function text(value, x, y, fontSize = 20) {
  return { type: 'text', text: value, x, y, fontSize, fontFamily: 2, strokeColor: INK };
}
function rectangle(x, y, width, height, filled = false) {
  return { type: 'rectangle', x, y, width, height, strokeColor: INK, backgroundColor: filled ? INK : 'transparent', fillStyle: 'solid', roughness: 0, strokeWidth: 1 };
}
function title(value) {
  if (typeof value !== 'string' || !value.trim() || value.length > 60 || /[\x00-\x1f\x7f]/.test(value)) throw Error('Use a title of 1–60 characters on one line.');
  return value.trim();
}
export function chartPlan(label, input) {
  const heading = title(label);
  if (typeof input !== 'string' || input.length > 1600) throw Error('Use up to 12 label | value rows.');
  const lines = input.trim().split('\n');
  if (lines.length < 1 || lines.length > 12) throw Error('Use 1–12 label | value rows.');
  const rows = lines.map(line => {
    const parts = line.split('|');
    if (parts.length !== 2) throw Error('Each row needs a label | numeric value.');
    const name = title(parts[0]);
    if (name.length > 24 || !/^-?(?:\d+(?:\.\d+)?|\.\d+)$/.test(parts[1].trim())) throw Error('Use labels up to 24 characters and decimal numeric values.');
    const value = Number(parts[1]);
    if (!Number.isFinite(value) || Math.abs(value) > 1e9) throw Error('Values must be finite and within ±1 billion.');
    return { name, value };
  });
  const minimum = Math.min(0, ...rows.map(row => row.value));
  const maximum = Math.max(0, ...rows.map(row => row.value));
  const range = maximum - minimum || 1, scale = 360 / range, zero = 260 - minimum * scale;
  const shapes = [text(heading, 0, 0, 28), text('Values · linear scale from zero', 0, 40, 16)];
  shapes.push({ type: 'line', x: zero, y: 74, points: [[0, 0], [0, rows.length * 52]], strokeColor: INK, roughness: 0 });
  rows.forEach((row, index) => {
    const y = 82 + index * 52, end = zero + row.value * scale;
    shapes.push(text(row.name, 0, y, 16));
    if (row.value !== 0) shapes.push(rectangle(Math.min(zero, end), y, Math.abs(end - zero), 24, true));
    shapes.push(text(String(row.value), 640, y, 16));
  });
  return shapes;
}
export function calendarPlan(month, monday = true) {
  if (typeof month !== 'string' || !/^(?:19|20|21|22)\d{2}-(?:0[1-9]|1[0-2])$/.test(month)) throw Error('Use a month as YYYY-MM between 1900 and 2299.');
  if (typeof monday !== 'boolean') throw Error('Choose a valid week start.');
  const [year, number] = month.split('-').map(Number);
  const first = new Date(Date.UTC(year, number - 1, 1));
  const days = new Date(Date.UTC(year, number, 0)).getUTCDate();
  const offset = (first.getUTCDay() + (monday ? 6 : 0)) % 7;
  const names = monday ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] : ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const shapes = [text(month, 0, 0, 28)];
  names.forEach((name, column) => shapes.push(text(name, column * 100 + 8, 52, 18)));
  const weeks = Math.ceil((offset + days) / 7);
  for (let week = 0; week < weeks; week++) for (let column = 0; column < 7; column++) {
    const day = week * 7 + column - offset + 1, x = column * 100, y = 84 + week * 90;
    shapes.push(rectangle(x, y, 100, 90));
    if (day >= 1 && day <= days) shapes.push(text(String(day), x + 8, y + 8, 18));
  }
  return shapes;
}
export function storyboardPlan(label, input) {
  const heading = title(label);
  if (typeof input !== 'string' || input.length > 1600) throw Error('Use 1–12 scene briefs, one per line.');
  const briefs = input.trim().split('\n').map(line => line.trim());
  if (briefs.length < 1 || briefs.length > 12 || briefs.some(line => !line || line.length > 100 || /[\x00-\x1f\x7f]/.test(line))) throw Error('Use 1–12 nonempty scene briefs, up to 100 characters each.');
  const shapes = [text(heading, 0, 0, 28)];
  briefs.forEach((brief, index) => {
    const x = index % 2 * 380, y = 64 + Math.floor(index / 2) * 340;
    const words = brief.split(/\s+/); let lines = [''];
    for (const word of words) { const last = lines.length - 1; if ((lines[last] + ' ' + word).trim().length > 20 && lines[last]) lines.push(word); else lines[last] = (lines[last] + ' ' + word).trim(); }
    // Long unbroken words are split rather than drawn beyond the card.
    lines = lines.flatMap(line => line.match(/.{1,20}/gu) || ['']);
    shapes.push(text(`Scene ${index + 1}`, x, y, 18), rectangle(x, y + 28, 340, 160), text(lines.join('\n'), x, y + 200, 16));
  });
  return shapes;
}
