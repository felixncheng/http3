import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

const val HTTP_PORT = 9997
const val HTTPS_PORT = 9998
const val HTTP3_PORT = 9999

const val BIG_FILE_REQUEST_PATH = "bigFile"
const val SMALL_FILE_REQUEST_PATH = "smallFile"
val BIG_FILE_PATH: Path = Paths.get("big.data")
val SMALL_FILE_PATH: Path = Paths.get("small.data")

fun createFile(path: Path, size: Long): File {
    val output = Files.newOutputStream(path)
    ZeroInputStream(size).copyTo(output)
    return path.toFile().apply {
        println("Create file[${this.absolutePath}],size:${this.length()}")
    }
}

fun clean() {
    println("Clean files")
    Files.deleteIfExists(BIG_FILE_PATH)
    Files.deleteIfExists(SMALL_FILE_PATH)
}
fun processLine(process: Long, total: Long): String {
    val percent = process * 100 / total.toDouble()
    val strBuilder = StringBuilder("【")
    for (i in 0 until 100) {
        if (i < percent) {
            strBuilder.append(">")
        } else {
            strBuilder.append(" ")
        }
    }
    strBuilder.append("】$process/$total ${String.format("%.2f",percent) }%")
    return strBuilder.toString()
}
