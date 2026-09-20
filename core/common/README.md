# :core:common

Cross-cutting primitives with no feature knowledge:

- Coroutine `@Dispatcher` qualifiers + `DispatchersModule`.
- The media **seams** that let features cooperate without depending on each other: `SpeechStreamSource`
  (the Zundamon voice), `MouthTrackSource` (viseme frames for the renderer), `PcmSink` (broadcast audio),
  and `VoiceState`. `:feature:voice` implements them; `:feature:publish` and `:feature:avatar` consume them.
