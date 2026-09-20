package com.example.virtualtwitchdroid.feature.publish

/** Twitch RTMP ingest. The stream key comes from the user's Twitch Creator Dashboard. */
internal object TwitchIngest {
    /** Twitch's primary ingest endpoint; it auto-routes to the nearest region. */
    private const val RTMP_BASE = "rtmp://live.twitch.tv/app/"

    /** Builds the full RTMP publish URL for a given stream key. */
    fun rtmpUrl(streamKey: String): String = RTMP_BASE + streamKey.trim()
}
