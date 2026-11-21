# Noble Editing Agent on Windows

This source preview runs in Noble Video Studio through a Windows launcher and a loopback browser window. It uses the same receipt-verified FilmCraft engine as the existing local preview.

With Node and a local Ollama service running on port 11434, run `editing-agent\start-windows.cmd MODEL_NAME` from `full-editors`. Open [Video Studio](http://127.0.0.1:4181/video/) and select **Editing agent**. Choose two to twelve photos or supported video clips, describe the shots, and enter a brief. Review the proposed order and timing before choosing **Build editable timeline**. Save and export through FilmCraft's controls.

The in-app panel calls `draft_vertical_edit` over local MCP. The model receives the brief and shot descriptions. The model also receives clip duration limits. Media bytes stay in the editor. Use a locally installed model and disable cloud features in Ollama when keeping prompts on the device matters. [MCP protocol release notes](https://blog.modelcontextprotocol.io/posts/2026-07-28/) · [Ollama chat API](https://docs.ollama.com/api/chat)

The draft uses labeled photos and the opening portion of each selected clip. Edit the brief and choose **Revise with AI** to revise a draft before building the timeline. The browser launcher exposes this labeled montage workflow.

The installed Windows Studio also includes **Cinematic editing agent**. It measures motion and brightness from selected video moments and detects transients from the chosen music. The local model requests those measurements through MCP and chooses source moments. FilmCraft places them on an editable 9:16 timeline, with a quieter first section and cuts on detected beats. A middle revision keeps the first and last cuts at their original times and can be undone by returning to the preceding sequence. Use the desktop setup instructions in the adjacent Windows README.
