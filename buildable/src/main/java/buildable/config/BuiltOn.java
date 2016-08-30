package buildable.config;


import buildable.annotation.BuiltWith;

public @interface BuiltOn {

    String name();

    BuiltWith value();
}
