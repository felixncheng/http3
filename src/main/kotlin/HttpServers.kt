import http.HttpFileServer
import http3.Http3FileServer
import quic.TcpFileServer
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    println("Program arguments: ${args.joinToString()}")
    createFile(BIG_FILE_PATH, 1024 * 1024 * 1024L)
    createFile(SMALL_FILE_PATH, 1024)
    val tcpServer = TcpFileServer(TCP_PORT)
    val http3Server = Http3FileServer(HTTP3_PORT)
    val httpsServer = HttpFileServer(true, HTTPS_PORT)
    val httpServer = HttpFileServer(false, HTTP_PORT)
    Runtime.getRuntime().addShutdownHook(
        thread(start = false) {
            httpServer.stop()
            httpsServer.stop()
            http3Server.stop()
            tcpServer.stop()
            clean()
        }
    )
    // 启动http,https,http3 server
/*    httpServer.start()
    httpsServer.start()
    http3Server.start()*/
    tcpServer.start()
}
