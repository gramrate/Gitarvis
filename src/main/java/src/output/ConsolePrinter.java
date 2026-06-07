package src.output;

import java.io.PrintStream;

public final class ConsolePrinter implements OutputSink {
    private final PrintStream printStream;

    public ConsolePrinter(PrintStream printStream) {
        this.printStream = printStream;
    }

    @Override
    public void write(String text) {
        printStream.println(text);
    }
}
