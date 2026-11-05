/** Deterministic local design starters. Everything remains editable; no network or media input. */
export const STARTER_STYLES = ['quote', 'announcement', 'flyer'];
function plain(value, maximum, required, multiline = false) {
  if (typeof value !== 'string' || Array.from(value).length > maximum ||
      (multiline ? /[\x00-\x09\x0b-\x1f\x7f]/ : /[\x00-\x1f\x7f]/).test(value)) {
    throw Error(`Use plain ${multiline ? 'detail' : 'single-line'} text up to ${maximum} characters.`);
  }
  const text = value.trim();
  if (required && !text) throw Error('Add a headline before inserting your design.');
  if (multiline && text.split('\n').length > 3) throw Error('Use up to 3 detail lines; long lines wrap automatically.');
  return text;
}
function rect(x, y, width, height, fill = 'transparent', stroke = 'transparent') {
  return { type: 'rectangle', x, y, width, height, strokeColor: stroke, backgroundColor: fill, fillStyle: 'solid', strokeWidth: 1, roughness: 0 };
}
function label(value, x, y, width, height, fontSize, align, fill = 'transparent') {
  return { ...rect(x, y, width, height, fill), label: { text: value, fontSize, fontFamily: 2, strokeColor: '#111111', textAlign: align, verticalAlign: 'middle' } };
}
export function starterPlan(style, headline, details, signoff) {
  if (!STARTER_STYLES.includes(style)) throw Error('Choose a quote card, announcement or simple flyer.');
  headline = plain(headline, 60, true);
  details = plain(details, 180, false, true);
  signoff = plain(signoff, 60, false);
  const align = style === 'quote' ? 'center' : 'left';
  const shapes = [rect(0, 0, 640, 960, '#ffffff', '#111111')];
  shapes.push(rect(style === 'quote' ? 280 : 40, 40, 80, 6, '#111111'));
  shapes.push(label({ quote: 'WORDS TO KEEP', announcement: 'YOU ARE INVITED', flyer: 'IN THE SPOTLIGHT' }[style], 40, 64, 560, 48, 16, align));
  shapes.push(label(headline, 40, 132, 560, 220, 36, align));
  if (details) shapes.push(label(details, 40, 380, 560, 360, 24, align, style === 'flyer' ? '#eeeeee' : 'transparent'));
  if (signoff) {
    shapes.push(rect(40, 780, 560, 1, '#111111'));
    shapes.push(label(signoff, 40, 812, 560, 96, 18, align));
  }
  return shapes;
}
