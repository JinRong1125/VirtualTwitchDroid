# :feature:browse

Live-channel and category browsing — the **Games**, **Popular** and **Search** tabs.

- Fetches the current top live channels and game categories from Twitch's public **GraphQL** endpoint
  (via `:core:network`), exposed through `:core:data` repositories.
- `BrowseViewModel` maps the repository flows into a sealed Compose UI state (`stateIn`, loading/empty/error).
- Compose UI renders full-width stream thumbnails (Coil) with a red **Live** badge, circular avatar, title,
  and purple tag chips from `:core:designsystem`.
- No account needed — read-only public data.

Depends on `:core:{model,data,designsystem,common}`. No dependency on other feature modules
(enforced by `ArchitectureGuardTest`).
