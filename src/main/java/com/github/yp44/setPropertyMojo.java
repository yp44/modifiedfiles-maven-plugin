package com.github.yp44;

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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Stream.concat;

@Mojo(name = "set-property")
public class setPropertyMojo extends AbstractMojo {

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
    private String extensions;

    private List<String> extensionsList = null;

    // List of method called to retrieve file list from jgist.Status
    // http://download.eclipse.org/jgit/site/5.7.0.202003110725-r/apidocs/org/eclipse/jgit/api/Status.html
    @Parameter
    private String gitStatusElements = "modified,uncommittedChanges,untracked";

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
        getLog().info("Set property " + propertyName + " with files list : " + list);

    }

    private String getGitModifiedFiles() throws MojoExecutionException {
        final File gitPath = project.getBasedir().toPath().resolve(".git").toFile();

        final FileRepositoryBuilder builder = new FileRepositoryBuilder();
        final Repository repo;
        try {
            repo = builder.setGitDir(gitPath).build();
            Git git = new Git(repo);

            final StatusCommand status = git.status();
            final Status call = status.call();

            final StringBuilder files = new StringBuilder();

            Stream<String> stream = Stream.empty();
            List<String> elementsSource = Arrays.asList(gitStatusElements.split(","));

            for (String method : elementsSource) {
                try {
                    method = method.substring(0, 1).toUpperCase() + method.substring(1);
                    final Method m = call.getClass().getMethod("get" + method);
                    Set<String> ff = (Set<String>) m.invoke(call);
                    stream = concat(stream, ff.stream());
                    getLog().debug("Get git status " + method + " list : " + ff.stream().map(this::toAbsolute).map(f -> "\n" + f).reduce("", (a, f) -> a + f));
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

            stream.filter(this::accept)
                    .map(this::toAbsolute)
                    .forEach(f -> files.append(f).append(","));
            String sfiles = files.toString();

            if (!sfiles.isEmpty()) {
                sfiles = sfiles.substring(0, sfiles.length() - 1);
            }

            git.close();
            repo.close();

            return sfiles;

        } catch (IOException | GitAPIException e) {
            throw new MojoExecutionException(e.getMessage(), e);
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

    private String toAbsolute(final String f) {
        final String projectFolder = this.project.getBasedir().getAbsolutePath();
        return Paths.get(projectFolder, f).toString();
    }

}