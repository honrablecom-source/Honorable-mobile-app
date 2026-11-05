const phoneImages = {
  search: '../ui-previews/01-home.png',
  studio: '../ui-rebuild/previews/11-studio-free.png',
  library: '../ui-rebuild/previews/06-memories.png',
};

const studioContent = {
  photo: {
    eyebrow: 'Photo workspace',
    title: 'More control, less clutter.',
    detail: 'A compact editor with the canvas first and adjustments close at hand.',
    image: '../ui-rebuild/previews/25-desktop-photo-editor.png',
    items: ['Crop and transform', 'Light, color, curves, and white balance', 'Sharpen, vignette, text, and saved looks'],
  },
  video: {
    eyebrow: 'Video workspace',
    title: 'Edit the moment that matters.',
    detail: 'A timeline-focused workspace for a clear cut, polished captions, and a protected export.',
    image: '../ui-rebuild/previews/26-desktop-video-editor.png',
    items: ['Trim, split, speed, and reorder', 'Titles, script captions, and SRT', 'Color, framing, music, fades, and export'],
  },
  drawing: {
    eyebrow: 'Creation workspace',
    title: 'Start with a blank canvas.',
    detail: 'A simple creation surface for sketches, shapes, notes, and a fresh visual idea.',
    image: '../ui-rebuild/previews/20-project-explorer.png',
    items: ['Blank image creation', 'Drawing tools and local drafts', 'Send finished artwork into the photo editor'],
  },
};

const phoneImage = document.querySelector('#phone-image');
document.querySelectorAll('[data-tab]').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('[data-tab]').forEach((tab) => tab.classList.remove('active'));
    button.classList.add('active');
    phoneImage.src = phoneImages[button.dataset.tab];
  });
});

const studioImage = document.querySelector('#studio-image');
const studioEyebrow = document.querySelector('#studio-eyebrow');
const studioTitle = document.querySelector('#studio-title');
const studioDetail = document.querySelector('#studio-detail');
const studioList = document.querySelector('#studio-list');

document.querySelectorAll('[data-studio]').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('[data-studio]').forEach((tab) => {
      tab.classList.remove('active');
      tab.setAttribute('aria-selected', 'false');
    });
    button.classList.add('active');
    button.setAttribute('aria-selected', 'true');

    const content = studioContent[button.dataset.studio];
    studioEyebrow.textContent = content.eyebrow;
    studioTitle.textContent = content.title;
    studioDetail.textContent = content.detail;
    studioImage.src = content.image;
    studioImage.alt = `${content.eyebrow} preview`;
    studioList.replaceChildren(...content.items.map((item) => {
      const listItem = document.createElement('li');
      listItem.textContent = item;
      return listItem;
    }));
  });
});

document.querySelector('[data-open-preview]')?.addEventListener('click', () => {
  document.querySelector('.phone-stage')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
});
