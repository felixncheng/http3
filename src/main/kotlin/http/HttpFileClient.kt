package http

import NullOutputStream
import Throughput
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.ReferenceCountUtil
import processLine
import java.net.URI

class HttpFileClient(val ssl: Boolean, val host: String, val port: Int) {

    val sslCtx: SslContext = SslContextBuilder
        .forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
        .build()
    private val workerGroup = NioEventLoopGroup()
    private val bootstrap = Bootstrap()

    fun request(path: String) {
        val output = NullOutputStream()
        var count = 0L
        bootstrap.group(workerGroup)
            .channel(NioSocketChannel::class.java)
            .handler(object : ChannelInitializer<SocketChannel>() {
                @Throws(Exception::class)
                override fun initChannel(sc: SocketChannel) {
                    val pipeline = sc.pipeline()
                    if (ssl) {
                        pipeline.addLast(sslCtx.newHandler(sc.alloc(), host, port))
                    }
                    pipeline.addLast(HttpClientCodec())
                    pipeline.addLast(object : ChannelInboundHandlerAdapter() {
                        @Throws(Exception::class)
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
                    })
                }
            })
        val channel = bootstrap.connect(host, port).sync().channel()
        val request = DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, URI("/$path").toASCIIString())
        request.headers().add("Host", host)
        request.headers().add("Connection", "close")
        val start = System.nanoTime()
        channel.writeAndFlush(request).sync()
        channel.closeFuture().sync()
        val end = System.nanoTime()
        val throughput = Throughput(output.size, end - start)
        println(throughput)
    }

    fun stop() {
        workerGroup.shutdownGracefully()
    }
}
