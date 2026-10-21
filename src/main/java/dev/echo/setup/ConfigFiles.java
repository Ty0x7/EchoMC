package dev.echo.setup;

import dev.echo.EchoConfig;
import java.nio.file.*;
import java.io.IOException;

/** Replace only after the full new configuration has validated and serialized. */
public final class ConfigFiles {
    public static void write(Path path, Object config) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path pending=Files.createTempFile(path.toAbsolutePath().getParent(),"echo-config-",".tmp");
        try {
            Files.writeString(pending,EchoConfig.JSON.toJson(config));
            try { Files.move(pending,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ex) { Files.move(pending,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(pending); }
    }
    public static String text(String value,String label,int max) {
        if(value==null || value.isBlank() || value.length()>max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label+" is required (maximum "+max+" characters).");
        return value.strip();
    }
    public static String keyName(String value) {
        if(value==null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
            throw new IllegalArgumentException("Enter an environment variable name, not an API key.");
        return value;
    }
}
