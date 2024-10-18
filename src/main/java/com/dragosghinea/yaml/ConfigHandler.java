package com.dragosghinea.yaml;

import com.dragosghinea.yaml.annotations.Comments;
import com.dragosghinea.yaml.annotations.OnCreationValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.dragosghinea.yaml.exceptions.ConfigTempFileIssue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConfigHandler<T extends ConfigValues> {

    @Getter
    @AllArgsConstructor
    private static class CommentsMetadata {
        private final String[] comments;
        private final int indentation;
    }

    private final Class<T> configClass;

    private final ObjectMapper objectMapper = new ObjectMapper(
            new YAMLFactory()
                    .configure(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true)
                    .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                    .disable(YAMLGenerator.Feature.SPLIT_LINES)
    );

    {
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Getter
    private final Path path;

    public ConfigHandler(Class<T> classOfTheParameter, Path path) {
        this.path = path;
        this.configClass = classOfTheParameter;
    }

    @SneakyThrows
    public T load() {
        Constructor<T> constructor = configClass.getConstructor();
        constructor.setAccessible(true);
        return load(() -> {
            try {
                return constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public T load(Supplier<T> onCreationInitializer) throws IOException, ConfigTempFileIssue {
        if (!path.toFile().exists()) {
            T config = onCreationInitializer.get();
            applyOnCreationValues(config);
            save(config);
            return config;
        }

        return objectMapper.readValue(path.toFile(), configClass);
    }

    public void save(T config) throws IOException, ConfigTempFileIssue {
        if (!path.toFile().exists()) {
            try {
                if (path.getParent() != null && !path.getParent().toFile().exists()) {
                    path.getParent().toFile().mkdirs();
                }

                path.toFile().createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        objectMapper.writeValue(path.toFile(), config);
        Map<String, String[]> comments = computeComments(config);
        applyComments(path, comments);
    }

    private void applyOnCreationValues(ConfigValues config) {
        streamFields(config)
                .filter(field -> field.isAnnotationPresent(OnCreationValue.class))
                .forEach(field -> {
                    try {
                        field.setAccessible(true);

                        OnCreationValue annotation = field.getAnnotation(OnCreationValue.class);
                        field.set(config, objectMapper.readValue(annotation.value(), field.getType()));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    } finally {
                        field.setAccessible(false);
                    }
                });

        streamFields(config)
                .forEach(field -> {
                    if (!ConfigValues.class.isAssignableFrom(field.getType()))
                        return;

                    Object configValuesObjectToUse = getFieldValue(field, config);
                    if (configValuesObjectToUse == null)
                        return;

                    applyOnCreationValues((ConfigValues) configValuesObjectToUse);
                });
    }

    private Map<String, String[]> computeComments(T config) {
        Map<String, String[]> comments = new HashMap<>();

        computeInnerComments("", config, null, comments);

        return comments;
    }

    private void computeInnerComments(String key, ConfigValues config, Field field, Map<String, String[]> comments) {
        if (field != null && !ConfigValues.class.isAssignableFrom(field.getType()))
            return;

        Object configValuesObjectToUse = field == null ? config : getFieldValue(field, config);
        if (configValuesObjectToUse == null)
            return;

        streamFields((ConfigValues) configValuesObjectToUse)
                .forEach(innerField -> {
                    String fieldName = getFieldName(innerField);
                    String innerKey = key.isEmpty() ? fieldName : key + "." + fieldName;
                    computeInnerComments(innerKey, (ConfigValues) configValuesObjectToUse, innerField, comments);
                });

        streamFields((ConfigValues) configValuesObjectToUse)
                .filter(innerField -> innerField.isAnnotationPresent(Comments.class))
                .forEach(innerField -> {
                    Comments annotation = innerField.getAnnotation(Comments.class);

                    String fieldName = getFieldName(innerField);
                    comments.put(key.isEmpty() ? fieldName : key + "." + fieldName, annotation.value());
                });
    }

    private String getFieldName(Field field) {
        if (field.isAnnotationPresent(JsonProperty.class)) {
            return field.getAnnotation(JsonProperty.class).value();
        }

        return field.getName();
    }

    // try to get the value directly, then try to get it through a getter, then try to get it forcefully
    private Object getFieldValue(Field field, ConfigValues config) {
        try {
            return field.get(config);
        } catch (IllegalAccessException e) {
            try {
                Method getter = config.getClass().getDeclaredMethod("get" + field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1));
                return getter.invoke(config);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
                try {
                    field.setAccessible(true);
                    return field.get(config);
                } catch (IllegalAccessException exc) {
                    throw new RuntimeException(exc);
                } finally {
                    field.setAccessible(false);
                }
            }
        }
    }

    private void applyComments(Path configPath, Map<String, String[]> comments) throws IOException, ConfigTempFileIssue {
        Map<Integer, CommentsMetadata> commentsMetadataPerLine = getCommentsAndIndentationMaps(configPath, comments);

        String originalFileName = configPath.getFileName().toString();

        int dotIndex = originalFileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? originalFileName : originalFileName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : originalFileName.substring(dotIndex);

        String newFileName = baseName + "_before_comments" + extension;

        Path beforeCommentsPath = configPath.resolveSibling(newFileName);
        if (beforeCommentsPath.toFile().exists() && !beforeCommentsPath.toFile().delete()) {
            throw new ConfigTempFileIssue(beforeCommentsPath.toFile(), "Could not delete the temporary file after comments were added.");
        }

        if (!configPath.toFile().renameTo(beforeCommentsPath.toFile())) {
            throw new ConfigTempFileIssue(configPath.toFile(), "Could not rename the original file to the temporary file before comments were added.");
        }

        try (
                FileInputStream fis = new FileInputStream(beforeCommentsPath.toFile());
                BufferedReader in = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));

                FileOutputStream fos = new FileOutputStream(configPath.toFile());
                PrintWriter out = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))
        ) {

            String thisLine;
            int lineCounter = 1; // lines are 1-indexed
            while ((thisLine = in.readLine()) != null) {
                CommentsMetadata commentsMetadata = commentsMetadataPerLine.getOrDefault(lineCounter, null);

                if (commentsMetadata != null) {
                    String indentation = Stream.generate(() -> " ")
                            .limit(commentsMetadata.getIndentation())
                            .collect(Collectors.joining());

                    for (String comment : commentsMetadata.getComments()) {
                        if (comment.isEmpty()) {
                            out.println();
                            continue;
                        }

                        out.println(indentation + "# " + comment);
                    }
                }

                out.println(thisLine);
                lineCounter++;
            }

            out.flush();
        }

        if (!beforeCommentsPath.toFile().delete()) {
            throw new ConfigTempFileIssue(beforeCommentsPath.toFile(), "Could not delete the temporary file after comments were added.");
        }
    }

    private Map<Integer, CommentsMetadata> getCommentsAndIndentationMaps(Path configPath, Map<String, String[]> comments) {
        YAMLFactory yamlFactory = new YAMLFactory();
        Stack<String> keyBuilder = new Stack<>();
        Map<Integer, CommentsMetadata> commentsMetadataPerLine = new HashMap<>();

        try (YAMLParser parser = yamlFactory.createParser(configPath.toFile())) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    // is the first start object
                    if (keyBuilder.isEmpty())
                        continue;

                    keyBuilder.add(".");
                } else if (parser.currentToken() == JsonToken.END_OBJECT) {
                    // is the final end object
                    if (keyBuilder.size() <= 1) {
                        keyBuilder.clear();
                        continue;
                    }

                    // clean up field name
                    if (!keyBuilder.peek().equals("."))
                        keyBuilder.pop();
                    keyBuilder.pop(); // the dot
                    keyBuilder.pop(); // the field name for the object that is being closed
                } else if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    // clean up previous sibling field name, if it exists
                    if (!keyBuilder.isEmpty() && !keyBuilder.peek().equals("."))
                        keyBuilder.pop();

                    keyBuilder.add(parser.getText());

                    String key = String.join("", keyBuilder);
                    if (comments.containsKey(key)) {
                        int indentOffset = parser.currentLocation().getColumnNr() - parser.getTextLength() - 1;

                        commentsMetadataPerLine.put(parser.currentLocation().getLineNr(), new CommentsMetadata(comments.get(key), indentOffset));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return commentsMetadataPerLine;
    }

    private Stream<Field> streamFields(ConfigValues config) {
        Class<?> classOrSuperClass = config.getClass();

        Stream<Field> stream = Stream.empty();
        while (classOrSuperClass != ConfigValues.class) {
            stream = Stream.concat(stream, Stream.of(classOrSuperClass.getDeclaredFields()));
            classOrSuperClass = classOrSuperClass.getSuperclass();
        }

        return stream;
    }

}
