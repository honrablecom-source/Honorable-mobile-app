const id = new URLSearchParams(location.search).get('screen') || '01-home';
const phone = document.querySelector('#phone');

const darkChrome = new Set([
  '03-search-focus', '04-ai-searching', '07-media-viewer', '12-privacy',
  '13-honorable-plus', '16-dark-home', '17-dark-memories', '18-dark-terms-ai'
]);

phone.classList.toggle('dark', darkChrome.has(id));
phone.dataset.screen = id;

const A = 'assets/';

const topbar = (section = 'Private intelligence', state = 'On device') => `
  <header class="topbar">
    <div class="brand-lockup">
      <span class="brand-bubble">h</span>
      <span class="brand-copy"><b>Honorable</b><small>${section}</small></span>
    </div>
    <span class="device-pill"><i></i>${state}</span>
  </header>`;

const modalHead = (section, accent = 'lilac') => `
  <header class="modal-head">
    <div class="brand-lockup">
      <span class="brand-bubble ${accent}">h</span>
      <span class="brand-copy"><b>${section}</b><small>Private · on device</small></span>
    </div>
    <span class="round-control" aria-label="Close">×</span>
  </header>`;

const nav = active => `
  <nav class="dock" aria-label="Primary">
    ${[
      ['⌂', 'Home'], ['◉', 'Memories'], ['✦', 'Terms'], ['↻', 'Activity'], ['⚙', 'Settings']
    ].map(([icon, label]) => `
      <span class="dock-item ${label === active ? 'active' : ''}">
        <i>${icon}</i><small>${label}</small>
      </span>`).join('')}
  </nav>`;

const search = (text = 'Describe a memory…', label = 'Search naturally', cls = '') => `
  <div class="search-bubble ${cls}">
    <span class="search-spark">✦</span>
    <span class="search-copy"><small>${label}</small><b>${text}</b></span>
    <span class="search-go">↗</span>
  </div>`;

const action = (label, cls = '') => `
  <div class="bubble-action ${cls}"><b>${label}</b><span>↗</span></div>`;

const memory = (img, title, detail, tone = 'cyan', time = '') => `
  <article class="memory-card ${tone}">
    <img src="${A + img}" alt="">
    <span class="memory-dot">●</span>
    ${time ? `<span class="time-pill">${time}</span>` : ''}
    <div class="memory-copy"><b>${title}</b><small>${detail}</small></div>
  </article>`;

const sheet = () => `
  <div class="memory-mosaic">
    ${memory('memory_tennis_4k.webp', 'Saturday match', 'outdoors · blue shirt', 'cyan')}
    ${memory('memory_snow_car_4k.webp', 'Winter drive', 'snow · red vehicle', 'lilac')}
    ${memory('memory_birthday_4k.webp', 'Birthday light', 'cake · candles', 'blush')}
  </div>`;

const privacyPill = () => `
  <div class="privacy-pill">
    <span class="privacy-icon">⌁</span>
    <span><b>Yours stays yours</b><small>Search and media never leave this phone.</small></span>
    <i>✓</i>
  </div>`;

const home = (dark = false) => `
  <section class="screen home-screen ${dark ? 'midnight' : ''}">
    ${topbar('Private intelligence')}
    <div class="home-hero">
      <div class="bubble-scene" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
      <p class="kicker"><span></span> Your life, searchable</p>
      <h1>Find what<br><em>matters.</em></h1>
      <p class="body-copy">Describe a moment the way you remember it. Honorable finds it privately, right here.</p>
      ${search('A white beach with tall grass', 'Ask your memories', 'hero-search')}
    </div>
    <div class="home-bento">
      <article class="feature-card memories-card"><span class="feature-icon">◉</span><div><b>Memories</b><small>Find any moment</small></div><i>↗</i></article>
      <article class="feature-card terms-card"><span class="feature-icon">✦</span><div><b>Terms AI</b><small>Make fine print clear</small></div><i>↗</i></article>
    </div>
    ${privacyPill()}
    ${nav('Home')}
  </section>`;

