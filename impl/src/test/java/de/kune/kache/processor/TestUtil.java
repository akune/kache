package de.kune.kache.processor;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class TestUtil {

    /**
     * Holds compiled byte code in a byte array.
     */
    private static class SimpleClassFile extends SimpleJavaFileObject {

        private ByteArrayOutputStream out;

        public SimpleClassFile(URI uri) {
            super(uri, Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return out = new ByteArrayOutputStream();
        }

        public byte[] getCompiledBinaries() {
            return out.toByteArray();
        }
    }

    /**
     * Adapts {@link SimpleClassFile} to the {@link JavaCompiler}.
     */
    private static class SimpleFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final List<SimpleClassFile> compiled = new ArrayList<>();

        public SimpleFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location,
                                                   String className,
                                                   JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            SimpleClassFile result = new SimpleClassFile(URI.create("string://" + className));
            compiled.add(result);
            return result;
        }

        /**
         * @return  compiled binaries processed by the current class
         */
        public List<SimpleClassFile> getCompiled() {
            return compiled;
        }
    }

    /**
     * Exposes given test source to the compiler.
     */
    private static class SimpleSourceFile extends SimpleJavaFileObject {

        private final String content;

        public SimpleSourceFile(String qualifiedClassName, String testSource) {
            super(URI.create(String.format("file://%s%s",
                    qualifiedClassName.replaceAll("\\.", "/"),
                    Kind.SOURCE.extension)),
                    Kind.SOURCE);
            content = testSource;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }

    public static class Compiler {
        public byte[] compile(String qualifiedClassName, String testSource) {
            StringWriter output = new StringWriter();

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            SimpleFileManager fileManager = new SimpleFileManager(compiler.getStandardFileManager(
                    null,
                    null,
                    null
            ));
            List<SimpleSourceFile> compilationUnits = singletonList(new SimpleSourceFile(qualifiedClassName, testSource));
            List<String> arguments = new ArrayList<>();
            arguments.addAll(asList("-classpath", System.getProperty("java.class.path")
                    ));
            JavaCompiler.CompilationTask task = compiler.getTask(output,
                    fileManager,
                    null,
                    arguments,
                    null,
                    compilationUnits);
            Boolean result = task.call();
            if (!result) {
                throw new IllegalArgumentException(output.toString());
            }
            return fileManager.getCompiled().iterator().next().getCompiledBinaries();
        }
    }

    public static class Runner {

        public Class<?> readClass(byte[] byteCode,
                                         String qualifiedClassName) {
            ClassLoader classLoader = new ClassLoader() {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    return defineClass(name, byteCode, 0, byteCode.length);
                }
            };
            Class<?> clazz;
            try {
                clazz = classLoader.loadClass(qualifiedClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't load compiled test class", e);
            }
            return clazz;
        }

        public Object invoke(Object instance,
                          String methodName,
                          long arg) throws Throwable {
            return invoke(instance.getClass(), instance, methodName, new Class[] {long.class}, arg);
        }

        public Object invoke(Object instance,
                          String methodName,
                          Object... args) throws Throwable {
            return invoke(instance.getClass(), instance, methodName, types(args), args);
        }

        private Class<?>[] types(Object[] args) {
            return Stream.of(args).map(Object::getClass).collect(Collectors.toList()).toArray(new Class[0]);
        }

        public Object invoke(Class<?> clazz,
                          Object instance,
                          String methodName,
                          Class<?>[] argumentTypes,
                          Object... args) throws Throwable {
            Method method;
            try {
                method = clazz.getMethod(methodName, argumentTypes);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Can't find the method '"+methodName+"' in the compiled test class '"+clazz+"'", e);
            }
            try {
                return method.invoke(instance, args);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw e.getCause();
            }
        }

        public Object run(byte[] byteCode,
                          String qualifiedClassName,
                          String methodName,
                          Class<?>[] argumentTypes,
                          Object... args)
                throws Throwable {
            ClassLoader classLoader = new ClassLoader() {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    return defineClass(name, byteCode, 0, byteCode.length);
                }
            };
            Class<?> clazz;
            try {
                clazz = classLoader.loadClass(qualifiedClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't load compiled test class", e);
            }
            Method method;
            try {
                method = clazz.getMethod(methodName, argumentTypes);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Can't find the 'main()' method in the compiled test class", e);
            }
            try {
                if ((method.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
                    return method.invoke(null, args);
                } else {
                    return method.invoke(clazz.newInstance(), args);
                }
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
