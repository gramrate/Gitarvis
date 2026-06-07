package src.ai;

import java.io.IOException;

public interface AiGateway {
    String complete(String prompt) throws IOException, InterruptedException;
}