const memories = (dark = false) => `
  <section class="screen memories-screen ${dark ? 'midnight' : ''}">
    ${topbar('Memory space', '9,812 local')}
    <div class="memory-ambient" aria-hidden="true"><i></i><i></i><i></i></div>
    <div class="page-intro memory-intro">
      <p class="kicker"><span></span> Search what only you remember</p>
      <h1>What do you<br><em>remember?</em></h1>
      <p class="body-copy">A color, a place, a tiny detail—start anywhere.</p>
    </div>
    ${search('Start describing…', 'Private memory search')}
    <div class="section-title memory-start-title"><h2>Start anywhere</h2><span>Made for your library</span></div>
    <article class="memory-feature-prompt">
      <div class="prompt-art" aria-hidden="true"><i></i><i></i><i></i><span>⌁</span></div>
      <div class="prompt-feature-copy"><small>TRY A SCENE</small><h2>White beach<br>with tall grass</h2><p>Place + texture + color</p></div>
      <span class="prompt-feature-go">↗</span>
    </article>
    <div class="memory-prompt-grid">
      <article class="prompt-tile car"><span>◒</span><small>COLOR + WEATHER</small><b>Red car<br>in snow</b><i>↗</i></article>
      <article class="prompt-tile cake"><span>✦</span><small>OBJECT + LIGHT</small><b>Birthday cake<br>by a window</b><i>↗</i></article>
    </div>
    <div class="memory-local-note">
      <span>✓</span><div><b>Private by default</b><small>Photos and searches stay on this device.</small></div><i>9,812 ready</i>
    </div>
    ${nav('Memories')}
  </section>`;

const searchFocus = () => `
  <section class="screen no-dock midnight search-focus-screen">
    ${modalHead('Memories AI', 'cyan')}
    <div class="focus-orbit" aria-hidden="true"><i></i><i></i><i></i></div>
    <p class="kicker"><span></span> Say it how you remember it</p>
    <h1>What are you<br><em>looking for?</em></h1>
    <p class="body-copy">A scene, a color, a person, a sign—even a feeling.</p>
    ${search('blue shirt at tennis', 'Your description', 'focus-field')}
    <div class="prompt-tools"><span>◌ Photo</span><span>▷ Video</span><span>⌁ Place</span></div>
    ${action('Search my private library', 'focus-action')}
    <div class="safe-note"><span>⌁</span><b>Private query</b><small>Nothing leaves this device</small></div>
  </section>`;

const searching = () => `
  <section class="screen no-dock midnight searching-screen">
    <div class="search-orbit" aria-label="Searching">
      <i class="orbit-one"></i><i class="orbit-two"></i><i class="orbit-three"></i>
      <span><b>h</b><small>thinking locally</small></span>
    </div>
    <p class="kicker"><span></span> Scanning your library</p>
    <h1>Finding your<br><em>moment…</em></h1>
    <div class="query-pill">“tennis outside”</div>
    <p class="body-copy">Looking at scenes, words, colors, time and visual meaning—all on this phone.</p>
    <div class="scan-steps"><span class="done">Scenes</span><span class="active">Meaning</span><span>Ranking</span></div>
  </section>`;

const resultScreen = (video = false) => `
  <section class="screen results-screen">
    ${topbar('Memory search', video ? '4 videos' : '12 matches')}
    <div class="results-heading majestic-results-heading">
      <div><p class="kicker"><span></span> ${video ? 'Video memories' : 'A private discovery'}</p><h1>Your moment,<br><em>found.</em></h1></div>
      <span class="result-count-bubble"><b>${video ? '04' : '12'}</b><small>${video ? 'Videos' : 'Matches'}</small></span>
    </div>
    <article class="hero-result">
      <img src="${A}memory_tennis_4k.webp" alt="Best matching tennis memory">
      <div class="result-badges"><span class="strong-pill">✦ Best match</span>${video ? '<span class="time-pill">01:42</span>' : ''}</div>
      <div class="hero-result-copy">
        <small>${video ? 'The exact video moment' : 'The scene you described'}</small>
        <h2>Blue shirt.<br>Open court.</h2>
        <div><span>tennis outside · visual match</span><b>Open ↗</b></div>
      </div>
    </article>
    <div class="result-summary-card">
      <span class="summary-orb ${video ? 'lilac' : 'cyan'}">${video ? '▶' : '✦'}</span>
      <div><small>${video ? 'Best moment' : 'Why this matched'}</small><b>${video ? 'Play from the strongest scene.' : 'Blue clothing, an outdoor court, and tennis.'}</b></div>
      <span class="summary-arrow">↗</span>
    </div>
    <div class="result-filter-row"><span class="active">${video ? 'Videos' : 'All'}</span><span>${video ? 'Moments' : 'Photos'}</span><span>${video ? 'Longest' : 'Videos'}</span></div>
    ${nav('Memories')}
  </section>`;

const viewer = () => `
  <section class="screen full-bleed no-dock viewer-screen">
    <img class="viewer-image" src="${A}memory_tennis_4k.webp" alt="Tennis memory">
    <div class="viewer-controls"><span>←</span><span>↗</span></div>
    <div class="viewer-sheet">
      <span class="strong-pill">✦ Best local match</span>
      <h1>The Saturday<br><em>match.</em></h1>
      <div class="viewer-stats"><span><small>Confidence</small><b>High</b></span><span><small>Moment</small><b>01:42</b></span><span><small>Evidence</small><b>Visual</b></span></div>
    </div>
  </section>`;

