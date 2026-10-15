package schedule

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals

class CommandsTest {
    @Test
    fun periodIncludesBothBoundsKeepsFileOrderAndChecksRejectedRowsOutsideFilter(@TempDir dir: Path) {
        val file = input(dir, listOf(
            "2026-10-06|LECTURE|Last|Room|Ivanov",
            "2026-10-04|LECTURE|Outside|Room|Ivanov",
            "2026-10-05|CANCELLED|First|-|-",
            "2030-01-01|EXAM|Invalid outside|Room|Ivanov",
        ))
        assertEquals(
            1 to "2026-10-06 | LECTURE | Last | Room\n2026-10-05 | CANCELLED | First | -\nTotal: 2\n",
            execute("period", "--file", file, "--from", "2026-10-05", "--to", "2026-10-06"),
        )
        assertEquals(1 to "Total: 0\n", execute("type", "--file", file, "--type", "PRACTICE"))
        assertEquals(
            1 to "2026-10-05 | CANCELLED | First | - | -\nTotal: 1\n",
            execute("type", "--file", file, "--type", "CANCELLED"),
        )
    }

    @Test
    fun errorsContinueAfterRejectionsAndCountBlankLines(@TempDir dir: Path) {
        val file = input(dir, listOf("", "2026-10-05|LECTURE|Valid|Room|Ivanov", "bad|SEMINAR|Topic|Room|Ivanov", ""))
        assertEquals(
            1 to "line 1: invalid field separator\nline 3: invalid date \"bad\"\nline 4: invalid field separator\nRejected: 3 of 4\n",
            execute("errors", "--file", file),
        )
    }

    @Test
    fun typesFormatTeacherOrderAndEmptyFilesHaveZeroTotals(@TempDir dir: Path) {
        val file = input(dir, listOf("2026-10-05|EXAM|Exam|Room|Petrov,Ivanov"))
        assertEquals(
            0 to "2026-10-05 | EXAM | Exam | Room | Petrov, Ivanov\nTotal: 1\n",
            execute("type", "--file", file, "--type", "EXAM"),
        )
        Files.writeString(Path.of(file), "")
        assertEquals(0 to "Rejected: 0 of 0\n", execute("errors", "--file", file))
        assertEquals(0 to "Total: 0\n", execute("type", "--file", file, "--type", "EXAM"))
        assertEquals(0 to "Total: 0\n", execute("period", "--file", file, "--from", "0001-01-01", "--to", "9999-12-31"))
    }

    @Test
    fun missingFilePrintsOnlyReadingError(@TempDir dir: Path) {
        assertEquals(2 to "Cannot read file\n", execute("errors", "--file", dir.resolve("missing").toString()))
    }

    @Test
    fun readingFailureAfterPartialOutputDoesNotPrintSuccessfulTotal(@TempDir dir: Path) {
        val file = dir.resolve("damaged.txt")
        val valid = "2026-10-05|LECTURE|Topic|Room|Ivanov\n".repeat(1000).toByteArray(Charsets.UTF_8)
        Files.write(file, valid + byteArrayOf(0xc3.toByte(), 0x28))
        val (code, output) = execute("type", "--file", file.toString(), "--type", "LECTURE")
        assertEquals(2, code)
        kotlin.test.assertTrue(output.startsWith("2026-10-05 | LECTURE | Topic | Room | Ivanov\n"))
        kotlin.test.assertTrue(output.endsWith("Cannot read file\n"))
        kotlin.test.assertFalse(output.contains("Total:"))
    }

    private fun input(dir: Path, lines: List<String>): String {
        val path = dir.resolve("input.txt")
        Files.writeString(path, lines.joinToString("\r\n", postfix = "\r\n"))
        return path.toString()
    }

    private fun execute(vararg args: String): Pair<Int, String> {
        val original = System.out
        val bytes = ByteArrayOutputStream()
        val output = PrintStream(bytes, true, Charsets.UTF_8)
        return try {
            System.setOut(output)
            val code = runApplication(arrayOf(*args))
            code to bytes.toString(Charsets.UTF_8).replace("\r\n", "\n")
        } finally {
            System.setOut(original)
            output.close()
        }
    }
}
