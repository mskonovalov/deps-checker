package com.touwolf.plugin.idea.depschecker.gradle;

import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class GradleTests
{
    @Test
    public void formalDependenciesTest()
    {
        String group = "dep.group";
        String name = "dep.name";
        String version = "dep.version";
        int times = 2;
        String content = "dependencies {\n";
        for (int i = 0; i < times; i++)
        {
            content += "compile group: '" + group + i + "', name: '" + name + i + "', version: '" + version + i + "'\n";
        }
        content += "}";
        GradleBuild build = new GradleBuild(Arrays.asList(content.split("\n")));
        Assert.assertEquals(times, build.getDependencies().size());
        for (int i = 0; i < times; i++)
        {
            GradleDependency dependency = build.getDependencies().get(i);
            Assert.assertEquals("compile", dependency.getType());
            Assert.assertEquals(group + i, dependency.getGroup());
            Assert.assertEquals(name + i, dependency.getName());
            Assert.assertEquals(version + i, dependency.getVersion());
        }
        String newVersion = "new.version";
        int index = 1;
        build.upgradeDependency(group + index, name + index, newVersion);
        String depLine = "compile group: '" + group + index + "', name: '" + name + index + "', version: '" + newVersion + "'";
        Assert.assertEquals(depLine, build.getContent().get(index + 1));
        GradleDependency dependency = build.getDependencies().get(index);
        Assert.assertEquals(newVersion, dependency.getVersion());
    }
}
