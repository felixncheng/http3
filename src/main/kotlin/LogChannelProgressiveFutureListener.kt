import io.netty.channel.ChannelProgressiveFuture
import io.netty.channel.ChannelProgressiveFutureListener

class LogChannelProgressiveFutureListener : ChannelProgressiveFutureListener {
    val start = System.currentTimeMillis()
    var last = System.currentTimeMillis()
    var lastProgress = 0L
    override fun operationComplete(future: ChannelProgressiveFuture) {
        println("${future.channel()} Transfer complete, send $lastProgress took${System.currentTimeMillis() - start} ms.")
    }

    override fun operationProgressed(future: ChannelProgressiveFuture, progress: Long, total: Long) {
        val current = System.currentTimeMillis()
        val took = current - last
        last = current
        val send = progress - lastProgress
        lastProgress = progress
        if (total < 0) {
            println("${future.channel()} Transfer progress: $progress")
        } else {
            println("${future.channel()} Transfer progress: $progress/$total,send $send took$took ms")
        }
    }
}
