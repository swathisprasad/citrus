/*
 * Copyright 2006-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.admin.model;

import com.consol.citrus.admin.configuration.*;
import com.consol.citrus.admin.exception.CitrusAdminRuntimeException;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.util.FileUtils;
import com.consol.citrus.util.XMLUtils;
import com.consol.citrus.xml.xpath.XPathUtils;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Document;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Project model holding Citrus project related information.
 * @author Christoph Deppisch
 * @since 1.4.1
 */
public class Project {

    private String name;
    private String description;
    private String version;
    private final String projectHome;
    private String basePackage = System.getProperty(PropertyConstants.BASE_PACKAGE, "com.consol.citrus");

    /** Citrus project information as Json file */
    private final String PROJECT_INFO_FILENAME = "citrus-project.info";

    /** List of run configurations */
    private List<RunConfiguration> runConfigurationList = new ArrayList<RunConfiguration>();

    /** Logger */
    private static Logger log = LoggerFactory.getLogger(Project.class);

    /**
     * Default constructor using project home directory.
     * @param projectHome
     */
    public Project(String projectHome) {
        try {
            this.projectHome = new File(projectHome).getCanonicalPath();
        } catch (IOException e) {
            throw new CitrusAdminRuntimeException("Unable to access project home directory", e);
        }
    }

    /**
     * Setup project information from given Json info object. Json object is usually loaded from file system
     * project home.
     * @param projectInfo
     */
    public void setup(JSONObject projectInfo) {
        name = readProperty(projectInfo, "name", "citrus-project");
        version = readProperty(projectInfo, "version", "unknown");
        description = readProperty(projectInfo, "description", "");

        MavenRunConfiguration mavenRunConfiguration = new MavenRunConfiguration();
        mavenRunConfiguration.setId("Maven");
        mavenRunConfiguration.setStandard(true);
        runConfigurationList.add(mavenRunConfiguration);

        ClasspathRunConfiguration classpathRunConfiguration = new ClasspathRunConfiguration();
        classpathRunConfiguration.setId("Classpath");
        runConfigurationList.add(classpathRunConfiguration);
    }

    /**
     * Creates new project info object based on either Maven POM or ANT build file.
     * @return
     */
    public JSONObject createProjectInfo() {
        JSONObject projectInfo = new JSONObject();

        if (isMavenProject()) {
            try {
                String pomXml = FileUtils.readToString(new FileSystemResource(new File(getMavenPomFilePath())));
                SimpleNamespaceContext nsContext = new SimpleNamespaceContext();
                nsContext.bindNamespaceUri("mvn", "http://maven.apache.org/POM/4.0.0");

                Document pomDoc = XMLUtils.parseMessagePayload(pomXml);
                projectInfo.put("basePackage", XPathUtils.evaluateAsString(pomDoc, "/mvn:project/mvn:groupId", nsContext));
                projectInfo.put("name", XPathUtils.evaluateAsString(pomDoc, "/mvn:project/mvn:artifactId", nsContext));
                projectInfo.put("version", XPathUtils.evaluateAsString(pomDoc, "/mvn:project/mvn:properties/mvn:citrus.version", nsContext));
                projectInfo.put("description", XPathUtils.evaluateAsString(pomDoc, "/mvn:project/mvn:description", nsContext));
            } catch (IOException e) {
                throw new CitrusAdminRuntimeException("Unable to open Maven pom.xml file", e);
            }
        } else if (isAntProject()) {
            try {
                String buildXml = FileUtils.readToString(new FileSystemResource(new File(getAntBuildFilePath())));
                SimpleNamespaceContext nsContext = new SimpleNamespaceContext();

                Document buildDoc = XMLUtils.parseMessagePayload(buildXml);
                projectInfo.put("name", XPathUtils.evaluateAsString(buildDoc, "/project/@name", nsContext));
                projectInfo.put("version", XPathUtils.evaluateAsString(buildDoc, "/project/property[@name='citrus.version']/@value", nsContext));
                projectInfo.put("description", XPathUtils.evaluateAsString(buildDoc, "/project/@description", nsContext));
            } catch (IOException e) {
                throw new CitrusAdminRuntimeException("Unable to open ANT build.xml file", e);
            }
        } else {
            projectInfo.put("basePackage", basePackage);
            projectInfo.put("name", name);
            projectInfo.put("version", version);
            projectInfo.put("description", description);
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(getProjectInfoFile());
            fos.write(projectInfo.toJSONString().getBytes());
            fos.flush();
        } catch (FileNotFoundException e) {
            throw new CitrusRuntimeException("Unable to open project info file", e);
        } catch (IOException e) {
            throw new CitrusAdminRuntimeException("Unable to write project info file", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    log.warn("Failed to close project info file", e);
                }
            }
        }

        return projectInfo;
    }

    /**
     * Gets file pointer to project info file in project home directory.
     * @return
     */
    public File getProjectInfoFile() {
        return new File(projectHome + System.getProperty("file.separator") + PROJECT_INFO_FILENAME);
    }

    /**
     * Gets the run configuration.
     * @param id
     * @return
     */
    public RunConfiguration getRunConfiguration(String id) {
        for (RunConfiguration runConfiguration : runConfigurationList) {
            if (runConfiguration.getId().equals(id)) {
                return runConfiguration;
            }
        }

        throw new CitrusAdminRuntimeException("Unknown run configuration: " + id);
    }

    /**
     * Checks ANT project nature by finding the basic build.xml ANT file in project home directory.
     * @return
     */
    public boolean isAntProject() {
        return new File(getAntBuildFilePath()).exists();
    }

    /**
     * Gets the ANT build xml file path.
     * @return
     */
    private String getAntBuildFilePath() {
        return projectHome + System.getProperty("file.separator") + "build.xml";
    }

    /**
     * Checks Maven project nature by finding the basic pom.xml Maven file in project home directory.
     * @return
     */
    public boolean isMavenProject() {
        return new File(getMavenPomFilePath()).exists();
    }

    /**
     * Gets the Maven POM xml file path.
     * @return
     */
    private String getMavenPomFilePath() {
        return projectHome + System.getProperty("file.separator") + "pom.xml";
    }

    /**
     * Reads property if present in given Json info object. Otherwise uses default value.
     * @param projectInfo
     * @param propertyName
     * @param defaultValue
     * @return
     */
    private String readProperty(JSONObject projectInfo, String propertyName, String defaultValue) {
        if (projectInfo.containsKey(propertyName)) {
            return projectInfo.get(propertyName).toString();
        } else {
            return defaultValue;
        }
    }

    /**
     * Gets the project name.
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the project name.
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the Citrus version.
     * @return
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the Citrus version.
     * @param version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Gets the run configuration.
     * @return
     */
    public List<RunConfiguration> getRunConfigurations() {
        return runConfigurationList;
    }

    /**
     * Gets the base package.
     * @return
     */
    public String getBasePackage() {
        return basePackage;
    }

    /**
     * Sets the base package.
     * @param basePackage
     */
    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    /**
     * Gets the project home.
     * @return
     */
    public String getProjectHome() {
        return this.projectHome;
    }

    /**
     * Gets the project description.
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the project description.
     * @param description
     */
    public void setDescription(String description) {
        this.description = description;
    }
}