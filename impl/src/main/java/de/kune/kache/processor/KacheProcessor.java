package de.kune.kache.processor;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;
import de.kune.kache.annotation.CacheAccessor;
import de.kune.kache.annotation.Cached;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.nio.CharBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({"de.kune.kache.annotation.Cached", "de.kune.kache.annotation.CacheAccessor"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class KacheProcessor extends AbstractProcessor {

    private Trees trees;
    private Context context;
    private TreeMaker factory;
    private Names names;
    private ClassReader classReader;
    private Symtab symbols;
    private ParserFactory parserFactory;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
        context = ((JavacProcessingEnvironment) processingEnv).getContext();
        factory = TreeMaker.instance(context);
        names = Names.instance(context);
        classReader = ClassReader.instance(context);
        symbols = Symtab.instance(context);
        Symbol.ClassSymbol sym = classReader.enterClass(names.fromString(Map.class.getName()));

        parserFactory = ParserFactory.instance(context);
    }

    private void replaceMethodBody(JCTree.JCMethodDecl method, String s) {
        StringBuilder b = new StringBuilder("{" + s + "}");
        // TODO: Figure out why we need this stupid work-around to fix the position of parsed blocks
        while (b.length() < method.pos) {
            b.insert(0, " ");
        }
        JavacParser parser = parserFactory.newParser(
                b
                , false, true, true
        );
        method.body = parser.block();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        final TreePathScanner<Object, CompilationUnitTree> cachedScanner =
                new TreePathScanner<Object, CompilationUnitTree>() {

                    @Override
                    public Trees visitMethod(final MethodTree methodTree, final CompilationUnitTree unitTree) {
                        ((JCTree.JCMethodDecl) methodTree).accept(new TreeTranslator() {
                            @Override
                            public void visitMethodDef(JCTree.JCMethodDecl methodTree) {

                                ((JCCompilationUnit) unitTree).getTypeDecls().forEach(typeTree -> ((JCTree.JCClassDecl) typeTree).accept(new TreeTranslator() {
                                    @Override
                                    public void visitClassDef(JCTree.JCClassDecl classTree) {
                                        ListBuffer<JCTree> nDefs = new ListBuffer<>();
                                        nDefs.addAll(classTree.defs);
                                        // add original method with new name
                                        JCTree.JCMethodDecl method = (JCTree.JCMethodDecl) methodTree.clone();
                                        method.name = method.name.table.names.fromString("_doUncached_" + method.name.toString());
                                        method.mods = (JCTree.JCModifiers) method.mods.clone();
                                        if ((method.mods.flags & Flags.PUBLIC) == Flags.PUBLIC) {
                                            method.mods.flags -= Flags.PUBLIC;
                                        }
                                        method.mods.flags = method.mods.flags | Flags.PRIVATE;
                                        ListBuffer<JCVariableDecl> newParams = new ListBuffer<>();
                                        methodTree.params.forEach(p->newParams.add((JCVariableDecl) p.clone()));
                                        methodTree.params = newParams.toList();
                                        methodTree.params.forEach(p->p.mods = (JCTree.JCModifiers) p.mods.clone());
                                        methodTree.params.forEach(p->p.mods.flags |= Flags.FINAL);
                                        nDefs.append(method);
                                        // add cache variable
                                        JCVariableDecl cacheVar = factory.VarDef(factory.Modifiers(Flags.PRIVATE), names.fromString("_cache_" + methodTree.name.toString()), factory.TypeApply(factory.QualIdent(classReader.enterClass(names.fromString(ConcurrentMap.class.getName()))), List.of(factory.QualIdent(classReader.enterClass(names.fromString(Object.class.getName()))), factory.QualIdent(classReader.enterClass(names.fromString(Object.class.getName()))))), factory.NewClass(null, null, factory.TypeApply(factory.QualIdent(classReader.enterClass(names.fromString(ConcurrentHashMap.class.getName()))), List.nil()), List.nil(), null));
                                        if ((methodTree.mods.flags & Flags.STATIC) == Flags.STATIC) {
                                            cacheVar.mods.flags |= Flags.STATIC;
                                        }
                                        nDefs.prepend(cacheVar);
                                        classTree.defs = nDefs.toList();
                                        // rewrite original method to use cache
                                        final Function<String, String> replacer = s->s.replace("${key}", "java.util.Arrays.asList("+method.params.stream().map(p -> p.name.toString()).collect(Collectors.joining(", "))+")")
                                                .replace("${cache}", cacheVar.name.toString())
                                                .replace("${params}", method.params.stream().map(p -> p.name.toString()).collect(Collectors.joining(", ")))
                                                .replace("${method}", method.name.toString())
                                                .replace("${returnType}", method.getReturnType().toString());

                                        String e = "return (${returnType}) ${cache}.computeIfAbsent(${key}, (___cacheParam)->${method}(${params}) );";
//                                        e = parserFactory.newParser(replacer.apply(e), false, false, false).parseStatement().toString();
//                                        StringBuilder b = new StringBuilder(e);
//                                        // TODO: Figure out why we need this stupid work-around to fix the position of parsed statements
//                                        while (b.length() < method.pos) {
//                                            b.insert(0, " ");
//                                        }
//                                        System.out.println(b);
//                                        JavacParser parser = parserFactory.newParser(
//                                                b
//                                                , false, true, true
//                                        );
//                                        JCTree.JCStatement statement = parser.parseStatement();
//                                        methodTree.body = factory.Block(0, List.of(
//                                                statement
//                                        ));
                                        replaceMethodBody(methodTree, replacer.apply(e));
                                        super.visitClassDef(classTree);
                                    }

                                }));
                                super.visitMethodDef(methodTree);
                            }
                        });
                        return trees;
                    }
                };

        final TreePathScanner<Object, CompilationUnitTree> cacheAccessorScanner =
                new TreePathScanner<Object, CompilationUnitTree>() {
                    @Override
                    public Trees visitMethod(final MethodTree methodTree, final CompilationUnitTree unitTree) {
                        ((JCTree.JCMethodDecl) methodTree).accept(new TreeTranslator() {
                            @Override
                            public void visitMethodDef(JCTree.JCMethodDecl methodTree) {
                                String cacheMethodName = methodTree.getModifiers().getAnnotations().stream().filter(a->a.getAnnotationType().type.toString().equals(CacheAccessor.class.getName())).flatMap(a->a.attribute.values.stream()).filter(attr->attr.fst.name.toString().equals("value")).map(attr->attr.snd.getValue()).findFirst().map(Object::toString).orElse(null);
                                replaceMethodBody(methodTree, "return _cache_" + cacheMethodName + ";");
                            }
                        });
                        return trees;
                    }
                };


        for (final Element element : roundEnv.getElementsAnnotatedWith(Cached.class)) {
            final TreePath path = trees.getPath(element);
            cachedScanner.scan(path, path.getCompilationUnit());
        }
        for (final Element element : roundEnv.getElementsAnnotatedWith(CacheAccessor.class)) {
            final TreePath path = trees.getPath(element);
            cacheAccessorScanner.scan(path, path.getCompilationUnit());
        }
        // TODO: potentially try to fix compilation unit here instead of the stupid work-around with blanks above

        // Claiming that annotations have been processed by this processor
        return true;

    }
}
