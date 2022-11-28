package quic

import NullOutputStream
import Throughput
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ChannelInputShutdownReadComplete
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicClientCodecBuilder
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.incubator.codec.quic.QuicStreamType
import io.netty.util.ReferenceCountUtil
import processLine
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class QuicFileClient(val host: String, val port: Int) {
    fun request(path: String) {
        val context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols("http/1.1").build()
        val group = NioEventLoopGroup(1)
        val output = NullOutputStream()
        var count = 0L
        try {
            val codec = QuicClientCodecBuilder()
                .sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000) // As we don't want to support remote initiated streams just setup the limit for local initiated
                // streams in this example.
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build()
            val bs = Bootstrap()
            val channel = bs.group(group)
                .channel(NioDatagramChannel::class.java)
                .handler(codec)
                .bind(0).sync().channel()
            val quicChannel = QuicChannel.newBootstrap(channel)
                .streamHandler(object : ChannelInboundHandlerAdapter() {
                    override fun channelActive(ctx: ChannelHandlerContext) {
                        ctx.close()
                    }
                })
                .remoteAddress(InetSocketAddress(host, port))
                .connect()
                .get()
            val streamChannel = quicChannel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                object : ChannelInitializer<QuicStreamChannel>() {
                    override fun initChannel(ch: QuicStreamChannel) {
                        ch.pipeline().addLast(HttpClientCodec())
                        ch.pipeline().addLast(
                            object : ChannelInboundHandlerAdapter() {
                                override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                                    if (msg is HttpResponse) {
                                        count = msg.headers()[HttpHeaderNames.CONTENT_LENGTH].toString().toLong()
                                        msg.headers().forEach {
                                            println("${it.key}:${it.value}")
                                        }
                                    }
                                    if (msg is HttpContent) {
                                        val buf = msg.content()
                                        while (buf.isReadable) {
                                            buf.readBytes(output, buf.readableBytes())
                                            val line = processLine(output.size, count)
                                            print("$line\r")
                                        }
                                        if (count == output.size) {
                                            println()
                                        }
                                        ReferenceCountUtil.release(buf)
                                    }
                                }

                                override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
                                    if (evt === ChannelInputShutdownReadComplete.INSTANCE) {
                                        // Close the connection once the remote peer did send the FIN for this stream.
                                        (ctx.channel().parent() as QuicChannel).close(
                                            true,
                                            0,
                                            ctx.alloc().directBuffer(16)
                                                .writeBytes(
                                                    byteArrayOf(
                                                        'k'.code.toByte(),
                                                        't'.code.toByte(),
                                                        'h'.code.toByte(),
                                                        'x'.code.toByte(),
                                                        'b'.code.toByte(),
                                                        'y'.code.toByte(),
                                                        'e'.code.toByte()
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

            ).sync().now
            val start = System.nanoTime()

            val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, URI("/$path").toASCIIString())
            request.headers().add("Host", host)
            request.headers().add("Connection", "close")
            // Write the data and send the FIN. After this its not possible anymore to write any more data.
            streamChannel.writeAndFlush(request)
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)

            // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
            // After this is done we will close the underlying datagram channel.
            streamChannel.closeFuture().sync()
            quicChannel.closeFuture().sync()
            channel.close().sync()
            val end = System.nanoTime()
            val throughput = Throughput(output.size, end - start)
            println(throughput)
        } finally {
            group.shutdownGracefully()
        }
    }
}