const terms = (dark = false) => `
  <section class="screen terms-screen ${dark ? 'midnight' : ''}">
    ${topbar('Terms intelligence')}
    <div class="terms-hero">
      <div class="terms-bubbles" aria-hidden="true"><i>§</i><i></i><i></i></div>
      <p class="kicker"><span></span> Clear answers, privately</p>
      <h1>Fine print,<br><em>made human.</em></h1>
      <p class="body-copy">Bring an agreement. Get the parts that matter, without sending it away.</p>
      <div class="import-bubbles">
        <div class="cyan"><b>↗</b><small>Paste link</small></div>
        <div class="lilac"><b>¶</b><small>Paste text</small></div>
        <div class="mint"><b>↑</b><small>Import file</small></div>
      </div>
    </div>
    ${action('Analyze agreement')}
    ${privacyPill()}
    ${nav('Terms')}
  </section>`;

const termsResult = () => `
  <section class="screen no-dock terms-result-screen">
    ${topbar('Terms intelligence', 'Analysis ready')}
    <div class="verdict-head"><div><p class="kicker"><span></span> Agreement health</p><h1>Here’s the<br><em>real story.</em></h1></div><span class="score-bubble"><b>58</b><small>Review</small></span></div>
    <div class="risk-card"><span class="risk-face">~</span><div><small>Moderate attention</small><h2>A few clauses need a closer look.</h2><p>Renewal and dispute limits deserve your attention.</p></div></div>
    <div class="summary-card"><span>✦</span><div><small>One-minute read</small><p>Renews annually. Cancel before billing; refunds are limited.</p></div></div>
    <div class="disclosure-list">
      ${[
        ['!', 'Important', 'blush'], ['⌁', 'Watch out', 'lilac'], ['✓', 'Good', 'mint'],
        ['$', 'Money', 'cyan'], ['×', 'Cancellation', 'blush'], ['◉', 'Privacy', 'lilac'], ['↗', 'Your rights', 'mint']
      ].map(([icon, label, tone]) => `<div class="disclosure-row"><span class="${tone}">${icon}</span><b>${label}</b><i>⌄</i></div>`).join('')}
    </div>
  </section>`;

const activity = () => `
  <section class="screen activity-screen">
    ${topbar('Private activity', 'Today')}
    <div class="page-intro compact">
      <span class="floating-glyph blush">↻</span>
      <p class="kicker"><span></span> A quiet record</p>
      <h1>Your recent<br><em>sparks.</em></h1>
      <p class="body-copy">What Honorable understood, in one calm place.</p>
    </div>
    <div class="activity-list">
      ${[
        ['✦', 'Tennis outside', '12 private matches found', 'Now', 'cyan'],
        ['¶', 'Subscription terms', 'Moderate agreement health', 'Today', 'lilac'],
        ['◉', 'Library refreshed', '48 new memories indexed', '12 Aug', 'mint'],
        ['⌁', 'Flight screenshot', 'Air Canada text matched', '11 Aug', 'blush']
      ].map(([icon, title, detail, date, tone]) => `<article class="activity-row"><span class="activity-icon ${tone}">${icon}</span><div><b>${title}</b><small>${detail}</small></div><time>${date}</time></article>`).join('')}
    </div>
    ${nav('Activity')}
  </section>`;

const settingGroup = (label, items) => `
  <div class="setting-group"><div class="setting-label">${label}</div>${items.map(([icon, title, detail, tone]) => `
    <div class="setting-row"><span class="setting-icon ${tone}">${icon}</span><div><b>${title}</b><small>${detail}</small></div><i>›</i></div>`).join('')}</div>`;

const settings = () => `
  <section class="screen settings-screen">
    ${topbar('Your space')}
    <div class="settings-head"><div class="profile-bubble">H<span>✓</span></div><div><small>Built around you</small><h1>Settings</h1></div></div>
    <div class="plus-card"><span class="plus-orb">+</span><div><small>Honorable Plus</small><h2>More private intelligence.</h2><p>Advanced tools. Same private foundation.</p></div><i>↗</i></div>
    <div class="settings-stack">
      ${settingGroup('Privacy', [['⌁', 'Privacy promise', 'Everything stays local', 'mint'], ['◉', 'Permissions', 'Photos and videos', 'cyan']])}
      ${settingGroup('Intelligence', [['✦', 'AI & search', 'On-device processing', 'lilac'], ['▦', 'Storage & index', '9,812 local memories', 'blush']])}
      ${settingGroup('Honorable', [['◐', 'Appearance', 'Soft cloud', 'cyan'], ['h', 'About', 'Version 0.1.0', 'mint']])}
    </div>
    ${nav('Settings')}
  </section>`;

