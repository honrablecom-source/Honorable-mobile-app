// Drive the actual Projects task switcher; never bypass the UI or open a closed panel implicitly.
const labels = { saved: 'Saved designs', files: 'Project files', images: 'Image copies', parts: 'Reusable parts', advanced: 'Advanced transfer' };
async function projectView(page, view) {
  const button = page.getByRole('button', { name: labels[view], exact: true });
  if (await button.getAttribute('aria-pressed') !== 'true') await button.click();
}
async function withProjectView(page, view, action) { await projectView(page, view); return action(); }
module.exports = { projectView, withProjectView };
