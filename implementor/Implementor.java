package iimplementor;

import ImplerException;
import JarImpler;

import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * The class is an implementation of the {@link JarImpler} interface.
 * The {@link #implement(Class, Path)}} and {@link #implementJar(Class, Path)} operations,
 * in addition to creating interfaces, support the creation of classes.
 * The class and all its methods do not support generic types
 *
 * @author Zabeivorota Kirill
 */
public class Implementor implements JarImpler {

    /**
     * A constant containing a suffix added to the name of the source class to generate its implementation
     */
    private static final String SUFFIX = "Impl";

    /**
     * A constant containing a line feed for the system on which the program is running
     */
    private static final String ENDL = System.lineSeparator();

    /**
     * A constant containing a space character for easier reference
     */
    private static final char SPACE = ' ';

    /**
     * A constant containing a semicolon character for easier reference
     */
    private static final char SEMICOLON = ';';

    /**
     * A constant containing a symbol for opening a block of method/constructor parameters
     * for more convenient access to it
     */
    private static final char OPEN_MBRACKET = '(';

    /**
     * A constant containing a symbol for closing a block of method/constructor parameters
     * for more convenient access to it
     */
    private static final char CLOSE_MBRACKET = ')';

    /**
     * Converts string to unicode.
     *
     * @param inp - the string that needs to be modified
     * @return is a string where all characters of the original string are
     * with codes larger than 127 replaced with characters of the form u%04x corresponding to the original
     */
    private static String fixGreatChars(final String inp) {
        final var res = new StringBuilder();
        final int maxCode = 127; // Max of 8-bit char codes
        for (char c : inp.toCharArray()) {
            res.append((c > maxCode) ? String.format("\\u%04x", (int) c) : c);
        }
        return res.toString();
    }

    /**
     * A function for recording the closure of a block of code.
     * Is paired with {@link #writeBlockStart(Writer)}
     *
     * @param writer - {@link Writer}, through which you want to record
     * @throws IOException if recording is not possible for some reason
     */

    private void writeBlockEnd(Writer writer) throws IOException {
        writer.append('}').append(ENDL);
    }

    /**
     * A function for recording the opening of a code block.
     * Is paired with {@link #writeBlockEnd(Writer)}
     *
     * @param writer {@link Writer} through which you want to record
     * @throws IOException if recording is not possible for some reason
     */
    private void writeBlockStart(Writer writer) throws IOException {
        writer.append(SPACE).append('{').append(ENDL);
    }

    /**
     * Generates and writes the class header.
     * The header includes a package that has a class, a name,
     * implemented interfaces and inherited interfaces/classes.
     *
     * @param token  can be either a class or an interface. Correctness is guaranteed only with the public modifier
     * @param writer {@link Writer} through which you want to write
     * @throws IOException if writing to the file is not possible for some reason
     */

    private void writeClassSignature(Class<?> token, Writer writer) throws IOException {
        writer.append("package ")
                .append(fixGreatChars(token.getPackageName()))
                .append(SEMICOLON).append(ENDL)
                .append("public ").append("class ")
                .append(fixGreatChars(token.getSimpleName()).concat(SUFFIX).concat(String.valueOf(SPACE)))
                .append(token.isInterface() ? "implements " : "extends ")
                .append(fixGreatChars(token.getCanonicalName()));
        writeBlockStart(writer);
    }

    /**
     * Generates and writes a call to the constructor of the inherited class.
     *
     * @param constructor includes all the data required for writing, in the form of {@link PreparedExecutable}.
     *                    If {@link PreparedExecutable} was obtained from {@link Method}, correctness is not guaranteed
     * @param writer      {@link Writer} through which you want to write
     * @throws IOException if writing to the file is not possible for some reason
     */

    private void writeConstructorDelegation(PreparedExecutable constructor, Writer writer) throws IOException {
        writer.append("     super").append(OPEN_MBRACKET)
                .append(Arrays.stream(constructor.parameters).map(
                        a -> fixGreatChars(a.getName())
                ).collect(Collectors.joining(", ")))
                .append(CLOSE_MBRACKET).append(SEMICOLON).append(ENDL);
    }

    /**
     * Generates and writes the return of a value corresponding to the default value of the returned type.
     *
     * @param executable includes all the data required for writing in the form {@link PreparedExecutable}.
     *                   If {@link PreparedExecutable} was obtained from {@link Constructor}, correctness is not guaranteed
     * @param writer     {@link Writer} through which you want to write
     * @throws IOException if writing to the file is not possible for some reason
     * @see #getDefaultValue(Class) for an accurate understanding of the concept of "default value"
     */
    private void writeMethodReturn(PreparedExecutable executable, Writer writer) throws IOException {
        writer.append("return ")
                .append(getDefaultValue(executable.retType))
                .append(SEMICOLON).append(ENDL);
    }

    /**
     * Generates and records the signature and body of the pre-prepared {@link Executable}.
     * Pre-preparation refers to the creation of a type object based on {@link Executable}.
     * {@link PreparedExecutable}
     *
     * @param executable includes all the data required for writing in the form {@link PreparedExecutable}.
     *                   In case of incorrect creation or modification of data after construction, the correctness of the work
     *                   Not guaranteed.
     * @param writer     {@link Writer} through which you want to write
     * @throws IOException if writing to the file is not possible for some reason
     */

    private void writeExecutable(PreparedExecutable executable, Writer writer) throws IOException {
        writer.append(executable.modifiers).append(SPACE);
        if (!executable.isConstructor()) {
            writer.append(fixGreatChars(executable.retType.getCanonicalName())).append(SPACE);
        }
        writeExecutableSignature(executable.name, writer, executable.getParamString());
        writer.append(executable.throwz);
        writeBlockStart(writer);
        if (executable.isConstructor()) {
            writeConstructorDelegation(executable, writer);
        } else {
            writeMethodReturn(executable, writer);
        }
        writeBlockEnd(writer);
    }

    /**
     * Write signature of executable by previously generated parameters and name.
     *
     * @param name       - name of the implementing executable
     * @param writer     - {@link Writer} through which you want to write
     * @param parameters - parameters of implementing executable
     * @throws IOException if writing to the file is not possible for some reason
     */

    private void writeExecutableSignature(String name, Writer writer, String parameters) throws IOException {
        writer.append(name).append(SPACE).append(OPEN_MBRACKET);
        writer.append(parameters);
        writer.append(CLOSE_MBRACKET).append(SPACE);
    }

    /**
     * Generates and writes the bodies and signatures of all constructors declared by the passed object.
     * The implementation of each individual constructor corresponds to
     * the result of the work {@link #writeExecutable(PreparedExecutable, Writer)}}
     *
     * @param token  is an object whose constructors need to be implemented. If the interface is passed, it does nothing.
     * @param writer {@link Writer} through which you want to write
     * @throws IOException     if writing to the file is not possible for some reason
     * @throws ImplerException if all constructors of the passed class are private
     */
    private void writeConstructors(Class<?> token, Writer writer) throws IOException, ImplerException {
        if (token.isInterface()) {
            return;
        }
        List<Constructor<?>> constructors = Arrays.stream(token.getDeclaredConstructors())
                .filter(a -> !Modifier.isPrivate(a.getModifiers())).toList();
        if (constructors.isEmpty()) {
            throw new ImplerException("All of provided class constructors are private");
        }
        for (Constructor<?> constructor : constructors) {
            writeExecutable(new PreparedExecutable(constructor, fixGreatChars(token.getSimpleName())), writer);
        }
    }

    /**
     * Generates and writes the bodies and signatures of all abstract non-private methods declared or inherited
     * a passed object.
     * The implementation of each individual method corresponds to
     * the result of the work {@link #writeExecutable(PreparedExecutable, Writer)}}
     *
     * @param token  object whose methods need to be implemented
     * @param writer {@link Writer} through which you want to write
     * @throws IOException     if writing to the file is not possible for some reason
     * @throws ImplerException if the passed class contains a private abstract method
     */

    private void writeMethods(Class<?> token, Writer writer) throws IOException, ImplerException {
        Set<PreparedExecutable> methods = new HashSet<>();
        for (Class<?> tempToken = token; tempToken.getSuperclass() != null; tempToken = tempToken.getSuperclass()) {
            Method[] temp = token.getDeclaredMethods();
            for (var method : temp) {
                PreparedExecutable characteristick = new PreparedExecutable(method);
                if (isInteresting(method)) {
                    methods.add(characteristick);
                }
            }
        }

        for (Method method : token.getMethods()) {
            PreparedExecutable characteristick = new PreparedExecutable(method);
            if (isInteresting(method)) {
                methods.add(characteristick);
            }
        }

        for (var method : methods.stream().toList()) {
            writeExecutable(method, writer);
        }
    }

    /**
     * Generates a string that is part of the signature of the executable object responsible for throwing exceptions
     *
     * @param executable object to generate a string for.
     *                   If it does not throw errors, an empty string is returned
     * @return a string in the throws format + a list of exceptions that this object is capable of generating,
     * separated by commas
     */
    private static String genThrows(Executable executable) {
        Class<?>[] exceptions = executable.getExceptionTypes();
        if (exceptions.length == 0) {
            return "";
        }
        return "throws " + Arrays.stream(exceptions).map(
                a -> fixGreatChars(a.getCanonicalName())
        ).collect(Collectors.joining(", "));
    }

    /**
     * Returns modifiers, except for transient and abstract.
     *
     * @param method whose interesting modifiers you want to get
     * @return interesting modifiers
     */

    private static int getInterestModifiers(Executable method) {
        return method.getModifiers() & ~Modifier.TRANSIENT & ~Modifier.ABSTRACT;
    }

    /**
     * Generates strings expression for default decision of provided class
     * For non-trivial types returns null
     *
     * @param token Class whose default decision needs to get
     * @return Strings expression for default decision of provided class
     */

    private String getDefaultValue(Class<?> token) {
        if (token.isPrimitive()) {
            return token.equals(boolean.class) ? "false" : (token.equals(void.class) ? "" : "0");
        }
        return "null";
    }

    /**
     * Check if method needs to be implemented.
     *
     * @param method Method that needs to check
     * @return true if Method is abstract and non-private
     * @throws ImplerException if provided method are private and abstract
     */

    private boolean isInteresting(Method method) throws ImplerException {
        int temp = method.getModifiers();
        if (Modifier.isAbstract(temp) && Modifier.isPrivate(temp)) {
            throw new ImplerException("Provided class have private abstract method");
        }
        return (Modifier.isAbstract(temp) && !Modifier.isPrivate(temp));
    }

    /**
     * Creates {@link BufferedWriter} that writes by given path.
     * If directories through path does not exist creates them.
     * If file does not exist creates file.
     *
     * @param root - {@link Path} to file for writing
     * @return {@link BufferedWriter} that writes by given Path
     * @throws IOException if writing to the file is not possible for some reason
     */

    private BufferedWriter createWriter(Path root) throws IOException {
        Path parent = root.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.newBufferedWriter(root, StandardOpenOption.CREATE);
    }

    /**
     * Checks whether implementation generation of provided class is possible
     *
     * @param token - {@link Class} that should be checked
     * @throws ImplerException if implementation of given file can not be created
     */
    private void checkInp(Class<?> token) throws ImplerException {
        if (token.isArray()
                || token.isPrimitive()
                || Modifier.isFinal(token.getModifiers())
                || token == Enum.class
                || token == Record.class
                || Modifier.isPrivate(token.getModifiers())
        ) {
            throw new ImplerException("Class given to input are incorrect");
        }
    }

    /**
     * Generates {@link Path} for .java file by joining the provided path and
     * the name of package of provided class converted to a path
     *
     * @param token - the {@link Class} to create an implementation for
     * @param inp   the path to the desired directory
     * @return the resulting path
     */

    private Path calculatePath(Path inp, Class<?> token) {
        return inp.resolve(token.getPackage().getName().replace('.', File.separatorChar))
                .resolve(token.getSimpleName().concat(SUFFIX) + ".java");
    }


    /**
     * Class to extract and containing information from {@link Executable} that important for implement
     */
    private static class PreparedExecutable {

        /**
         * Constructor that initializes fields that are initializing uniformly for constructors and methods
         *
         * @param exec - {@link Executable} from what need to extract information
         */

        private PreparedExecutable(Executable exec) {
            modifiers = Modifier.toString(getInterestModifiers(exec));
            parameters = exec.getParameters();
            throwz = genThrows(exec);
        }

        /**
         * Extract data for implementation from provided constructor
         *
         * @param constructor - constructor to extract data
         * @param cName       - for properly creation need information about name of the implementing class
         */

        PreparedExecutable(Constructor<?> constructor, String cName) {
            this(constructor);
            name = cName + SUFFIX;
        }

        /**
         * Extract data for implementation from provided method
         *
         * @param method - method to extract data
         */

        PreparedExecutable(Method method) {
            this((Executable) method);
            name = fixGreatChars(method.getName());
            retType = method.getReturnType();
        }

        /**
         * Transforms stored parameters to string for implementation
         *
         * @return String expression of executable parameters for implementation
         */
        String getParamString() {
            return Arrays.stream(parameters)
                    .map(temp -> String.format("%s %s",
                            fixGreatChars(temp.getType().getCanonicalName()),
                            fixGreatChars(temp.getName()))
                    )
                    .collect(Collectors.joining(", "));
        }

        /**
         * Checks if informatioon contains about constructor
         *
         * @return true if constructed from Constructor and else otherwise
         */
        boolean isConstructor() {
            return retType == null;
        }


        /**
         * Compares the specified object.
         * Objects are equal if their {@link #name} and {@link #parameters} are equal.
         *
         * @return hashcode for this object
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj.getClass() != this.getClass()) {
                return false;
            }
            final PreparedExecutable other = (PreparedExecutable) obj;
            return (Objects.equals(this.name, other.name) && Arrays.equals(this.parameters, other.parameters));
        }

        /**
         * Calculates hashcode using hashes of name and parameters types of provided {@link Executable}
         *
         * @return hashcode for this object
         */
        @Override
        public int hashCode() {
            return Objects.hash(this.name, Arrays.hashCode(this.parameters));
        }


        /**
         * Contains String representation of the executable name
         */
        String name;

        /**
         * Contains string representation of modifiers interesting for implementation
         *
         * @see #getInterestModifiers(Executable)
         */
        final String modifiers;

        /**
         * Contains string representation of exceptions that throwing
         *
         * @see #genThrows(Executable)
         */
        final String throwz;


        /**
         * Contains array of {@link Parameter} of provided {@link Executable}
         */
        final Parameter[] parameters;

        /**
         * Contains return time of provided {@link Executable}
         * If provided are {@link Constructor} retType are null
         */
        Class<?> retType = null;
    }

    /**
     * Produces code implementing class or interface specified by provided {@code token}.
     * <p>
     * Generated class' name the same as the class name of the type token with {@code Impl} suffix
     * added. Generated source code placed in subdirectory (generates relying on the pocket name of the provided class)
     * of the specified {@code root} directory and have file name specified above.
     *
     * @param token type token to create implementation for.
     * @param root  root directory.
     * @throws ImplerException when implementation cannot be generated.
     */

    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkInp(token);
        root = calculatePath(root, token);
        try (BufferedWriter writer = createWriter(root)) {
            writeClassSignature(token, writer);
            writeConstructors(token, writer);
            writeMethods(token, writer);
            writeBlockEnd(writer);
        } catch (IOException e) {
            throw new ImplerException("Could not write by gotten path");
        }
    }

    /**
     * Compiles generated by {@link #implement(Class, Path)} file.
     *
     * @param token   Class with source files
     * @param catalog catalog that consist file to compilation
     * @throws ImplerException if the URI and, as a result, the source files cannot be retrieved
     */

    private void compile(Class<?> token, Path catalog) throws ImplerException {
        final String sourcePath;
        try {
            sourcePath = Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new ImplerException("Error in getting URI of provided token");
        }
        final String[] args = new String[]{
                "-cp",
                sourcePath,
                calculatePath(catalog, token).toString(),
                "-encoding",
                StandardCharsets.UTF_8.name()
        };
        ToolProvider.getSystemJavaCompiler().run(null, null, null, args);
    }


    /**
     * Creates manifest with filled fields of version, author and main class.
     *
     * @return created manifest
     */
    private Manifest crManifest() {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.IMPLEMENTATION_VENDOR, "Zabeivorota Kirill");
        attributes.put(Attributes.Name.MAIN_CLASS, "Implementor");
        return manifest;
    }

    /**Remove directory with all its content.
     * @param dir - directory to delete
     */
    private static void removeDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] elements = dir.listFiles();
            if (elements != null) {
                for (File element : elements) {
                    removeDirectory(element);
                }
            }
            dir.delete();
        } else {
            dir.delete();
        }
    }

    /**
     * Produces <var>.jar</var> file implementing class or interface specified by provided <var>token</var>.
     * <p>
     * Generated class' name the same as the class name of the type token with <var>Impl</var> suffix
     * added.
     *
     * @param token   type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException when implementation cannot be generated.
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        // :NOTE: camelCase
        Path accesible_dir = jarFile.getParent();
        Path workingCatalog;
        try {
            workingCatalog = Files.createTempDirectory(accesible_dir, "_");
        } catch (IOException e) {
            throw new ImplerException("Working without temp directory could be unsafe");
        }
        implement(token, workingCatalog);
        compile(token, workingCatalog);

        try (final var writer = new JarOutputStream(Files.newOutputStream(jarFile), crManifest())) {
            String temp = token.getPackage()
                    .getName().concat(".").concat(token.getSimpleName())
                    .replace('.', '/')
                    .concat(SUFFIX).concat(".class");
            writer.putNextEntry(new ZipEntry(temp));
            Files.copy(Path.of(calculatePath(workingCatalog, token).toString().replace(".java", ".class")), writer);
        } catch (final IOException e) {
            throw new ImplerException("Error occurred. Program can not write by given path", e);
        } finally {
            removeDirectory(new File(workingCatalog.toUri()));
        }
    }

    /**
     * checks whether the arguments match the expected formats.
     * If it fits one of the formats, it returns which one
     *
     * @param args - arguments that needs to be checked for format
     * @return true if provided arguments have format of implementJar and else otherwise
     * @throws ImplerException if arguments format is not expected
     * @see #main(String[]) for more details
     */

    private static boolean checkArguments(String[] args) throws ImplerException {
        if (args.length > 3) {
            throw new ImplerException("Too much arguments provided");
        } else if (args.length < 1) {
            throw new ImplerException("No arguments were provided to function");
        } else if (args.length == 2) {
            throw new ImplerException("Can not understand arguments");
        }
        if (args.length == 3 && !Objects.equals(args[0], "-jar")) {
            throw new ImplerException("Provided arguments have wrong format");
        }
        return args.length == 3;
    }


    /**
     * Depending on provided arguments runs {@link #implement(Class, Path)} or {@link #implementJar(Class, Path)}.
     * If arguments have format "-jar class jar-file" runs {@link #implementJar(Class, Path)}
     * If arguments have format "class" runs {@link #implement(Class, Path)}
     * If arguments does not match expected formats execution stops and the corresponding message is displayed
     *
     * @param args arguments passed to program. Must satisfy expected format
     */
    public static void main(String[] args) {
        boolean isJar;
        try {
            isJar = checkArguments(args);
        } catch (ImplerException e) {
            System.err.println("Arguments have wrong format " + e.getMessage());
            return;
        }
        Class<?> token;
        try {
            token = Class.forName(isJar ? args[1] : args[0]);
        } catch (ClassNotFoundException exception) {
            System.err.println("Class file not found");
            return;
        }
        Implementor impl = new Implementor();
        try {
            if (!isJar) {
                impl.implement(token, Paths.get("."));
            } else {
                impl.implementJar(token, Path.of(args[2]));
            }
        } catch (ImplerException e) {
            System.err.println("During execution something went wrong: " + e.getMessage());
        }
    }

}
