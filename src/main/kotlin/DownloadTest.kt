
import http.HttpFileClient
import http3.Http3Client

fun main(args: Array<String>) {
    download(SMALL_FILE_REQUEST_PATH)
    download(BIG_FILE_REQUEST_PATH)
}

private fun download(path: String) {
    val host = "localhost"
    println("Use http download...")
    val httpClient = HttpFileClient(false, host, HTTP_PORT)
    httpClient.request(path)
    httpClient.stop()
    println()

    println("Use https download...")
    val httpsClient = HttpFileClient(true, host, HTTPS_PORT)
    httpsClient.request(path)
    httpsClient.stop()
    println()

    println("Use http3 download...")
    val http3Client = Http3Client(host, HTTP3_PORT)
    http3Client.request(path)
    http3Client.stop()
    println()

    // nginx udp/quic 转发
/*    println("Use http3-udp download...")
    val http3Client1 = Http3Client("localhost", 8889)
    http3Client1.request(path)
    http3Client1.stop()
    println()

    println("Use http(quic)-tcp download...")
    val quicClient = QuicFileClient("localhost", 9999)
    quicClient.request(path)*/
}
