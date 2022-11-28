
import http.HttpFileClient
import http3.Http3Client
import quic.QuicFileClient

fun main() {
    download(SMALL_FILE_REQUEST_PATH)
    download(BIG_FILE_REQUEST_PATH)
}

private fun download(path: String) {
    println("Use http download...")
    val httpClient = HttpFileClient(false, "localhost", HTTP_PORT)
    httpClient.request(path)
    httpClient.stop()
    println()

    println("Use https download...")
    val httpsClient = HttpFileClient(true, "localhost", HTTPS_PORT)
    httpsClient.request(path)
    httpsClient.stop()
    println()

    println("Use http3 download...")
    val http3Client = Http3Client("localhost", HTTP3_PORT)
    http3Client.request(path)
    http3Client.stop()
    println()

    println("Use quic/nginx download...")
    val quicClient = QuicFileClient("localhost", TCP_PORT)
    quicClient.request(path)
}
