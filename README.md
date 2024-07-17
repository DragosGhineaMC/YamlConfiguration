# Intro

Mapping a yaml configuration to java objects and back to yaml configuration.

The project was created with minecraft plugin configs in mind, but it might not be limited to only that.

```java
// Java Config Example, imbricated

@Getter
public class InnerSectionConfig extends ConfigValues{
    @Comments({"Inner comment", "", "Seems to work"})
    private String innerTest = "innerTestContent";
}

@Getter
public class CommentsConfig extends ConfigValues {
    @Comments({"This is a comment", "This is another comment"})
    private String test = "something";

    private String test2 = "something2";

    @Comments({"This is a comment", "This is another comment"})
    private String test3 = "lastly";

    private InnerSectionConfig test4 = new InnerSectionConfig();

    @Comments({""})
    private int cc = 1;
}
```

```yaml
# Generated from Config.java
----
# This is a comment
# This is another comment
test: "something"
test2: "something2"
# This is a comment
# This is another comment
test3: "lastly"
test4:
  # Inner comment

  # Seems to work
  innerTest: "innerTestContent"

cc: 1
```

It also allows for recursive definitions

```java
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
```
Will output for `configHandler.load(() -> new IndentationConfig(5))` the following yaml configuration.

```yaml
# This is a comment
# This is another comment
test: "something"
testSection:
  # This is a comment
  # This is another comment
  test: "something"
  testSection:
    # This is a comment
    # This is another comment
    test: "something"
    testSection:
      # This is a comment
      # This is another comment
      test: "something"
      testSection:
        # This is a comment
        # This is another comment
        test: "something"
        testSection:
          # This is a comment
          # This is another comment
          test: "something"
```

# Creating and using

A class that extends **ConfigValues** is candidate to being a configuration.

Through a **ConfigHandler** you can load or save your config. The constructor requires a generic type of the config object you are going to use and two parameters: the class of the generic type and the path of the configuration inside the file system.

The **load()** method will fetch the contents of the yaml file and map it to your object, if the configuration file does not exist, the load method will also create it while initializing the java object.
For parsing, you need to always provide a constructor with no parameters, but you can customize the instance used when the file does not exist, by providing a custom object supplier to the load method, that will be used only on yaml creation.

The **save()** method will output the parsed yaml to file, and populate it with comments.

```java
// Loading a config will also create it if absent

Path path = Paths.get("test.yml");
ConfigHandler<CommentsConfig> configHandler = new ConfigHandler<>(CommentsConfig.class, path);
Config config = configHandler.load();
```

# A bit of content

It relies on [jackson-databind-yaml](https://www.baeldung.com/jackson-yaml) to parse the variables, therefore annotations from jackson can be used as well, such as:
 - **@JsonProperty** for giving the property a different name than the variable's
 - **@JsonIgnore** for excluding fields from a file.

A downside of the configuration files is that they must extend the **ConfigValues** class, but since they are designed with the purpose of holding variables, mostly acting like DTOs, I think it is tolerable.

It offers two custom annotations:
 - **@OnCreationValue** which is used to offer a default value when the configuration is created, that will not be recreated if deleted. This annotation is useful since, creating default values via direct initialization, will regenerate the fields on config save, even if they were deleted.
 - **@Comments** which allows you to create comments right before a property. For an empty line you can use an empty string "".
