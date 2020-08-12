package tech.relaycorp.gateway.background.endpoint

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tech.relaycorp.gateway.background.component
import tech.relaycorp.gateway.common.Logging.logger
import tech.relaycorp.gateway.domain.endpoint.EndpointRegistration
import java.util.logging.Level
import javax.inject.Inject

class EndpointPreRegistrationService : Service() {
    private val scope get() = CoroutineScope(Dispatchers.IO)

    @Inject
    lateinit var endpointRegistration: EndpointRegistration

    override fun onBind(intent: Intent): IBinder? {
        component.inject(this)
        val messenger = Messenger(
            object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    onMessageReceived(msg)
                }
            }
        )
        return messenger.binder
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    internal fun onMessageReceived(message: Message) {
        // The original message will be recycled when the scope changes
        val localMessage = Message.obtain(message)
        scope.launch {
            when (localMessage.what) {
                PREREGISTRATION_REQUEST -> reply(localMessage)
            }
        }
    }

    private suspend fun reply(requestMessage: Message) {
        val endpointApplicationId = getApplicationNameForUID(requestMessage.sendingUid)
        if (endpointApplicationId == null) {
            logger.log(Level.WARNING, "Could not get applicationId from caller")
            return
        }

        val craSerialized = endpointRegistration.authorize(endpointApplicationId)
        val replyMessage = Message.obtain(null, REGISTRATION_AUTHORIZATION, craSerialized)
        requestMessage.replyTo.send(replyMessage)
    }

    private fun getApplicationNameForUID(uid: Int) =
        applicationContext.packageManager.getNameForUid(uid)

    companion object {
        const val PREREGISTRATION_REQUEST = 1
        const val REGISTRATION_AUTHORIZATION = 2
    }
}