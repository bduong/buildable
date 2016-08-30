package buildable.config.processor;

import buildable.annotation.BuiltWith;
import buildable.config.BuildableClass;
import buildable.config.BuildableConfig;
import buildable.config.BuiltOn;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static buildable.annotation.processor.Util.capitalize;
import static java.util.Arrays.asList;
import static javax.tools.Diagnostic.Kind.NOTE;

@SupportedAnnotationTypes(value = {
        "buildable.config.BuildableConfig",
        "buildable.config.BuildableClass"
})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SuppressWarnings("UnusedDeclaration")
public class BuildableConfigProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        this.processingEnv.getMessager().printMessage(NOTE, "Creating builders for classes annotated with @Buildable...");
        if (roundEnvironment.processingOver()) {
            return true;
        }
        final Set<? extends Element> config = roundEnvironment.getElementsAnnotatedWith(BuildableConfig.class);
        if (config.size() == 0) {
            return true;
        }

        Elements elementUtils = processingEnv.getElementUtils();
        for (Element element : config) {
            TypeElement configClass = (TypeElement) element;
            List<? extends Element> buildableClasses = configClass.getEnclosedElements();
            //Each Class in the builder config
            for (Element buildableClass : buildableClasses) {
                if (buildableClass.getKind().isField()) {
                    VariableElement fieldClass = (VariableElement) buildableClass;
                    DeclaredType typeMirror = (DeclaredType) fieldClass.asType();
                    TypeElement clazz = (TypeElement) typeMirror.asElement();
                    //fields of the class
                    List<? extends Element> variables = new ArrayList<>(clazz.getEnclosedElements());

                    variables.removeIf(v -> !v.getKind().isField());

                    Map<String, BuiltWith> defaults = new HashMap<>();

                    BuildableClass annotation = fieldClass.getAnnotation(BuildableClass.class);
                    if (annotation != null) {
                        List<String> excludedFields = asList(annotation.excludedFields());
                        variables.removeIf(v -> excludedFields.contains(v.toString()));

                        defaults = Arrays.stream(annotation.value()).collect(Collectors.toMap(BuiltOn::name, BuiltOn::value));
                    }


                    ClassName className = ClassName.get(clazz);
                    TypeSpec.Builder builder = TypeSpec.classBuilder(clazz.getSimpleName() + "Builder")
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
                            .addMethod(MethodSpec.methodBuilder(createFactoryMethodName(clazz.getSimpleName()))
                                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                    .returns(ParameterizedTypeName.get(clazz.asType()))
                                    .addStatement("return new $T()", className)
                                    .build()
                            );

                    for (Element variable : variables) {
                        FieldSpec.Builder fieldBuilder = FieldSpec.builder(TypeName.get(variable.asType()), variable
                                .getSimpleName().toString(), Modifier.PRIVATE);
                        if(defaults.containsKey(variable.getSimpleName().toString())) {
                            String sub = "java.lang.String".equals(variable.asType().toString()) ? "$S" : "$L";
                            fieldBuilder.initializer(sub, defaults.get(variable.getSimpleName().toString()).defaultValue());
                        }


                        ClassName builderName = ClassName.get(className.packageName(), clazz.getSimpleName() +
                                "Builder");
                        builder.addField(fieldBuilder.build());
                        builder.addMethod(MethodSpec.methodBuilder(determineFluentMethodName((VariableElement) variable))
                                .addModifiers(Modifier.PUBLIC)
                                .returns(builderName)
                                .addParameter(TypeName.get(variable.asType()), variable.getSimpleName().toString())
                                .addStatement("this.$L = $L", variable.getSimpleName().toString(), variable.getSimpleName().toString())
                                .addStatement("return this")
                                .build()
                        );
                    }

                    ParameterSpec classParam = ParameterSpec.builder(Class.class, "clazz").build();
                    ParameterSpec fieldParam = ParameterSpec.builder(String.class, "fieldName").build();
                    MethodSpec getDeclaredField = MethodSpec.methodBuilder("getDeclaredField")
                            .addException(NoSuchFieldException.class)
                            .addModifiers(Modifier.PRIVATE)
                            .returns(Field.class)
                            .addParameter(classParam).addParameter(fieldParam)
                            .beginControlFlow("try")
                            .addStatement("return $N.getDeclaredField($N)", classParam, fieldParam)
                            .nextControlFlow("catch ($T e)", NoSuchFieldException.class)
                            .addStatement("Class superclass = $N.getSuperclass();", classParam)
                            .beginControlFlow("if (superclass == null)")
                            .addStatement("throw e")
                            .nextControlFlow("else")
                            .addStatement("return getDeclaredField(superclass, $N)", fieldParam)
                            .endControlFlow()
                            .endControlFlow()
                            .build();


                    MethodSpec.Builder build = MethodSpec.methodBuilder("build").returns(className).addModifiers(Modifier.PUBLIC);
                    build.beginControlFlow("try")
                            .addStatement("final $T clazz = $T.forName($T.class.getCanonicalName())", Class.class, Class.class, className)
                            .addStatement("final $T instance = ($T) clazz.newInstance()", className, className);

                    for (Element variable : variables) {
                        String methodName = variable.getSimpleName().toString() + "Method";
                        String fieldName = variable.getSimpleName().toString() + "Field";
                        build
                                .beginControlFlow("try")
                                .addStatement("final $T $L = clazz.getDeclaredMethod($S, $T.class)", Method.class, methodName, "set" + capitalize(variable.getSimpleName()), variable.asType())
                                .addStatement("$L.setAccessible(true)", methodName)
                                .addStatement("$L.invoke(instance, $L)", methodName, variable.getSimpleName().toString())
                                .nextControlFlow("catch ($T nsme)", NoSuchMethodException.class)
                                .addStatement("final $T $L = $N(clazz, $S)", Field.class, fieldName, getDeclaredField, variable.getSimpleName().toString())
                                .addStatement("$L.setAccessible(true)", fieldName)
                                .addStatement("$L.set(instance, $L)", fieldName, variable.getSimpleName().toString())
                                .addStatement("$L.setAccessible(false)", fieldName)
                                .endControlFlow();
                    }
                    build.addStatement("return instance")
                            .nextControlFlow("catch ($T | $T e)", Exception.class, Error.class)
                            .addStatement("e.printStackTrace()")
                            .endControlFlow()
                            .addStatement("return null");



                    builder.addMethod(build.build());
                    builder.addMethod(getDeclaredField);

                    TypeSpec builderClass = builder.build();
                    JavaFile javaFile = JavaFile.builder(elementUtils.getPackageOf(typeMirror.asElement()).toString(), builderClass).indent("\t").build();

                    try {
                        javaFile.writeTo(processingEnv.getFiler());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                }
            }

        }
        return true;
    }

    private String createFactoryMethodName(Name className) {
        if (className.toString().matches("[AEIOUaeiou].*")) {
            return "an" + className.toString();
        } else {
            return "a" + className.toString();
        }
    }

    private String determineFluentMethodName(final VariableElement field) {
        return "with" + capitalize(field.getSimpleName());
    }
}
