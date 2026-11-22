// The visible menus follow the video engine's registry and its current selection.
export function mountVideoMenus(nav, {listMenus, toolsTrigger, run, currentExport, onError}) {
  const groups = ['File','Edit','Clip','Sequence','Markers','Graphics and Titles','View','Window','Tools','Help'];
  const popup = document.createElement('section');
  popup.className = 'video-menu-popup workspace-popover';
  popup.id = 'video-menu-popup';
  popup.setAttribute('popover','auto');
  popup.setAttribute('role','menu');
  nav.after(popup);
  let active;
  let commands = [];
  let path = [];
  let generation = 0;

  function row(label, action, suffix, disabled = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('role','menuitem');
    button.textContent = label;
    button.disabled = disabled;
    if (suffix) {
      const hint = document.createElement('span');
      hint.className = 'menu-hint';
      hint.textContent = suffix;
      button.append(hint);
    }
    button.onclick = action;
    popup.append(button);
    return button;
  }
  function execute(id) {
    popup.hidePopover();
    active.focus();
    void run(id);
  }
  function entries() { return [...popup.querySelectorAll('button:enabled')]; }
  function render(focus) {
    popup.replaceChildren();
    popup.setAttribute('aria-label',path.join(' / '));
    if (path.length > 1) {
      row('‹  '+path.at(-1), () => { path.pop(); render(true); }).className = 'menu-back';
    }
    const exportMenu = path.length === 2 && path[0] === 'File' && path[1] === 'Export';
    if (exportMenu) {
      row(currentExport() ? 'Return to editing' : 'Video (MP4)…', () => execute('file.exportMedia'));
      row('Advanced export…', () => execute('mode.export'));
    }
    const branches = new Set();
    const fileOrder = ['New','file.open','Open Recent','file.save','file.saveAs','file.import','Export'];
    const priority = cmd => {
      const index = fileOrder.indexOf(cmd.path[1] || cmd.id);
      return index < 0 ? fileOrder.length : index;
    };
    const items = path.length === 1 && path[0] === 'File'
      ? commands.slice().sort((a,b) => priority(a)-priority(b)) : commands;
    for (const cmd of items) {
      if (exportMenu && ['file.exportMedia','mode.export'].includes(cmd.id)) continue;
      if (!path.every((part,i) => cmd.path[i] === part)) continue;
      const next = cmd.path[path.length];
      if (next) {
        if (branches.has(next)) continue;
        branches.add(next);
        const branch = row(next, () => { path.push(next); render(true); }, '›');
        branch.setAttribute('aria-haspopup','menu');
      } else {
        row(cmd.label, () => execute(cmd.id), cmd.shortcut?.replaceAll('Cmd','Ctrl').replaceAll('Option','Alt'),!cmd.enabled);
      }
    }
    popup.scrollTop = 0;
    if (focus) entries()[0]?.focus();
  }
  async function open(button, focus = false) {
    const ticket = ++generation;
    if (active) active.setAttribute('aria-expanded','false');
    active = button;
    active.setAttribute('aria-expanded','true');
    path = [button.dataset.menu];
    popup.style.left = Math.max(4,Math.min(innerWidth-264,button.getBoundingClientRect().left))+'px';
    popup.replaceChildren();
    popup.textContent = 'Loading commands…';
    popup.showPopover();
    try {
      const items = await listMenus();
      if (ticket !== generation || !popup.matches(':popover-open')) return;
      commands = items;
      render(focus);
    } catch (error) {
      if (ticket === generation) { popup.hidePopover(); onError(error); }
    }
  }
  const buttons = groups.map(group => {
    const button = group === 'Tools' ? toolsTrigger : document.createElement('button');
    button.type = 'button';
    button.textContent = group === 'Graphics and Titles' ? 'Graphics' : group;
    button.dataset.menu = group;
    if (group !== 'Tools') {
      button.setAttribute('aria-haspopup','menu');
      button.setAttribute('aria-controls',popup.id);
      button.setAttribute('aria-expanded','false');
      let wasOpen = false;
      button.onpointerdown = () => { wasOpen = active === button && popup.matches(':popover-open'); };
      button.onclick = event => {
        if (active === button && ((event.detail && wasOpen) || popup.matches(':popover-open'))) popup.hidePopover();
        else void open(button);
        wasOpen = false;
      };
      button.onpointerenter = () => {
        if (active !== button && popup.matches(':popover-open')) void open(button);
      };
    }
    nav.append(button);
    return button;
  });
  popup.addEventListener('toggle',() => {
    if (!popup.matches(':popover-open')) active?.setAttribute('aria-expanded','false');
  });
  function adjacent(button, offset) {
    return buttons[(buttons.indexOf(button) + offset + buttons.length) % buttons.length];
  }
  nav.onkeydown = event => {
    const button = event.target.closest('[data-menu]');
    if (!button) return;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
      adjacent(button,event.key === 'ArrowLeft' ? -1 : 1).focus();
    } else if (event.key === 'ArrowDown') {
      event.preventDefault();
      if (button === toolsTrigger) button.click();
      else void open(button,true);
    }
  };
  popup.onkeydown = event => {
    const items = entries();
    if (!items.length) return;
    const selected = document.activeElement;
    const index = items.indexOf(selected);
    if (['ArrowUp','ArrowDown','Home','End'].includes(event.key)) {
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length-1 :
        (index+(event.key === 'ArrowDown' ? 1 : -1)+items.length)%items.length;
      items[next].focus();
    } else if (event.key === 'ArrowRight' && selected.getAttribute('aria-haspopup')) {
      event.preventDefault(); selected.click();
    } else if ((event.key === 'ArrowLeft' || event.key === 'Escape') && path.length > 1) {
      event.preventDefault(); path.pop(); render(true);
    } else if (event.key === 'Escape') {
      event.preventDefault(); popup.hidePopover(); active.focus();
    }
  };
  return buttons[0];
}
