package http3

import BIG_FILE_PATH
import BIG_FILE_REQUEST_PATH
import LogChannelProgressiveFutureListener
import SMALL_FILE_PATH
import SMALL_FILE_REQUEST_PATH
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.stream.ChunkedFile
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame
import io.netty.incubator.codec.http3.Http3DataFrame
import io.netty.incubator.codec.http3.Http3HeadersFrame
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler
import io.netty.incubator.codec.quic.QuicStreamChannel
import io.netty.util.ReferenceCountUtil
import java.io.File

class Http3RequestHandler : Http3RequestStreamInboundHandler() {

    private var isBigFile = false
    override fun channelRead(ctx: ChannelHandlerContext, frame: Http3HeadersFrame, isLast: Boolean) {
        println("Accept header frame from ${ctx.channel().remoteAddress()}, $frame")
        frame.headers().forEach {
            println("${it.key}: ${it.value}")
        }
        val path = frame.headers().path().removePrefix("/").toString()
        isBigFile = when (path) {
            BIG_FILE_REQUEST_PATH -> true
            SMALL_FILE_REQUEST_PATH -> false
            else -> {
                val headersFrame: Http3HeadersFrame = DefaultHttp3HeadersFrame()
                headersFrame.headers().status("404")
                headersFrame.headers().add("server", "netty")
                ctx.writeAndFlush(headersFrame)
                ReferenceCountUtil.release(frame)
                return
            }
        }
        if (isLast) {
            println("Accept last header frame from ${ctx.channel().remoteAddress()}")
            service(ctx)
        }
        ReferenceCountUtil.release(frame)
    }

    override fun channelRead(ctx: ChannelHandlerContext, frame: Http3DataFrame, isLast: Boolean) {
        println("Accept data frame from ${ctx.channel().remoteAddress()}")
        if (isLast) {
            println("Accept last data frame from ${ctx.channel().remoteAddress()}")
            service(ctx)
        }
        ReferenceCountUtil.release(frame)
    }

    private fun service(ctx: ChannelHandlerContext) {
        if (isBigFile) {
            downloadBigFile(ctx)
        } else {
            downloadSmallFile(ctx)
        }
    }

    private fun downloadBigFile(ctx: ChannelHandlerContext) {
        writeResponse(ctx, BIG_FILE_PATH.toFile())
    }

    private fun downloadSmallFile(ctx: ChannelHandlerContext) {
        writeResponse(ctx, SMALL_FILE_PATH.toFile())
    }

    private fun writeResponse(ctx: ChannelHandlerContext, file: File) {
        val headersFrame: Http3HeadersFrame = DefaultHttp3HeadersFrame()
        headersFrame.headers().status("200")
        headersFrame.headers().add("server", "netty")
        headersFrame.headers().addLong(HttpHeaderNames.CONTENT_LENGTH, file.length())
        ctx.writeAndFlush(headersFrame)
        val sendFileFuture = ctx
            .writeAndFlush(ChunkedFile(file, 64 * 1024), ctx.newProgressivePromise())
            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
        sendFileFuture.addListener(LogChannelProgressiveFutureListener())
    }
}
