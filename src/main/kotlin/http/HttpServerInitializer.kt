package http

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.ssl.SslContext
import io.netty.handler.stream.ChunkedWriteHandler

class HttpServerInitializer(val sslCtx: SslContext?) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        if (sslCtx != null) {
            pipeline.addLast(sslCtx.newHandler(ch.alloc()))
        }
        pipeline.addLast(HttpServerCodec())
        pipeline.addLast(HttpObjectAggregator(65536))
        pipeline.addLast(ChunkedWriteHandler())
        pipeline.addLast(HttpFileServerHandler())
    }
}
