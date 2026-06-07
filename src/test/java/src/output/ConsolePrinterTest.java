package src.output;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConsolePrinterTest {
    @Test
    void writesLineToPrintStream() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ConsolePrinter printer = new ConsolePrinter(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        printer.write("hello");

        assertEquals("hello" + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
    }
}
