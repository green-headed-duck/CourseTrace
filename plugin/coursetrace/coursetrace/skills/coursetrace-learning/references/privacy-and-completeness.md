# Privacy and completeness rules

- ChatGPT cannot passively read the user's other ChatGPT conversations or the Android app's private storage.
- A CourseTrace session contains only messages explicitly processed while the skill is active or later shared by the user.
- Never call a transcript complete merely because a summary is coherent. Completeness means every relevant classroom chat segment was appended.
- Keep the server relay minimal and short-lived. The Android app's private local Git repository is the canonical store.
- Do not request an OpenAI API key for ChatGPT Mobile integration. The user's ChatGPT subscription covers reasoning in ChatGPT; the separate OpenAI-compatible key is only for the Android PDF-import path chosen by the user.
- Do not expose pairing tokens, API keys, raw transcripts, recovery passwords, or material contents in logs.
- Deletion of sensitive Git-tracked text requires rebuilding local history; deleting only the working-tree file is insufficient.
- Never use accessibility automation, screen scraping, root, hidden Android APIs, or permission bypasses to capture ChatGPT content.
