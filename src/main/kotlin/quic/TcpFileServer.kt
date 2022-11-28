package quic

import BIG_FILE_PATH
import HumanReadable
import LogChannelProgressiveFutureListener
import SMALL_FILE_PATH
import SMALL_FILE_REQUEST_PATH
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.DefaultFileRegion
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LineBasedFrameDecoder
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.CharsetUtil
import java.io.RandomAccessFile

class TcpFileServer(val port: Int) {

    var serverChannel: Channel? = null
    fun start() {
        val bossGroup: EventLoopGroup = NioEventLoopGroup(1)
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        val b = ServerBootstrap()
        b.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    ch.pipeline().addLast(LoggingHandler(LogLevel.INFO)).addLast(LineBasedFrameDecoder(1024))
                        .addLast(object : ChannelInboundHandlerAdapter() {
                            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                                val byteBuf = msg as ByteBuf
                                try {
                                    val file = if (byteBuf.toString(CharsetUtil.US_ASCII).trim() == SMALL_FILE_REQUEST_PATH) {
                                        SMALL_FILE_PATH.toFile()
                                    } else {
                                        BIG_FILE_PATH.toFile()
                                    }
                                    val raf = RandomAccessFile(file, "r")
                                    val length = file.length()
                                    println("length $${HumanReadable.size(length)}")
                                    ctx.writeAndFlush("length: $length")
                                    val sendFileFuture = ctx.writeAndFlush(
                                        DefaultFileRegion(raf.channel, 0, length),
                                        ctx.newProgressivePromise()
                                    )
                                    sendFileFuture.addListener(LogChannelProgressiveFutureListener())
                                        .addListeners(ChannelFutureListener.CLOSE)
                                } finally {
                                    byteBuf.release()
                                }
                            }
                        })
                }
            })
        val ch: Channel = b.bind(port).sync().channel()
        println("Tcp server started, listening on $port")
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
