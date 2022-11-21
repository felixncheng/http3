import java.io.OutputStream

class NullOutputStream : OutputStream() {
    var size: Long = 0
    override fun write(b: Int) {
        size++
    }
}
