# Milestone 0.6.5

## Gallery detail hardening

- Keep asynchronous detail responses scoped to the currently selected media item.
- Prevent an older media request from overwriting a newer selection.
- Preserve the short-lived view grant returned by the authorized media boundary.
- Surface media access and preview failures in the detail viewer instead of silently hiding the reason.
- Clear stale detail errors when closing or opening another item.

This milestone continues the 0.6.3 search/detail viewer and 0.6.4 gallery state polish.