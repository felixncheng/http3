package http3

import NullOutputStream
import Throughput
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame
import io.netty.incubator.codec.http3.Http3
import io.netty.incubator.codec.http3.Http3ClientConnectionHandler
import io.netty.incubator.codec.http3.Http3DataFrame
import io.netty.incubator.codec.http3.Http3HeadersFrame
import io.netty.incubator.codec.http3.Http3RequestStreamFrame
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicSslContext
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.util.ReferenceCountUtil
import processLine
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class Http3Client(val host: String, val port: Int) {
    private val group = NioEventLoopGroup(1)
    private val context: QuicSslContext = QuicSslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .applicationProtocols(*Http3.supportedApplicationProtocols()).build()
    private val codec: ChannelHandler = Http3.newQuicClientCodecBuilder()
        // draft-29,有些服务端需要校验版本号，这里可以指定quic version
//        .version(-16777187)
        .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
        .initialMaxData(10000000)
        .maxRecvUdpPayloadSize((64 * 1024).toLong())
        .maxSendUdpPayloadSize((64 * 1024).toLong())
        .initialMaxStreamDataBidirectionalLocal(1000000)
        .sslContext(context)
        // clb必须带上sni
//        .sslEngineProvider { context.newEngine(it.alloc(), host, port) }
        .build()
    private val bs = Bootstrap()
    private val channel: Channel = bs.group(group)
        .channel(NioDatagramChannel::class.java)
        .handler(codec)
        .bind(0).sync().channel()
    private val quicChannel: QuicChannel = QuicChannel.newBootstrap(channel)
        .handler(Http3ClientConnectionHandler())
        .remoteAddress(InetSocketAddress(host, port))
        .connect()
        .get()
    fun request(path: String) {
        val output = NullOutputStream()
        var count = 0L
        val streamChannel = Http3.newRequestStream(
            quicChannel,
            object : Http3RequestStreamInboundHandler() {
                override fun channelRead(
                    ctx: ChannelHandlerContext,
                    frame: Http3HeadersFrame,
                    isLast: Boolean
                ) {
                    frame.headers().forEach {
                        println("${it.key}:${it.value}")
                    }
                    count = frame.headers().get(HttpHeaderNames.CONTENT_LENGTH).toString().toLong()
                    releaseFrameAndCloseIfLast(ctx, frame, isLast)
                }

                @Throws(IOException::class)
                override fun channelRead(
                    ctx: ChannelHandlerContext,
                    frame: Http3DataFrame,
                    isLast: Boolean
                ) {
                    val buf = frame.content()
                    while (buf.isReadable) {
                        val readableBytes = buf.readableBytes()
                        buf.readBytes(output, readableBytes)
                        val line = processLine(output.size, count)
                        print("$line\r")
                    }
                    if (count == output.size) {
                        println()
                    }
                    releaseFrameAndCloseIfLast(ctx, frame, isLast)
                }

                private fun releaseFrameAndCloseIfLast(
                    ctx: ChannelHandlerContext,
                    frame: Http3RequestStreamFrame,
                    isLast: Boolean
                ) {
                    ReferenceCountUtil.release(frame)
                    if (isLast) {
                        ctx.close()
                    }
                }
            }
        ).sync().now
        val start = System.nanoTime()
        // Write the Header frame and send the FIN to mark the end of the request.
        // After this its not possible anymore to write any more data.
        val frame: Http3HeadersFrame = DefaultHttp3HeadersFrame()
        frame.headers().method("GET").path("/$path")
            .authority("$host:$port")
            .scheme("https")
        streamChannel.writeAndFlush(frame)
            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync()

        // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
        // After this is done we will close the underlying datagram channel.
        streamChannel.closeFuture().sync()

        // After we received the response lets also close the underlying QUIC channel and datagram channel.
        quicChannel.close().sync()
        channel.close().sync()
        val end = System.nanoTime()
        val throughput = Throughput(output.size, end - start)
        println(throughput)
    }
    fun stop() {
        group.shutdownGracefully()
    }
}
