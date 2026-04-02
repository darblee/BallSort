package com.darblee.ballsort

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.darblee.ballsort.ui.screens.GameScreen
import com.darblee.ballsort.ui.theme.BallSortTheme

lateinit var gAudio_victory: MediaPlayer

/**
 * The main entry point of the Ball Sort application.
 *
 * This activity handles the initialization of the game's user interface using Jetpack Compose,
 * manages global audio resources such as the victory sound effect, and enforces the
 * required screen orientation for the gameplay experience.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val historyFile = java.io.File(filesDir, Global.GAME_HISTORY_FILENAME)

        setContent {
            gAudio_victory = MediaPlayer.create(LocalContext.current, R.raw.victory)
            gAudio_victory.setAudioAttributes(playbackAttributes)

            ForcePortraitMode()
            BallSortTheme {
                MainViewImplementation(historyFile)
            }
        }
    }

    /**
     * Force to use portrait orientation
     */
    @SuppressLint("SourceLockedOrientationActivity")
    @Composable
    fun ForcePortraitMode() {
        val activity = LocalContext.current as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    /**
     * Override onDestroy(). Need to perform addition custom cleanup routine
     */
    override fun onDestroy() {
        super.onDestroy()
        gAudio_victory.release()
    }
}

@Composable
private fun MainViewImplementation(historyFile: java.io.File)
{
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        GameScreen(historyFile = historyFile, modifier = Modifier.padding(innerPadding))
    }
}