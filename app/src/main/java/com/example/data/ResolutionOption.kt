package com.example.data

data class ResolutionOption(
    val format: String,
    val label: String,
    val badge: String,
    val description: String,
    val isAudio: Boolean = false,
    val isRecommended: Boolean = false
) {
    companion object {
        val ALL_OPTIONS = listOf(
            ResolutionOption(
                format = "1080",
                label = "1080p Full HD",
                badge = "1080p",
                description = "Maximum clarity & crisp detail"
            ),
            ResolutionOption(
                format = "720",
                label = "720p HD",
                badge = "720p",
                description = "Great balance of quality and size",
                isRecommended = true
            ),
            ResolutionOption(
                format = "480",
                label = "480p DVD Quality",
                badge = "480p",
                description = "Standard definition, quick save"
            ),
            ResolutionOption(
                format = "360",
                label = "360p Medium",
                badge = "360p",
                description = "Ideal for mobile screens"
            ),
            ResolutionOption(
                format = "240",
                label = "240p Low",
                badge = "240p",
                description = "Compact file size"
            ),
            ResolutionOption(
                format = "144",
                label = "144p Data Saver",
                badge = "144p",
                description = "Minimum data usage"
            ),
            ResolutionOption(
                format = "mp3",
                label = "MP3 Audio",
                badge = "MP3",
                description = "Audio only track",
                isAudio = true
            )
        )
    }
}
