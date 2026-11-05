const TYPES = new Set(['all', 'text', 'photos', 'shapes']);
const normalized = value => value.normalize('NFC').replace(/\s+/g, ' ').trim().toLowerCase();

/** Local composition metadata only. Never stores queries, resolves assets or changes artwork. */
export function findArtwork(elements, query = '', type = 'all') {
  if (!Array.isArray(elements) || elements.length > 500 || elements.some(element => !element || typeof element !== 'object')) throw Error('Unsupported object list.');
  if (typeof query !== 'string' || query.length > 100 || !TYPES.has(type)) throw Error('Use a search up to 100 characters and a supported object type.');
  const needle = normalized(query);
  return elements.filter(element => !element.isDeleted).map((element, index) => {
    const kind = element.type === 'image' ? 'photo' : element.type;
    const content = element.type === 'text' ? String(element.originalText ?? element.text ?? '') : '';
    const number = index + 1, hidden = !!element.customData?.nobleHidden || element.opacity === 0;
    return { element, number, hidden, label: content ? Array.from(content).slice(0, 40).join('') : `${kind} ${number}`, searchable: `${kind} ${element.type} ${number} ${content}` };
  }).filter(row => (type === 'all' || (type === 'photos' ? row.element.type === 'image' : type === 'text' ? row.element.type === 'text' : !['text', 'image'].includes(row.element.type))) && normalized(row.searchable).includes(needle))
    .reverse().map(({ searchable, ...row }) => row);
}
