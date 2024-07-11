package com.dragosghinea.yaml;

import com.dragosghinea.yaml.annotations.Comments;
import exceptions.ConfigTempFileIssue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommentsTest {
    private static final String TEST_FILE_CONTENT = "# This is a comment\n" +
            "# This is another comment\n" +
            "test: \"something\"\n" +
            "test2: \"something2\"\n" +
            "# This is a comment\n" +
            "# This is another comment\n" +
            "test3: \"lastly\"\n" +
            "test4:\n" +
            "  # Inner comment\n" +
            "\n" +
            "  # Seems to work\n" +
            "  innerTest: \"innerTestContent\"\n" +
            "\n" +
            "cc: 1";

    private static final String TEST_INDENTATION_FILE_CONTENT = "# This is a comment\n" +
            "# This is another comment\n" +
            "test: \"something\"\n" +
            "testSection:\n" +
            "  # This is a comment\n" +
            "  # This is another comment\n" +
            "  test: \"something\"\n" +
            "  testSection:\n" +
            "    # This is a comment\n" +
            "    # This is another comment\n" +
            "    test: \"something\"\n" +
            "    testSection:\n" +
            "      # This is a comment\n" +
            "      # This is another comment\n" +
            "      test: \"something\"\n" +
            "      testSection:\n" +
            "        # This is a comment\n" +
            "        # This is another comment\n" +
            "        test: \"something\"\n" +
            "        testSection:\n" +
            "          # This is a comment\n" +
            "          # This is another comment\n" +
            "          test: \"something\"";

    @Getter
    public static class InnerSectionConfig extends ConfigValues{
        @Comments({"Inner comment", "", "Seems to work"})
        private String innerTest = "innerTestContent";
    }

    @Getter
    public static class CommentsConfig extends ConfigValues {
        @Comments({"This is a comment", "This is another comment"})
        private String test = "something";

        private String test2 = "something2";

        @Comments({"This is a comment", "This is another comment"})
        private String test3 = "lastly";

        @Comments({})
        private InnerSectionConfig test4 = new InnerSectionConfig();

        @Comments({""})
        private int cc = 1;
    }

    @Getter
    @NoArgsConstructor
    public static class IndentationConfig extends ConfigValues {
        @Comments({"This is a comment", "This is another comment"})
        private String test = "something";

        private IndentationConfig testSection;

        public IndentationConfig(int depth) {
            if (depth == 0)
                return;

            testSection = new IndentationConfig(depth - 1);
        }
    }

    @AfterEach
    public void tearDown() {
        Paths.get("test.yml").toFile().delete();
    }

    @Test
    @DisplayName("Create a config file with comments")
    public void testComments() throws IOException {
        Path path = Paths.get("test.yml");
        ConfigHandler<CommentsConfig> configHandler = new ConfigHandler<>(CommentsConfig.class, path);
        configHandler.load();

        assertEquals(TEST_FILE_CONTENT, String.join("\n", Files.readAllLines(path)));
    }

    @Test
    @DisplayName("Indentation test of comments")
    public void testIndentation() throws IOException, ConfigTempFileIssue {
        Path path = Paths.get("test.yml");
        ConfigHandler<IndentationConfig> configHandler = new ConfigHandler<>(IndentationConfig.class, path);
        configHandler.load(() -> new IndentationConfig(5));

        assertEquals(TEST_INDENTATION_FILE_CONTENT, String.join("\n", Files.readAllLines(path)));
    }
}
