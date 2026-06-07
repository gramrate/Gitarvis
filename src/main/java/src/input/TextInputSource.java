package src.input;

import java.io.InputStream;
import java.util.Optional;
import java.util.Scanner;

public final class TextInputSource implements InputSource {
    private final Scanner scanner;

    public TextInputSource(InputStream inputStream) {
        this.scanner = new Scanner(inputStream);
    }

    @Override
    public Optional<String> read() {
        System.out.print("> ");
        if (!scanner.hasNextLine()) {
            return Optional.empty();
        }
        return Optional.of(scanner.nextLine());
    }
}
