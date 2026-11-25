package br.com.wdc.framework.cube.remote

/**
 * The version of the remote protocol: the messages between the host and its clients (handshake, form data and events,
 * scopes, released views). It goes up only when the wire changes in a way an older side cannot read.
 *
 * The client sends its version in the handshake (`"protocol": 1`, with its `clientKey`); the host answers with the
 * version it speaks (with its `serverKey`). A handshake with a version the host does not speak — or with none — and any
 * message before an accepted handshake are answered with [UNSUPPORTED] and the versions the host speaks:
 * `{"requestId": …, "error": "unsupported-protocol", "protocols": [1]}`. The client then tells the user the page is out
 * of date. The shared conformance fixtures (`conformance/` of the remote framework) describe it message by message.
 */
object RemoteProtocol {

    /** The version this host speaks. */
    const val VERSION = 1

    /** The versions this host accepts in a handshake. */
    val SUPPORTED: List<Int> = listOf(VERSION)

    /** The `error` of the answer to a client whose version is not spoken here. */
    const val UNSUPPORTED = "unsupported-protocol"
}
