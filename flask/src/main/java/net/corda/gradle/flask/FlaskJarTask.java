package net.corda.gradle.flask;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import net.corda.flask.common.Flask;
import net.corda.flask.common.ManifestEscape;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FlaskJarTask extends AbstractArchiveTask {

    @Setter
    private Property<String> launcherClassName;

    @Input
    public Property<String> getLauncherClassName() {
        return launcherClassName;
    }

    @Setter
    private Property<String> mainClassName;

    @Input
    public Property<String> getMainClassName() {
        return mainClassName;
    }

    @Setter
    private ListProperty<String> jvmArgs;

    @Input
    public ListProperty<String> getJvmArgs() {
        return jvmArgs;
    }

    public void includeLibraries(FileCollection fileCollection) {
        into(Flask.Constants.LIBRARIES_FOLDER, (copySpec) -> copySpec.from(fileCollection));
    }

    private static class JavaAgent {
        File jar;
        String args;
    }

    private final List<JavaAgent> javaAgents;

    @InputFiles
    FileCollection getAgentJars() {
        File[] jars = javaAgents.stream().map(it -> it.jar).toArray(File[]::new);
        return getProject().files((Object[]) jars);
    }

    @Input
    List<String> getAgentArgs() {
        return javaAgents.stream().map(it -> it.args).collect(Collectors.toList());
    }

    public void javaAgent(Action<JavaAgent> action) {
        JavaAgent agent = new JavaAgent();
        action.execute(agent);
        if(agent.jar == null) throw new GradleException("No jar file specified for Java agent");
        javaAgents.add(agent);
    }

    @Inject
    public FlaskJarTask(ObjectFactory objects) {
        BasePluginConvention basePluginConvention = getProject().getConvention().getPlugin(BasePluginConvention.class);
        getDestinationDirectory().set(basePluginConvention.getLibsDir());
        getArchiveBaseName().convention(getProject().getName());
        getArchiveExtension().convention("jar");
        launcherClassName = objects.property(String.class).convention(Flask.Constants.DEFAULT_LAUNCHER_NAME);
        mainClassName = objects.property(String.class);
        jvmArgs = objects.listProperty(String.class);
        javaAgents = new ArrayList<>();
        from(getProject().tarTree(LauncherResource.instance), copySpec -> exclude(JarFile.MANIFEST_NAME));
    }

    @Input
    @SneakyThrows
    public String getLauncherArchiveHash() {
        return Flask.bytes2Hex(Flask.computeSHA256Digest(LauncherResource.instance::read));
    }

    @RequiredArgsConstructor
    private static class StreamAction implements CopyActionProcessingStreamAction {

        private final ZipOutputStream zoos;
        private final Manifest manifest;
        private final MessageDigest md;
        private final byte[] buffer;

        /**
         * This is required since the manifest has to be the first zip entry in a jar archive, as an example,
         * {@link java.util.jar.JarInputStream} assumes the manifest is the first (or second at most)
         * entry in the jar and simply returns a null manifest if that is not the case.
         * In this case the manifest has to contain the hash of all the jar entries, so it cannot
         * be computed in advance, all entries have to be read to compute the manifest, then the manifest can
         * be written to the zip file as the first entries, then all the other entries can be added.
         */
        private final List<FileCopyDetailsInternal> fileCopyDetailsInternals = new ArrayList<>();

        @SneakyThrows
        public void write() {
            ZipEntry zipEntry = new ZipEntry(Flask.Constants.METADATA_FOLDER + '/');
            zoos.putNextEntry(zipEntry);
            zipEntry = new ZipEntry(JarFile.MANIFEST_NAME);
            zipEntry.setMethod(ZipEntry.DEFLATED);
            zoos.putNextEntry(zipEntry);
            manifest.write(zoos);
            for(FileCopyDetails fileCopyDetails : fileCopyDetailsInternals) {
                String entryName = fileCopyDetails.getRelativePath().toString();
                if(Objects.equals(Flask.Constants.METADATA_FOLDER, entryName)) continue;
                zipEntry = new ZipEntry(entryName);
                if(fileCopyDetails.isDirectory()) {
                    zipEntry = new ZipEntry(entryName + '/');
                    zoos.putNextEntry(zipEntry);
                } else {
                    boolean compressed = Flask.splitExtension(fileCopyDetails.getSourceName())
                            .map(entry -> Objects.equals(".jar", entry.getValue()))
                            .orElse(false);
                    if (!compressed) {
                        zipEntry.setMethod(ZipEntry.DEFLATED);
                    } else {
                        try (InputStream is = new FileInputStream(fileCopyDetails.getFile())) {
                            Flask.computeSizeAndCrc32(zipEntry, is, buffer);
                        }
                        zipEntry.setMethod(ZipEntry.STORED);
                    }
                    try(InputStream is = new FileInputStream(fileCopyDetails.getFile())) {
                        zoos.putNextEntry(zipEntry);
                        Flask.write2Stream(is, zoos, buffer);
                    }
                }
            }
        }

        @Override
        public void processFile(FileCopyDetailsInternal fileCopyDetailsInternal) {
            String entryName = fileCopyDetailsInternal.getRelativePath().toString();
            if(!fileCopyDetailsInternal.isDirectory() && entryName.startsWith(Flask.Constants.LIBRARIES_FOLDER)) {
                Supplier<InputStream> streamSupplier = new Supplier<InputStream>() {
                    @Override
                    @SneakyThrows
                    public InputStream get() {
                        return new FileInputStream(fileCopyDetailsInternal.getFile());
                    }
                };
                Attributes attr = manifest.getEntries().computeIfAbsent(entryName, it -> new Attributes());
                md.reset();
                attr.putValue(Flask.ManifestAttributes.ENTRY_HASH,
                        Base64.getEncoder().encodeToString(Flask.computeDigest(streamSupplier, md)));
            }
            fileCopyDetailsInternals.add(fileCopyDetailsInternal);
        }
    }

    @Override
    protected CopyAction createCopyAction() {
        File destination = getArchiveFile().get().getAsFile();
        return new CopyAction() {
            @Override
            @SneakyThrows
            public WorkResult execute(CopyActionProcessingStream copyActionProcessingStream) {
                try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(destination))) {
                    Manifest manifest = new Manifest();
                    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, launcherClassName.get());
                    Optional.ofNullable(mainClassName.getOrNull()).ifPresent(it ->
                        manifest.getMainAttributes().putValue(Flask.ManifestAttributes.APPLICATION_CLASS, it));
                    Optional.ofNullable(jvmArgs.getOrNull())
                            .filter(it -> !it.isEmpty())
                            .ifPresent(it -> manifest.getMainAttributes().putValue(Flask.ManifestAttributes.JVM_ARGS,
                                    ManifestEscape.escapeStringList(it)));
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[Flask.Constants.BUFFER_SIZE];
                    if(!javaAgents.isEmpty()) {
                        List<String> agentsStrings = javaAgents.stream().map(javaAgent -> {
                            md.reset();
                            Supplier<InputStream> streamSupplier = new Supplier<InputStream>() {
                                @Override
                                @SneakyThrows
                                public InputStream get() {
                                    return new FileInputStream(javaAgent.jar);
                                }
                            };
                            StringBuilder sb = new StringBuilder();
                            sb.append(Flask.bytes2Hex(Flask.computeDigest(streamSupplier, md)));
                            if (!javaAgent.args.isEmpty()) {
                                sb.append('=');
                                sb.append(javaAgent.args);
                            }
                            return sb.toString();
                        }).collect(Collectors.toList());
                        manifest.getMainAttributes().putValue(Flask.ManifestAttributes.JAVA_AGENTS, ManifestEscape.escapeStringList(agentsStrings));
                    }
                    StreamAction streamAction = new StreamAction(zipOutputStream, manifest, md, buffer);
                    copyActionProcessingStream.process(streamAction);
                    streamAction.write();
                    return () -> true;
                }
            }
        };
    }
}