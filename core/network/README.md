# :core:network

The network layer: OkHttp 5 + Retrofit 3 + kotlinx.serialization.

- Twitch **GraphQL** data source (top live channels, categories, playback access token → HLS `.m3u8`).
- Anonymous Twitch **IRC-over-WebSocket** chat socket + parser.
Exposes data-source interfaces consumed by `:core:data`.
