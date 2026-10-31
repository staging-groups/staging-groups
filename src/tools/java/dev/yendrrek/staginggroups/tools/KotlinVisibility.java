package dev.yendrrek.staginggroups.tools;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import kotlin.metadata.Attributes;
import kotlin.metadata.KmClass;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmProperty;
import kotlin.metadata.jvm.KotlinClassMetadata;

/**
 * Prints the Kotlin visibility of platform classes and their functions and properties. Bytecode
 * shows a Kotlin {@code internal} member as public and the Plugin Verifier does not report it, so
 * only the Kotlin metadata tells.
 *
 * <p>Run it through Gradle, which supplies the plugin's compile classpath as the
 * {@code platform.classpath} system property: {@code ./gradlew kotlinVisibility --args='<class>...'}.
 */
public class KotlinVisibility {

    public static void main(String[] args) throws Exception {
        String platformClasspath = System.getProperty("platform.classpath");
        if (platformClasspath == null || args.length == 0) {
            System.err.println("usage: ./gradlew kotlinVisibility --args='<class>...'");
            System.exit(2);
        }
        List<URL> entries = new ArrayList<>();
        for (String entry : platformClasspath.split(File.pathSeparator)) entries.add(new File(entry).toURI().toURL());
        // Parent-first, so kotlin.Metadata resolves to the same class as the one this program reads.
        try (URLClassLoader platform = new URLClassLoader(entries.toArray(URL[]::new), KotlinVisibility.class.getClassLoader())) {
            for (String name : args) print(Class.forName(name, false, platform));
        }
    }

    private static void print(Class<?> type) {
        kotlin.Metadata metadata = type.getAnnotation(kotlin.Metadata.class);
        if (metadata == null) {
            System.out.println(type.getName() + ": Java; check annotations with javap -v");
            return;
        }
        switch (KotlinClassMetadata.readLenient(metadata)) {
            case KotlinClassMetadata.Class kotlinClass -> {
                KmClass km = kotlinClass.getKmClass();
                System.out.println(type.getName() + ": class " + Attributes.getVisibility(km));
                printMembers(km.getFunctions(), km.getProperties());
            }
            case KotlinClassMetadata.FileFacade facade -> {
                System.out.println(type.getName() + ": top-level declarations");
                printMembers(facade.getKmPackage().getFunctions(), facade.getKmPackage().getProperties());
            }
            case KotlinClassMetadata other -> System.out.println(type.getName() + ": " + other.getClass().getSimpleName());
        }
    }

    private static void printMembers(List<KmFunction> functions, List<KmProperty> properties) {
        for (KmFunction function : functions) System.out.println("  fun " + function.getName() + ": " + Attributes.getVisibility(function));
        for (KmProperty property : properties) System.out.println("  val " + property.getName() + ": " + Attributes.getVisibility(property));
    }
}
