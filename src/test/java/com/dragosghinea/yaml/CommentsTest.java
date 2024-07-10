package com.dragosghinea.yaml;

import com.dragosghinea.yaml.annotations.Comments;
import exceptions.ConfigTempFileIssue;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CommentsTest {

    @Getter
    public static class Test1 extends ConfigValues{
        @Comments({"Inner comment", "", "Seems to work"})
        private String merge = "ceva";
    }

    @Getter
    public static class CommentsConfig extends ConfigValues {
        @Comments({"This is a comment", "This is another comment"})
        private String test = "ceva";

        private String test2 = "altceva";

        @Comments({"This is a comment", "This is another comment"})
        private String test3 = "ultimul";

        private Test1 test4 = new Test1();

        private int cc = 1;
    }

    @AfterEach
    public void tearDown() {
//        Paths.get("test.yml").toFile().delete();
    }

    @Test
    @DisplayName("Create a config file with comments")
    public void testComments() throws IOException, ConfigTempFileIssue {
        Path path = Paths.get("test.yml");
        ConfigHandler<CommentsConfig> configHandler = new ConfigHandler<>(CommentsConfig.class, path);
        CommentsConfig testValues = configHandler.load();

        configHandler.testingComments(testValues);
    }
}
