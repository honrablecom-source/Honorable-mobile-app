/** Inclusive calendar-day schedule artwork. No clock, locale, network or business-day assumptions. */
const DAY = 86400000;
const INK = '#111111';
const plain = (value, limit, what) => {
  if (typeof value !== 'string' || !value.trim() || Array.from(value).length > limit || /[\x00-\x1f\x7f]/.test(value)) throw Error(`Use ${what} of 1–${limit} characters on one line.`);
  return value.trim();
};
export function scheduleDay(value) {
  if (typeof value !== 'string' || !/^(?:19|20|21|22)\d{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])$/.test(value)) throw Error('Use real dates as YYYY-MM-DD between 1900 and 2299.');
  const [year, month, day] = value.split('-').map(Number);
  const stamp = Date.UTC(year, month - 1, day);
  if (new Date(stamp).toISOString().slice(0, 10) !== value) throw Error('Use real calendar dates; check the month and leap year.');
  return stamp / DAY;
}
export function parseSchedule(title, input) {
  const heading = plain(title, 60, 'a schedule title');
  if (typeof input !== 'string' || input.length > 4096) throw Error('Use up to 4,096 characters of task | start | finish rows.');
  const rows = input.trim().split(/\r?\n/);
  if (!input.trim() || rows.length > 16) throw Error('Use 1–16 task | start | finish rows.');
  const tasks = rows.map(row => {
    const parts = row.split('|');
    if (parts.length !== 3) throw Error('Each row needs task | YYYY-MM-DD | YYYY-MM-DD.');
    const name = plain(parts[0], 48, 'a task name'), startText = parts[1].trim(), finishText = parts[2].trim();
    const start = scheduleDay(startText), finish = scheduleDay(finishText);
    if (finish < start) throw Error('Task finish must be on or after its start date.');
    return { name, start, finish, startText, finishText, days: finish - start + 1 };
  });
  const first = Math.min(...tasks.map(task => task.start)), last = Math.max(...tasks.map(task => task.finish)), days = last - first + 1;
  if (days > 366) throw Error('Keep the whole schedule within 366 inclusive calendar days.');
  return { title: heading, tasks, first, last, days };
}
const text = (value, x, y, fontSize = 16) => ({ type: 'text', text: value, x, y, fontSize, fontFamily: 2, strokeColor: INK });
const rect = (x, y, width, height, color) => ({ type: 'rectangle', x, y, width, height, strokeColor: INK, backgroundColor: color, fillStyle: 'solid', roughness: 0, strokeWidth: 1 });
const dateText = day => new Date(day * DAY).toISOString().slice(0, 10);
function wrap(value, columns) {
  const chars = Array.from(value), lines = [];
  while (chars.length) lines.push(chars.splice(0, columns).join(''));
  return lines.join('\n');
}
export function schedulePlan(title, input) {
  const schedule = parseSchedule(title, input), x = 300, width = 720, top = 144, rowHeight = 108, scale = width / schedule.days;
  const shapes = [text(schedule.title, 0, 0, 28), text(`${dateText(schedule.first)} → ${dateText(schedule.last)} · ${schedule.days} calendar days`, 0, 44), text('Inclusive dates · overlaps allowed · static planning artwork', 0, 72)];
  // At most seven intervals keeps labels legible for both short and year-long schedules.
  const step = Math.max(1, Math.ceil(schedule.days / 7));
  for (let offset = 0; offset < schedule.days; offset += step) {
    const left = x + offset * scale;
    shapes.push(text(dateText(schedule.first + offset), left, 108, 12));
    shapes.push({ type: 'line', x: left, y: top, width: 0, height: schedule.tasks.length * rowHeight, points: [[0, 0], [0, schedule.tasks.length * rowHeight]], strokeColor: '#cccccc', roughness: 0, strokeWidth: 1 });
  }
  schedule.tasks.forEach((task, index) => {
    const y = top + index * rowHeight, left = x + (task.start - schedule.first) * scale;
    shapes.push(text(wrap(task.name, 16), 0, y + 4));
    shapes.push(rect(left, y + 4, task.days * scale, 28, '#dddddd'));
    shapes.push(text(`${task.startText} → ${task.finishText} · ${task.days} ${task.days === 1 ? 'day' : 'days'}`, x, y + 44, 14));
  });
  return shapes;
}
