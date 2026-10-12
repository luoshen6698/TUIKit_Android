package io.trtc.tuikit.chat.demo.xingdun.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunNetworkFailureTest {
    @Test fun recognizesTransportCausesWithoutReturningTheirMessages() {
        assertEquals(XingDunNetworkFailure.DNS, XingDunNetworkFailure.from(UnknownHostException("private host")))
        assertEquals(XingDunNetworkFailure.TIMEOUT, XingDunNetworkFailure.from(SocketTimeoutException()))
        assertEquals(XingDunNetworkFailure.TLS, XingDunNetworkFailure.from(SSLHandshakeException("private cert")))
        assertEquals(XingDunNetworkFailure.CONNECTION, XingDunNetworkFailure.from(ConnectException()))
        assertEquals(XingDunNetworkFailure.PERMISSION, XingDunNetworkFailure.from(IOException("socket failed: EACCES")))
        assertEquals(XingDunNetworkFailure.OTHER, XingDunNetworkFailure.from(IOException("private payload")))
    }

    @Test fun recognizesWrappedTransportFailure() {
        assertEquals(XingDunNetworkFailure.TIMEOUT, XingDunNetworkFailure.from(IOException(SocketTimeoutException())))
    }
}
