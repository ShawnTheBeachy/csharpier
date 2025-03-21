package com.intellij.csharpier;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CSharpierProcessProvider implements DocumentListener, Disposable, IProcessKiller {
    private final CustomPathInstaller customPathInstaller = new CustomPathInstaller();
    private final Logger logger = CSharpierLogger.getInstance();
    private final Project project;

    private boolean warnedForOldVersion;
    private final HashMap<String, Long> lastWarmedByDirectory = new HashMap<>();
    private final HashMap<String, String> csharpierVersionByDirectory = new HashMap<>();
    private final HashMap<String, ICSharpierProcess> csharpierProcessesByVersion = new HashMap<>();

    public CSharpierProcessProvider(@NotNull Project project) {
        this.project = project;

        for (var fileEditor : FileEditorManager.getInstance(project).getAllEditors()) {
            var path = fileEditor.getFile().getPath();
            if (path.toLowerCase().endsWith(".cs")) {
                this.findAndWarmProcess(path);
            }
        }

        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(this, this);
    }

    @NotNull
    static CSharpierProcessProvider getInstance(@NotNull Project project) {
        return project.getService(CSharpierProcessProvider.class);
    }

    // TODO ideally this would warm on document open/focus. But there doesn't seem to be an event for focus
    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
        var document = event.getDocument();
        var file = FileDocumentManager.getInstance().getFile(document);

        if (file == null
                || file.getExtension() == null
                || !file.getExtension().equalsIgnoreCase("cs")
        ) {
            return;
        }
        var filePath = file.getPath();
        this.findAndWarmProcess(filePath);
    }

    private void findAndWarmProcess(String filePath) {
        var directory = Path.of(filePath).getParent().toString();
        var now = Instant.now().toEpochMilli();
        var lastWarmed = this.lastWarmedByDirectory.getOrDefault(directory, Long.valueOf(0));
        if (lastWarmed + 5000 > now) {
            return;
        }
        this.logger.debug("Ensure there is a csharpier process for " + directory);
        this.lastWarmedByDirectory.put(directory, now);
        var version = this.csharpierVersionByDirectory.getOrDefault(directory, null);
        if (version == null) {
            version = this.getCSharpierVersion(directory);
            if (version == null || version.isEmpty()) {
                InstallerService.getInstance(this.project).displayInstallNeededMessage(directory, this);
            }
            this.csharpierVersionByDirectory.put(directory, version);
        }

        if (!this.csharpierProcessesByVersion.containsKey(version)) {
            this.csharpierProcessesByVersion.put(version, this.setupCSharpierProcess(
                    directory,
                    version
            ));
        }
    }

    public ICSharpierProcess getProcessFor(String filePath) {
        var directory = Path.of(filePath).getParent().toString();
        var version = this.csharpierVersionByDirectory.getOrDefault(directory, null);
        if (version == null) {
            this.findAndWarmProcess(filePath);
            version = this.csharpierVersionByDirectory.get(directory);
        }

        if (version == null || !this.csharpierProcessesByVersion.containsKey(version)) {
            // this shouldn't really happen, but just in case
            return NullCSharpierProcess.Instance;
        }

        return this.csharpierProcessesByVersion.get(version);
    }

    private String getCSharpierVersion(String directoryThatContainsFile) {
        var currentDirectory = Path.of(directoryThatContainsFile);
        try {
            while (true) {
                var csProjVersion = this.FindVersionInCsProj(currentDirectory);
                if (csProjVersion != null)
                {
                    return csProjVersion;
                }

                var configPath = Path.of(currentDirectory.toString(), ".config/dotnet-tools.json");
                var dotnetToolsPath = configPath.toString();
                var file = new File(dotnetToolsPath);
                this.logger.debug("Looking for " + dotnetToolsPath);
                if (file.exists()) {
                    var data = new String(Files.readAllBytes(configPath));
                    var configData = new Gson().fromJson(data, JsonObject.class);
                    var tools = configData.getAsJsonObject("tools");
                    if (tools != null) {
                        var csharpier = tools.getAsJsonObject("csharpier");
                        if (csharpier != null) {
                            var version = csharpier.get("version").getAsString();
                            if (version != null) {
                                this.logger.debug("Found version " + version + " in " + dotnetToolsPath);
                                return version;
                            }
                        }
                    }
                }

                if (currentDirectory.getParent() == null) {
                    break;
                }
                currentDirectory = currentDirectory.getParent();
            }
        } catch (Exception ex) {
            this.logger.error(ex);
        }

        this.logger.debug(
                "Unable to find dotnet-tools.json, falling back to running dotnet csharpier --version"
        );

        Map<String, String> env = new HashMap<>();
        env.put("DOTNET_NOLOGO", "1");

        var command = new String[]{"dotnet", "csharpier", "--version"};
        var version = ProcessHelper.ExecuteCommand(command, env, new File(directoryThatContainsFile));

        this.logger.debug("dotnet csharpier --version output: " + version);

        return version == null ? "" : version;
    }

    private String FindVersionInCsProj(Path currentDirectory) {
        for (var pathToCsProj : currentDirectory.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".csproj"))) {

            try {
                var xmlDocument = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder()
                    .parse(pathToCsProj);

                var selector = XPathFactory.newInstance().newXPath();
                var node = (Node) selector.compile("//PackageReference[@Include='CSharpier.MsBuild']").evaluate(xmlDocument, XPathConstants.NODE);
                if (node == null) {
                    continue;
                }

                var versionOfMsBuildPackage = node.getAttributes().getNamedItem("Version").getNodeValue();
                if (versionOfMsBuildPackage != null) {
                    this.logger.debug("Found version " + versionOfMsBuildPackage + " in " + pathToCsProj);
                    return versionOfMsBuildPackage;
                }
            } catch (Exception e) {
                this.logger.warn("The csproj at " + pathToCsProj + " failed to load with the following exception " + e.getMessage());
            }
        }

        return null;
    }

    private ICSharpierProcess setupCSharpierProcess(String directory, String version) {
        if (version == null || version.equals("")) {
            return NullCSharpierProcess.Instance;
        }

        try {
            this.customPathInstaller.ensureVersionInstalled(version);
            var customPath = this.customPathInstaller.getPathForVersion(version);

            this.logger.debug("Adding new version " + version + " process for " + directory);

            // ComparableVersion was unhappy in rider 2023, this code should probably just go away
            // but there are still 0.12 and 0.14 downloads happening
            var installedVersion = version.split("\\.");
            var versionWeCareAbout = Integer.parseInt(installedVersion[1]);

            if (versionWeCareAbout < 12) {
                if (!this.warnedForOldVersion) {
                    var content = "Please upgrade to CSharpier >= 0.12.0 for bug fixes and improved formatting speed.";
                    NotificationGroupManager.getInstance().getNotificationGroup("CSharpier")
                            .createNotification(content, NotificationType.INFORMATION)
                            .notify(this.project);

                    this.warnedForOldVersion = true;
                }


                return new CSharpierProcessSingleFile(customPath);
            }

            var useUtf8 = versionWeCareAbout >= 14;

            return new CSharpierProcessPipeMultipleFiles(customPath, useUtf8);

        } catch (Exception ex) {
            this.logger.error(ex);
        }

        return NullCSharpierProcess.Instance;
    }

    @Override
    public void dispose() {
        this.killRunningProcesses();
    }

    public void killRunningProcesses() {
        for (var key : this.csharpierProcessesByVersion.keySet()) {
            this.logger.debug(
                    "disposing of process for version " + (key == "" ? "null" : key)
            );
            this.csharpierProcessesByVersion.get(key).dispose();
        }
        this.lastWarmedByDirectory.clear();
        this.csharpierVersionByDirectory.clear();
        this.csharpierProcessesByVersion.clear();
    }
}
