const draftKey = 'noble.editing-brief';

// Native Studio keeps home and each editor in separate views of the same origin.
export function connectComposer(form) {
  const native = window.nobleNavigation;
  const channel = native && new BroadcastChannel(draftKey);
  let pending;
  if (channel) channel.onmessage = ({data}) => {
    if (!pending || pending.medium !== data.medium) return;
    if (data.type === 'ready') channel.postMessage(pending);
    if (data.type === 'received' && data.id === pending.id) pending = undefined;
  };
  const input = form.querySelector('textarea');
  input.oninput = () => input.setCustomValidity('');
  form.onsubmit = event => {
    event.preventDefault();
    const brief = input.value.trim();
    if (!brief) {
      input.setCustomValidity('Describe your edit first.');
      input.reportValidity();
      return;
    }
    const medium = form.elements.medium.value;
    const url = new URL('/' + medium + '/?empty&webgl', location.origin);
    const draft = {type:'draft', medium, brief, id:crypto.randomUUID()};
    if (native) {
      pending = draft;
      native.openWorkspace(url.href);
      channel.postMessage(draft);
    } else {
      sessionStorage.setItem(draftKey, JSON.stringify(draft));
      location.assign(url);
    }
  };
}

export function receiveBrief(medium, apply) {
  const channel = new BroadcastChannel(draftKey);
  let lastId;
  channel.onmessage = ({data}) => {
    if (data.type !== 'draft' || data.medium !== medium) return;
    if (lastId !== data.id) {
      apply(data.brief);
      lastId = data.id;
    }
    channel.postMessage({type:'received', medium, id:data.id});
  };
  channel.postMessage({type:'ready', medium});
  const stored = sessionStorage.getItem(draftKey);
  if (stored) {
    const draft = JSON.parse(stored);
    if (draft.medium === medium) {
      sessionStorage.removeItem(draftKey);
      apply(draft.brief);
    }
  }
}