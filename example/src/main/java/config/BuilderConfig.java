package config;

import buildable.annotation.BuiltWith;
import buildable.config.BuildableClass;
import buildable.config.BuildableConfig;
import buildable.config.BuiltOn;


@BuildableConfig
public class BuilderConfig {

    @BuildableClass
    private ThirdPartyTestObject testObject;

    @BuildableClass(excludedFields = "ignored")
    private ExcludedFieldTestObject excludedFieldTestObject;

    @BuildableClass({
            @BuiltOn(name = "name", value = @BuiltWith(defaultValue = "John")),
            @BuiltOn(name = "age", value = @BuiltWith(defaultValue = "25")),
            @BuiltOn(name = "account", value = @BuiltWith(defaultValue = "new Account(\"account_id\")"))
    })
    private DefaultTestObject defaultTestObject;
}
