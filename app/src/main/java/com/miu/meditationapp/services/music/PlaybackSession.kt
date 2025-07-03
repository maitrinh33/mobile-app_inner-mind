package com.miu.meditationapp.services.music

import android.net.Uri

data class PlaybackSession(
    var currentSongId: String = "",
    var currentTitle: String = "",
    var currentUri: Uri? = null,
    var currentDuration: String = "",
    var isPlaying: Boolean = false,
    var shouldAutoPlay: Boolean = false
) 