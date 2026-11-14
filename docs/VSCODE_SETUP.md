# Noble in VS Code

This folder is a Git checkout. Saving a file changes the local checkout immediately.
To publish changes, open Source Control (Ctrl+Shift+G), review and stage the intended
files, enter a commit message, and click Commit. Then choose Terminal > Run Task >
Noble: sync GitHub. VS Code's built-in Sync Changes button is also available.

The task requires a clean checkout, pulls only when a fast-forward is possible,
then pushes the current branch. It stops on conflicts, divergent history or
authentication errors. It never automatically stages files or creates commits.
Background fetch refreshes remote status; it does not publish edits on save.

Open Terminal > New Terminal to start the Codex — Noble profile at this repository.
Codex uses its normal permissions and sign-in. If prompted, run `codex login` or
follow its sign-in instructions. Use the terminal dropdown to choose a normal
PowerShell session when desired.

For another computer, install Git, VS Code and Codex, clone this repository, and
configure your own Git author name/email. Credentials are never stored here.
