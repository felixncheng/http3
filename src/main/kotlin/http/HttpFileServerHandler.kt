package http

import BIG_FILE_PATH
import BIG_FILE_REQUEST_PATH
import LogChannelProgressiveFutureListener
import SMALL_FILE_PATH
import SMALL_FILE_REQUEST_PATH
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.DefaultFileRegion
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpChunkedInput
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.ssl.SslHandler
import io.netty.handler.stream.ChunkedFile
import java.io.File
import java.io.RandomAccessFile

class HttpFileServerHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
    private var request: FullHttpRequest? = null
    override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
        this.request = request
        when (request.uri().removePrefix("/")) {
            BIG_FILE_REQUEST_PATH -> writeResponse(ctx, BIG_FILE_PATH.toFile())
            SMALL_FILE_REQUEST_PATH -> writeResponse(ctx, SMALL_FILE_PATH.toFile())
            else -> {
                val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
                ctx.writeAndFlush(response)
                return
            }
        }
    }

    private fun writeResponse(ctx: ChannelHandlerContext, file: File) {
        val raf = RandomAccessFile(file, "r")
        val fileLength = raf.length()
        val response: HttpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        HttpUtil.setContentLength(response, fileLength)
        // Write the initial line and the header.
        ctx.write(response)
        // Write the content.
        val sendFileFuture: ChannelFuture = if (ctx.pipeline().get(SslHandler::class.java) == null) {
            ctx.writeAndFlush(
                DefaultFileRegion(raf.channel, 0, fileLength),
                ctx.newProgressivePromise()
            )
        } else {
            ctx.writeAndFlush(
                HttpChunkedInput(ChunkedFile(raf, 0, fileLength, 8192)),
                ctx.newProgressivePromise()
            )
        }
        sendFileFuture.addListener(LogChannelProgressiveFutureListener())
            .addListeners(ChannelFutureListener.CLOSE)
    }
}
