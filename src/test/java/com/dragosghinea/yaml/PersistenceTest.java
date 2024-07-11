package com.dragosghinea.yaml;

import com.dragosghinea.yaml.exceptions.ConfigTempFileIssue;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PersistenceTest {

    private static final String TEST_FILE_CONTENT = "test: \"something\"\n" +
            "test2: 2\n" +
            "test3: true";

    @Getter
    public static class Config extends ConfigValues{
        String test = "something";
        int test2 = 2;
        boolean test3 = true;
    }

    @AfterEach
    public void tearDown() {
        Paths.get("test.yml").toFile().delete();
    }

    @Test
    @DisplayName("Deleted field is regenerated")
    public void testDeletedField() throws IOException, ConfigTempFileIssue {
        Path path = Paths.get("test.yml");

        ConfigHandler<Config> configHandler = new ConfigHandler<>(Config.class, path);
        configHandler.load();

        List<String> lines = Files.readAllLines(path);
        lines.removeIf(line -> line.contains("test2"));
        Files.write(path, lines);

        Config config = configHandler.load();
        assertEquals(2, config.test2);
        configHandler.save(config);
        assertEquals(TEST_FILE_CONTENT, String.join("\n", Files.readAllLines(path)));
    }

    @Test
    @DisplayName("Ignore extra fields")
    public void testIgnoreExtraFields() throws IOException, ConfigTempFileIssue {
        Path path = Paths.get("test.yml");

        ConfigHandler<Config> configHandler = new ConfigHandler<>(Config.class, path);
        configHandler.load();

        List<String> lines = Files.readAllLines(path);
        lines.add("extra: \"field\"");
        lines.add(1, "extra2: 2");
        Files.write(path, lines);

        Config config = configHandler.load();
        assertEquals("something", config.test);
        assertEquals(2, config.test2);
        assertTrue(config.test3);
        configHandler.save(config);
        assertEquals(TEST_FILE_CONTENT, String.join("\n", Files.readAllLines(path)));
    }
}
