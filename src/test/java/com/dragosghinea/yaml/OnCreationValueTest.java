package com.dragosghinea.yaml;

import com.dragosghinea.yaml.annotations.OnCreationValue;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OnCreationValueTest {

    @Getter
    public static class TypeCastingValues extends ConfigValues {
        @OnCreationValue("test")
        private String test;

        @OnCreationValue("2")
        private int test2;

        @OnCreationValue("true")
        private boolean test3;

        @OnCreationValue("1.25")
        private double test4;
    }

    @Getter
    public static class WithDefaultValues extends ConfigValues {
        @OnCreationValue("test")
        private String test = "default";

        @OnCreationValue("2")
        private int test2 = 1;

        @OnCreationValue("true")
        private boolean test3 = false;

        @OnCreationValue("1.25")
        private double test4 = 1.0;
    }

    @AfterEach
    public void tearDown() {
        Paths.get("test.yml").toFile().delete();
    }

    @Test
    @DisplayName("No default value")
    public void testOnCreationValue() {
        ConfigHandler<TypeCastingValues> configHandler = new ConfigHandler<>(TypeCastingValues.class, Paths.get("test.yml"));
        TypeCastingValues testValues = configHandler.load();

        assertEquals("test", testValues.test);
        assertEquals(2, testValues.test2);
        assertTrue(testValues.test3);
        assertEquals(1.25, testValues.test4);
    }

    @Test
    @DisplayName("With default value")
    public void testOnCreationValueWithDefault() {
        ConfigHandler<WithDefaultValues> configHandler = new ConfigHandler<>(WithDefaultValues.class, Paths.get("test.yml"));
        WithDefaultValues testValues = configHandler.load();

        assertEquals("test", testValues.test);
        assertEquals(2, testValues.test2);
        assertTrue(testValues.test3);
        assertEquals(1.25, testValues.test4);
    }
}
