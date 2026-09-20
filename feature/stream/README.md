# :feature:stream

The **watch** screen: a live player plus real-time chat, with UX modeled on the Xtra client.

- Plays a channel's live **HLS** stream with media3/ExoPlayer; a fading controller overlay (minimize→PiP,
  follow, quality/settings sheet, play/pause, mute, chat toggle, PiP, fullscreen, uptime + viewer count).
- **Live chat** over Twitch **IRC-on-WebSocket** (anonymous), parsed into colored names, badges, `/me`, and
  system-notice rows; an emote picker and message input.
- Fullscreen docks chat to the right in landscape; Picture-in-Picture shrinks to a 16:9 window.
- `StreamViewModel` drives a sealed UI state with `flatMapLatest` retry and a scan-reduced chat buffer.

Depends on `:core:{model,data,network,designsystem,common}`.
