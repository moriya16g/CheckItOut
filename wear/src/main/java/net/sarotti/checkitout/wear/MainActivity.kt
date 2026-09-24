package net.sarotti.checkitout.wear

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.sarotti.checkitout.shared.LikeResult

/** Launching this activity (app icon, tile, complication, hardware button) sends one like. */
class MainActivity : ComponentActivity() {

    private var uiState by mutableStateOf<UiState>(UiState.Sending)
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LikeScreen(uiState, onRetry = ::sendLike) }
        if (savedInstanceState == null) sendLike()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sendLike()
    }

    private fun sendLike() {
        if (job?.isActive == true) return
        val tappedAt = System.currentTimeMillis()
        job = lifecycleScope.launch {
            uiState = UiState.Sending
            val state = PhoneClient(this@MainActivity).like(tappedAt).toUiState()
            uiState = state
            vibrate(state is UiState.Saved)
            if (state is UiState.Saved) {
                delay(AUTO_FINISH_MS)
                finish()
            }
        }
    }

    private fun vibrate(success: Boolean) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val effect = if (success) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 120, 100, 120), -1)
        }
        vibrator.vibrate(effect)
    }

    private companion object {
        const val AUTO_FINISH_MS = 1_800L
    }
}

private sealed interface UiState {
    data object Sending : UiState
    data class Saved(val title: String?, val artist: String?) : UiState
    data class Failed(@StringRes val message: Int) : UiState
}

private fun PhoneClient.Outcome.toUiState(): UiState = when (this) {
    PhoneClient.Outcome.NoPhone -> UiState.Failed(R.string.status_no_phone)
    PhoneClient.Outcome.Timeout -> UiState.Failed(R.string.status_timeout)
    is PhoneClient.Outcome.Replied -> when (result.status) {
        LikeResult.Status.OK -> UiState.Saved(result.title, result.artist)
        LikeResult.Status.NO_TRACK -> UiState.Failed(R.string.status_no_track)
        LikeResult.Status.NO_PERMISSION -> UiState.Failed(R.string.status_no_permission)
        LikeResult.Status.ERROR -> UiState.Failed(R.string.status_error)
    }
}

@Composable
private fun LikeScreen(state: UiState, onRetry: () -> Unit) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (state) {
                    UiState.Sending -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.status_sending))
                    }
                    is UiState.Saved -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_like),
                            contentDescription = stringResource(R.string.status_ok),
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = state.title ?: stringResource(R.string.status_ok),
                            style = MaterialTheme.typography.title3,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.artist?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.caption1,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    is UiState.Failed -> {
                        Text(
                            text = stringResource(state.message),
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onRetry) {
                            Icon(
                                painter = painterResource(R.drawable.ic_like),
                                contentDescription = stringResource(R.string.like),
                            )
                        }
                    }
                }
            }
        }
    }
}
