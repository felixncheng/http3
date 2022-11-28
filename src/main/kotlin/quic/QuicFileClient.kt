package quic

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ChannelInputShutdownReadComplete
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.incubator.codec.quic.QuicChannel
import io.netty.incubator.codec.quic.QuicClientCodecBuilder
import io.netty.incubator.codec.quic.QuicSslContextBuilder
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.incubator.codec.quic.QuicStreamType
import io.netty.util.CharsetUtil
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class QuicFileClient(val host: String, val port: Int) {
    fun request(path: String) {
        val context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols("bftp").build()
        val group = NioEventLoopGroup(1)
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
                        // As we did not allow any remote initiated streams we will never see this method called.
                        // That said just let us keep it here to demonstrate that this handle would be called
                        // for each remote initiated stream.
                        ctx.close()
                    }
                })
                .remoteAddress(InetSocketAddress(host, port))
                .connect()
                .get()
            val streamChannel = quicChannel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                object : ChannelInboundHandlerAdapter() {
                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        val byteBuf = msg as ByteBuf
                        System.err.println(byteBuf.toString(CharsetUtil.US_ASCII))
                        byteBuf.release()
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
            ).sync().now
            // Write the data and send the FIN. After this its not possible anymore to write any more data.
            streamChannel.writeAndFlush(Unpooled.copiedBuffer(path, CharsetUtil.US_ASCII))
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)

            // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
            // After this is done we will close the underlying datagram channel.
            streamChannel.closeFuture().sync()
            quicChannel.closeFuture().sync()
            channel.close().sync()
        } finally {
            group.shutdownGracefully()
        }
    }
}
