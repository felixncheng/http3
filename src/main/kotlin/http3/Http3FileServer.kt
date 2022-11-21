package http3

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.incubator.codec.http3.Http3
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class Http3FileServer(val port: Int) {
    private var serverChannel: Channel? = null

    fun start() {
        // Allow to pass in the port so we can also use it to run h3spec against
        val group = NioEventLoopGroup(1)

        val cert = SelfSignedCertificate()
        val sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
            .applicationProtocols(*Http3.supportedApplicationProtocols()).build()
        val codec: ChannelHandler = Http3.newQuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
            .initialMaxData(10000000)
            .initialMaxStreamDataBidirectionalLocal(1000000)
            .initialMaxStreamDataBidirectionalRemote(1000000)
            .initialMaxStreamsBidirectional(100)
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            .maxRecvUdpPayloadSize(64 * 1024)
            .maxSendUdpPayloadSize(64 * 1024)
            .handler(object : ChannelInitializer<QuicChannel>() {
                override fun initChannel(ch: QuicChannel) {
                    // Called for each connection
                    ch.pipeline().addLast(
                        Http3ServerConnectionHandler(
                            object : ChannelInitializer<QuicStreamChannel>() {
                                // Called for each request-stream,
                                override fun initChannel(ch: QuicStreamChannel) {
                                    ch.pipeline().addLast(Http3ServerInitializer())
                                }
                            }
                        )
                    )
                }
            }).build()
        val bs = Bootstrap()
        val channel = bs.group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(codec)
            .bind(InetSocketAddress(port)).sync().channel()
        println("Http3 server started, listening on $port")
        channel.closeFuture().addListener { group.shutdownGracefully() }
        serverChannel = channel
    }

    fun stop() {
        serverChannel?.close()
    }
}
