package com.github.yp44;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

@Mojo(name = "set-property")
public class SetPropertyMojo extends AbstractMojo {

    private final static AtomicReference<Status> first_status = new AtomicReference<>();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "modifiedFiles")
    private String propertyName;

    @Parameter()
    private String files;

    @Parameter(defaultValue = "false")
    private boolean forceEmpty;

    @Parameter
    private String emptyListValue = "";

    @Parameter(defaultValue = "java,xml")
    private String extensions = "java,xml";

    // if false, it get 'status' retrieve got status from static
    @Parameter(defaultValue = "false")
    private boolean forceGitStatus;

    private List<String> extensionsList = null;

    // List of method called to retrieve file list from jgist.Status
    // http://download.eclipse.org/jgit/site/5.7.0.202003110725-r/apidocs/org/eclipse/jgit/api/Status.html
    @Parameter
    private String gitStatusElements = "modified";

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Set modified files list");
        getLog().info("Accepted extension : " + extensions);

        String list = emptyListValue;
        if (forceEmpty) {
            getLog().info("Force empty list.");
        } else if (files != null) {
            getLog().info("Set fixed list.");
            list = files;
        } else {
            getLog().info("Retrieve list from git.");
            list = getGitModifiedFiles();
        }

        project.getProperties().setProperty(propertyName, list);
        this.getRoot(project).getProperties().setProperty(propertyName, list);
        getLog().info("Set property " + propertyName + " with files list : " + list);

    }

    private MavenProject getRoot(MavenProject project) {
        MavenProject current = project;
        while (current.getParent() != null && current.getParent() != current) {
            current = current.getParent();
        }
        return current;
    }

    private String getGitModifiedFiles() throws MojoExecutionException {
        final MavenProject root = this.findRoot();

        final File gitPath = root.getBasedir().toPath().resolve(".git").toFile();
        getLog().info("Git path " + gitPath.getAbsolutePath() + "   base dir " + project.getBasedir().getName());
        final FileRepositoryBuilder builder = new FileRepositoryBuilder();

        try (final Repository repo = builder.setGitDir(gitPath).build();
             Git git = new Git(repo)) {



            final StatusCommand status = git.status();
            Status status_call = SetPropertyMojo.first_status.get();
            if(status_call == null){
                status_call = status.call();
                SetPropertyMojo.first_status.set(status_call);
            }
            else if(forceGitStatus){
                status_call = status.call();
            }

            final Status call = status_call;
            String sfiles = Arrays.stream(gitStatusElements.split(","))
                    .map((String name) -> retrieveFiles(call, name))
                    .flatMap(Set::stream)
                    .filter(this::accept)
                    .distinct()
                    .map(f -> root.getBasedir().toPath().resolve(f).toFile().getAbsolutePath())
                    .collect(Collectors.joining(","));

            return sfiles;

        } catch (IOException | GitAPIException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private MavenProject findRoot() {
        MavenProject root = this.project;
        while (root.getParent() != null && root.getParent() != root) {
            root = root.getParent();
        }
        return root;
    }

    private Set<String> retrieveFiles(Status status, String propName) {
        try {
            final String getterName = "get" + propName.substring(0, 1).toUpperCase() + propName.substring(1);
            final Method m = status.getClass().getMethod(getterName);
            return (Set<String>) m.invoke(status);
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    private boolean accept(final String f) {
        if (extensionsList == null) {
            extensionsList = Arrays.asList(extensions.split(","));
        }

        for (String ext : extensionsList) {
            if (f.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

}
