package com.mengcraft.simpleorm.lib;

import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Created on 17-6-26.
 */
@Data
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MavenLibrary extends Library {

    private final String repository;
    private final String group;
    private final String artifact;
    private final String version;
    private File file;

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
        File pom = new File(getFile().getParentFile(), getFile().getName() + ".pom");
        Node root = XMLHelper.getDocumentBy(pom).getFirstChild();

        Element all = XMLHelper.getElementBy(root, "dependencies");
        if (all == null) return ImmutableList.of();

        ImmutableList.Builder<Library> b = ImmutableList.builder();

        for (Element depend : XMLHelper.getElementListBy(all, "dependency")) {
            String scope = XMLHelper.getElementValue(depend, "scope");
            if (scope == null || scope.equals("compile")) {
                b.add(new MavenLibrary(repository,
                        XMLHelper.getElementValue(depend, "groupId"),
                        XMLHelper.getElementValue(depend, "artifactId"),
                        XMLHelper.getElementValue(depend, "version")
                ));
            }
        }

        return b.build();
    }

    @SneakyThrows
    public void init() {
        if (!(getFile().getParentFile().isDirectory() || getFile().getParentFile().mkdirs())) {
            throw new IOException("mkdir");
        }

        val lib = repository
                + group.replace('.', '/')
                + '/'
                + artifact
                + '/'
                + version
                + '/'
                + artifact + '-' + version;

        Files.copy(new URL(lib + ".jar").openStream(),
                getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Files.copy(new URL(lib + ".jar.md5").openStream(),
                new File(getFile().getParentFile(), getFile().getName() + ".md5").toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Files.copy(new URL(lib + ".pom").openStream(),
                new File(getFile().getParentFile(), getFile().getName() + ".pom").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static final char[] HEX = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f'
    };

    protected String hex(byte[] out) {
        StringBuilder buf = new StringBuilder();
        for (byte b : out) {
            buf.append(HEX[b >>> 4 & 0xf]);
            buf.append(HEX[b & 0xf]);
        }
        return buf.toString();
    }

    @SneakyThrows
    public boolean isLoadable() {
        if (getFile().isFile()) {
            val check = new File(file.getParentFile(), file.getName() + ".md5");
            if (check.isFile()) {
                val digest = MessageDigest.getInstance("MD5");
                val buf = ByteBuffer.allocate(1 << 16);
                FileChannel channel = FileChannel.open(file.toPath());
                while (!(channel.read(buf) == -1)) {
                    buf.flip();
                    digest.update(buf);
                    buf.compact();
                }
                return Files.newBufferedReader(check.toPath()).readLine().equals(hex(digest.digest()));
            }
        }
        return false;
    }

    public enum Repository {

        CENTRAL("http://central.maven.org/maven2/");

        private final String repository;

        Repository(String repository) {
            this.repository = repository;
        }
    }

    public static MavenLibrary of(String description) {
        return of(Repository.CENTRAL.repository, description);
    }

    public static MavenLibrary of(String repository, String description) {
        val split = description.split(":");
        if (!(split.length == 3)) throw new IllegalArgumentException(description);
        val itr = Arrays.asList(split).iterator();
        return new MavenLibrary(repository, itr.next(), itr.next(), itr.next());
    }

}
