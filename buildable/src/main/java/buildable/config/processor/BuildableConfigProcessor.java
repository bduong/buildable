package buildable.config.processor;

import buildable.config.BuildableConfig;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;

import java.util.List;
import java.util.Set;

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

        for (Element element : config) {
            TypeElement configClass = (TypeElement) element;
            List<? extends Element> buildableClasses = configClass.getEnclosedElements();
            //Each Class in the builder config
            for (Element buildableClass : buildableClasses) {
                if (buildableClass.getKind().isField()) {
                    VariableElement clazz = (VariableElement) buildableClass;
                    DeclaredType typeMirror = (DeclaredType) clazz.asType();
                    //fields of the class
                    List<? extends Element> varibles = typeMirror.asElement().getEnclosedElements();


                }
            }

        }
        return true;
    }
}