const privacy = () => `
  <section class="screen no-dock midnight privacy-screen">
    ${modalHead('Our promise', 'mint')}
    <div class="promise-orb"><i></i><span>⌁</span><b>100%</b><small>on this device</small></div>
    <p class="kicker centered"><span></span> Private by architecture</p>
    <h1>Your memories<br><em>stay yours.</em></h1>
    <p class="body-copy centered-copy">Intelligence should never require a cloud copy of your life.</p>
    <div class="promise-grid">
      <div class="cyan"><span>✦</span><b>Local AI</b></div>
      <div class="lilac"><span>¶</span><b>Local OCR</b></div>
      <div class="mint"><span>◉</span><b>Local index</b></div>
      <div class="blush"><span>×</span><b>No upload</b></div>
    </div>
    <div class="safe-note promise-note"><span>✓</span><b>Your content stays here</b><small>Photos, frames and search text remain local</small></div>
  </section>`;

const plus = () => `
  <section class="screen no-dock midnight plus-screen">
    ${modalHead('Honorable Plus', 'lilac')}
    <div class="plus-intro">
      <div class="plus-sculpture"><i></i><i></i><span>+</span></div>
      <p class="kicker"><span></span> More ways to understand</p>
      <h1>Go deeper.<br><em>Stay private.</em></h1>
      <p class="body-copy">Advanced intelligence, built on the same private foundation.</p>
    </div>
    <div class="benefit-cloud">${['Advanced Memories AI', 'Video search', 'Terms AI', 'No ads', 'Future intelligence'].map((x, i) => `<span class="tone-${i + 1}">✓ ${x}</span>`).join('')}</div>
    <div class="plan-bubbles"><div><small>Monthly</small><b>$5.99</b><span>per month</span></div><div class="selected"><small>Best value · Annual</small><b>$39.99</b><span>per year</span></div></div>
    ${action('Continue with annual', 'plus-action')}
    <p class="preview-note">Preview only · store processing not configured</p>
  </section>`;

const indexing = () => `
  <section class="screen no-dock indexing-screen">
    ${topbar('Memory space', 'Working locally')}
    <div class="index-sculpture"><i></i><i></i><i></i><span><b>76%</b><small>ready</small></span></div>
    <p class="kicker centered"><span></span> Building your private index</p>
    <h1>Your library is<br><em>waking up.</em></h1>
    <p class="body-copy centered-copy">You can leave this screen. Honorable keeps preparing safely.</p>
    <div class="progress-card"><div><span style="width:76%"></span></div><p><b>7,482 of 9,812</b><small>Photos complete · video frames next</small></p></div>
    ${privacyPill()}
  </section>`;

const empty = () => `
  <section class="screen no-dock empty-screen">
    ${topbar('Memory search', 'No clear match')}
    <div class="empty-sculpture"><i></i><i></i><span>⌕</span></div>
    <p class="kicker centered"><span></span> Nothing clear yet</p>
    <h1>Let’s try a<br><em>new detail.</em></h1>
    <p class="body-copy centered-copy">Add a place, color, date, or words visible in the image.</p>
    <div class="suggestion-cloud"><span class="cyan">＋ A date</span><span class="lilac">◉ Media type</span><span class="blush">● A color</span></div>
    ${action('Refine the search', 'empty-action')}
  </section>`;

const failure = () => `
  <section class="screen no-dock empty-screen failure-screen">
    ${topbar('Memory search', 'Paused safely')}
    <div class="empty-sculpture"><i></i><i></i><span>!</span></div>
    <p class="kicker centered"><span></span> Your library is safe</p>
    <h1>Something<br><em>tripped.</em></h1>
    <p class="body-copy centered-copy">Honorable paused without uploading, deleting, or changing your media.</p>
    <div class="safe-note failure-note"><span>✓</span><b>Nothing was changed</b><small>Close and return whenever you are ready</small></div>
  </section>`;

const screens = {
  'ios-01-home': () => '<section class="screen full-bleed no-rail"><img class="ios-source-preview" src="ios-home.svg" alt="iOS Honorable Home"></section>',
  '01-home': () => home(false),
  '02-memories': () => memories(false),
  '03-search-focus': searchFocus,
  '04-ai-searching': searching,
  '05-search-results': () => resultScreen(false),
  '06-video-results': () => resultScreen(true),
  '07-media-viewer': viewer,
  '08-terms-ai': () => terms(false),
  '09-terms-result': termsResult,
  '10-activity': activity,
  '11-settings': settings,
  '12-privacy': privacy,
  '13-honorable-plus': plus,
  '14-indexing': indexing,
  '15-empty-search': empty,
  '16-dark-home': () => home(true),
  '17-dark-memories': () => memories(true),
  '18-dark-terms-ai': () => terms(true),
  '19-error-state': failure
};

document.querySelector('#screen').innerHTML = (screens[id] || screens['01-home'])();
