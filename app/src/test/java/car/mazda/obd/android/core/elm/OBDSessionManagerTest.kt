package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.core.elm.transport.ElmTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class OBDSessionManagerTest {
    @Test
    fun initialConnectionRetriesWhenAdapterBecomesAvailable() = runBlocking {
        val transport = InitialConnectionTransport(failConnectAttempts = 1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = OBDSessionManager(
            client = OBDClient(transport),
            scope = scope,
            requiredPids = emptySet(),
            reconnectDelayMs = 0,
        )

        try {
            manager.connectUntilReady()

            assertEquals(2, transport.connectAttempts)
            assertTrue(manager.sessionState.value is OBDSessionState.Ready)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun initialConnectionStopsRetryingAfterFatalProtocolError() = runBlocking {
        val transport = InitialConnectionTransport(initResponses = listOf("STOPPED\r>"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = OBDSessionManager(
            client = OBDClient(transport),
            scope = scope,
            requiredPids = emptySet(),
            reconnectDelayMs = 0,
        )

        try {
            manager.connectUntilReady()

            assertEquals(1, transport.connectAttempts)
            assertTrue(manager.sessionState.value is OBDSessionState.Error)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun reconnectsWhenAdapterReturnsAfterConnectionLoss() = runBlocking {
        val transport = InitialConnectionTransport(failConnectAttempts = 2)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = OBDSessionManager(
            client = OBDClient(transport),
            scope = scope,
            requiredPids = emptySet(),
            reconnectDelayMs = 0,
        )

        try {
            manager.requestReconnect(AdapterUnreachableException())

            withTimeout(1_000) {
                while (manager.sessionState.value !is OBDSessionState.Ready) yield()
            }

            assertEquals(3, transport.connectAttempts)
        } finally {
            scope.cancel()
        }
    }

    private class InitialConnectionTransport(
        private val failConnectAttempts: Int = 0,
        initResponses: List<String> = defaultInitResponses,
    ) : ElmTransport {
        private val responses = ArrayDeque(initResponses)
        var connectAttempts = 0
            private set

        override suspend fun connect() {
            connectAttempts++
            if (connectAttempts <= failConnectAttempts) throw AdapterUnreachableException()
        }

        override suspend fun exchange(command: String, readTimeoutMs: Int): String =
            responses.removeFirst()

        override fun disconnect() = Unit

        companion object {
            private val defaultInitResponses = listOf(
                "ELM327 v1.5\r>",
                "OK\r>",
                "OK\r>",
                "OK\r>",
                "OK\r>",
                "OK\r>",
                "OK\r>",
            )
        }
    }
}
