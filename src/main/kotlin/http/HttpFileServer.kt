package http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate

class HttpFileServer(val ssl: Boolean, val port: Int) {
    var serverChannel: Channel? = null
    fun start() {
        val cert = SelfSignedCertificate()
        val sslCtx = if (ssl) {
            SslContextBuilder
                .forServer(cert.key(), cert.cert())
                .build()
        } else null
        val bossGroup: EventLoopGroup = NioEventLoopGroup(1)
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        val b = ServerBootstrap()
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(HttpServerInitializer(sslCtx))
        val ch: Channel = b.bind(port).sync().channel()
        println("Http${if (ssl) "s" else ""} server started, listening on $port")
        ch.closeFuture().addListener {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
        serverChannel = ch
    }

    fun stop() {
        serverChannel?.close()
    }
}
