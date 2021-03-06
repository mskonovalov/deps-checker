package com.touwolf.plugin.idea.depschecker.helper;

import com.intellij.openapi.vfs.VirtualFile;
import com.touwolf.plugin.idea.depschecker.model.DependencyInfo;
import com.touwolf.plugin.idea.depschecker.model.PomInfo;
import com.touwolf.plugin.idea.depschecker.rest.RestClient;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MavenHelper
{
    private static final Logger LOG = Logger.getLogger(MavenHelper.class.getName());

    private static final String MAVEN_API_URL = "http://search.maven.org/solrsearch/select?q=g:\"GROUP\"+AND+a:\"ARTIFACT\"&rows=5&wt=json";

    public static final String UNKNOWN_VERSION = "?";

    @NotNull
    public static String findLatestVersion(@NotNull String groupId, @NotNull String artifactId)
    {
        String url = MAVEN_API_URL
            .replace("GROUP", groupId)
            .replace("ARTIFACT", artifactId);
        Map response = RestClient.get(url, Map.class);
        if (response != null)
        {
            List docs = traverseMap(response, List.class, "response", "docs");
            if (docs != null && !docs.isEmpty() &&
                Map.class.isAssignableFrom(docs.get(0).getClass()))
            {
                Map doc = (Map) docs.get(0);
                Object latestVersion = doc.get("latestVersion");
                if (latestVersion != null)
                {
                    return latestVersion.toString();
                }
            }
        }
        return UNKNOWN_VERSION;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T> T traverseMap(@NotNull Map map, @NotNull Class<T> finalCls, @NotNull String... keys)
    {
        Map current = map;
        for (int i = 0; i < keys.length - 1; i++)
        {
            Object currentValue = current.get(keys[i]);
            if (currentValue == null || !Map.class.isAssignableFrom(currentValue.getClass()))
            {
                current = null;
                break;
            }
            else
            {
                current = (Map) currentValue;
            }
        }
        if (current != null)
        {
            Object result = current.get(keys[keys.length - 1]);
            if (result != null && finalCls.isAssignableFrom(result.getClass()))
            {
                return (T) result;
            }
        }
        return null;
    }

    @NotNull
    public static List<PomInfo> findPomInfos(@NotNull VirtualFile baseDir)
    {
        List<PomInfo> poms = new LinkedList<>();
        List<VirtualFile> pomFiles = VirtualFileHelper.findFiles(baseDir, "pom.xml");
        pomFiles.forEach(pomFile ->
        {
            PomInfo pom = parsePom(pomFile);
            if (pom != null)
            {
                poms.add(pom);
            }
        });
        return poms;
    }

    @Nullable
    private static PomInfo parsePom(@NotNull VirtualFile file)
    {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try
        {
            Model model = reader.read(file.getInputStream());
            Model parent = null;
            if (model.getParent() != null && file.getParent() != null && file.getParent().getParent() != null)
            {
                VirtualFile parentDirFile = file.getParent().getParent();
                VirtualFile parentFile = parentDirFile.findChild("pom.xml");
                if (parentFile != null)
                {
                    parent = reader.read(parentFile.getInputStream());
                }
            }
            return PomInfo.parse(model, parent);
        }
        catch (IOException | XmlPullParserException ex)
        {
            LOG.log(Level.SEVERE, ex.getMessage());
            return null;
        }
    }

    public static void upgradeDependency(@NotNull VirtualFile baseDir, @NotNull DependencyInfo dependencyInfo)
    {
        List<VirtualFile> pomFiles = VirtualFileHelper.findFiles(baseDir, "pom.xml");
        MavenXpp3Reader reader = new MavenXpp3Reader();
        MavenXpp3Writer writer = new MavenXpp3Writer();
        pomFiles.forEach(file ->
        {
            try
            {
                Model model = reader.read(file.getInputStream());
                boolean upgraded = upgradeDependency(dependencyInfo, model.getDependencies());
                if (model.getDependencyManagement() != null)
                {
                    upgraded |= upgradeDependency(dependencyInfo, model.getDependencyManagement().getDependencies());
                }
                if (upgraded)
                {
                    Writer contentWriter = new StringWriter();
                    writer.write(contentWriter, model);
                    contentWriter.flush();
                    VirtualFileHelper.save(file, contentWriter.toString());
                }
            }
            catch (IOException | XmlPullParserException ex)
            {
                LOG.log(Level.SEVERE, ex.getMessage());
            }
        });
    }

    private static boolean upgradeDependency(DependencyInfo dependencyInfo, List<Dependency> dependencies)
    {
        for (Dependency dependency : dependencies)
        {
            if (dependency.getGroupId().equals(dependencyInfo.getGroupId()) &&
                dependency.getArtifactId().equals(dependencyInfo.getArtifactId()))
            {
                dependency.setVersion(dependencyInfo.getLatestVersion());
                return true;
            }
        }
        return false;
    }
}
