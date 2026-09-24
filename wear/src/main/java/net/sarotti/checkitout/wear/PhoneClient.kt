package net.sarotti.checkitout.wear

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import net.sarotti.checkitout.shared.LikeRequest
import net.sarotti.checkitout.shared.LikeResult
import net.sarotti.checkitout.shared.WearProtocol
import java.util.UUID

/** Sends a like request to the paired phone and waits for its reply. */
class PhoneClient(context: Context) {
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)

    sealed interface Outcome {
        data class Replied(val result: LikeResult) : Outcome
        data object NoPhone : Outcome
        data object Timeout : Outcome
    }

    suspend fun like(tappedAt: Long = System.currentTimeMillis()): Outcome {
        val nodes = try {
            capabilityClient
                .getCapability(WearProtocol.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() ?: return Outcome.NoPhone

        val request = LikeRequest(requestId = UUID.randomUUID().toString(), tappedAt = tappedAt)
        val reply = CompletableDeferred<LikeResult>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path != WearProtocol.PATH_LIKE_RESULT) return@OnMessageReceivedListener
            val result = LikeResult.fromBytes(event.data) ?: return@OnMessageReceivedListener
            if (result.requestId == request.requestId) reply.complete(result)
        }
        messageClient.addListener(listener).await()
        return try {
            messageClient.sendMessage(node.id, WearProtocol.PATH_LIKE, request.toBytes()).await()
            withTimeoutOrNull(RESULT_TIMEOUT_MS) { reply.await() }
                ?.let { Outcome.Replied(it) }
                ?: Outcome.Timeout
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.NoPhone
        } finally {
            messageClient.removeListener(listener)
        }
    }

    private companion object {
        const val RESULT_TIMEOUT_MS = 8_000L
    }
}
