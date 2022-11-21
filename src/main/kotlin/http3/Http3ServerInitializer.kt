package http3

import io.netty.channel.ChannelInitializer
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.incubator.codec.quic.QuicStreamChannel

class Http3ServerInitializer : ChannelInitializer<QuicStreamChannel>() {
    override fun initChannel(ch: QuicStreamChannel) {
        val pipeline = ch.pipeline()
        pipeline.addLast(ByteToHttp3FrameHandler())
        pipeline.addLast(ChunkedWriteHandler())
        pipeline.addLast(Http3RequestHandler())
    }
}
