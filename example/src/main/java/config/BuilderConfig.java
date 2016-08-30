package config;

import buildable.config.BuildableClass;
import buildable.config.BuildableConfig;
import buildable.config.BuildableDefault;


@BuildableConfig
public class BuilderConfig {

    @BuildableClass
    private ThirdPartyTestObject testObject;

    @BuildableClass(excludedFields = "ignored")
    private ExcludedFieldTestObject excludedFieldTestObject;

    @BuildableClass(defaultValues = {
            @BuildableDefault(name = "name", value = "John"),
            @BuildableDefault(name = "age", value = "25"),
            @BuildableDefault(name = "account", value = "new Account(\"account_id\")")
    })
    private DefaultTestObject defaultTestObject;
}
