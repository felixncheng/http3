import java.io.InputStream

class ZeroInputStream(val size: Long) : InputStream() {
    var read = 0

    override fun read(): Int {
        if (read >= size) {
            return -1
        }
        read++
        return 0
    }
}
