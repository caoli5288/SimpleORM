package com.mengcraft.simpleorm.lib;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.val;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created on 17-6-26.
 */
@Data
@EqualsAndHashCode(exclude = {"file", "sublist", "clazz"})
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(exclude = {"file", "sublist", "clazz"})
public class MavenLibrary extends Library {

    private final String repository;
    private final String group;
    private final String artifact;
    private final String version;
    private final String clazz;

    private File file;
    private List<Library> sublist;

    @Override
    public File getFile() {
        if (file == null) {
            file = new File("lib", group.replace(".", File.separator)
                    + File.separator
                    + artifact
                    + File.separator
                    + artifact + '-' + version + ".jar");
        }
        return file;
    }

    @SneakyThrows
    @Override
    public List<Library> getSublist() {
        if (sublist == null) {
            val xml = new File(getFile().getParentFile(), getFile().getName() + ".pom");
            val project = XMLHelper.getSubNode(XMLHelper.getDocument(xml), "project");

            val all = XMLHelper.getElement(project, "dependencies");
            if (all == null) return (sublist = ImmutableList.of());

            val p = XMLHelper.getElement(project, "properties");
            Builder<Library> b = ImmutableList.builder();

            val list = XMLHelper.getElementList(all, "dependency");
            for (val depend : list) {
                val scope = XMLHelper.getElementValue(depend, "scope");
                if (scope == null || scope.equals("compile")) {
                    String version = XMLHelper.getElementValue(depend, "version");
                    if (version == null) throw new NullPointerException();

                    if (version.startsWith("${")) {
                        val sub = version.substring(2, version.length() - 1);
                        version = XMLHelper.getElementValue(p, sub);
                    }
                    if (version == null) throw new NullPointerException();

                    b.add(new MavenLibrary(repository,
                            XMLHelper.getElementValue(depend, "groupId"),
                            XMLHelper.getElementValue(depend, "artifactId"),
                            version,
                            null
                    ));
                }
            }
            sublist = b.build();
        }
        return sublist;
    }

    @SneakyThrows
    public void init() {
        if (!(getFile().getParentFile().isDirectory() || getFile().getParentFile().mkdirs())) {
            throw new IOException("mkdir");
        }

        loadFile(ImmutableSet.of(repository, Repository.CENTRAL.repository, Repository.I7MC.repository).iterator());
    }

    void loadFile(Iterator<String> repo) throws IOException {
        val url = repo.next()
                + '/'
                + group.replace('.', '/')
                + '/'
                + artifact
                + '/'
                + version
                + '/'
                + artifact + '-' + version;
        try {
            Files.copy(new URL(url + ".jar").openStream(),
                    getFile().toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new URL(url + ".jar.md5").openStream(),
                    new File(getFile().getParentFile(), getFile().getName() + ".md5").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.copy(new URL(url + ".pom").openStream(),
                    new File(getFile().getParentFile(), getFile().getName() + ".pom").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException io) {
            if (!repo.hasNext()) {
                throw new IOException("NO MORE REPOSITORY TO TRY", io);
            }
            loadFile(repo);
        }
    }

    @SneakyThrows
    public boolean isLoadable() {
        if (getFile().isFile()) {
            val md5 = new File(file.getParentFile(), file.getName() + ".md5");
            if (md5.isFile()) {
                byte[] buf = Files.readAllBytes(file.toPath());
                MessageDigest d = MessageDigestLocal.algorithm("md5");
                String result = Hex.hex(d.digest(buf));
                String l = Files.newBufferedReader(md5.toPath()).readLine();
                return l.indexOf(' ') == -1 ? l.equals(result) : Iterators.forArray(l.split(" ")).next().equals(result);
            }
        }
        return false;
    }

    @Override
    public boolean present() {
        if (clazz == null || clazz.isEmpty()) return false;
        try {
            val result = Class.forName(clazz);
            return !(result == null);
        } catch (Exception ign) {
        }
        return false;
    }

    public enum Repository {

        CENTRAL("https://repo1.maven.org/maven2"),
        I7MC("http://repository.i7mc.com:8008");

        final String repository;

        Repository(String repository) {
            this.repository = repository;
        }
    }

    public static MavenLibrary of(String description) {
        return of(Repository.CENTRAL.repository, description);
    }

    public static MavenLibrary of(String repository, String description) {
        val split = description.split(":");
        if (!(split.length == 3 || split.length == 4)) throw new IllegalArgumentException(description);
        val itr = Arrays.asList(split).iterator();
        return new MavenLibrary(repository, itr.next(), itr.next(), itr.next(), itr.hasNext() ? itr.next() : null);
    }

}
