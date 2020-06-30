package it.unict.vertx.esb.common.translator.utils;

import java.util.List;
import java.util.Map;

public class DeploymentUnit implements Entity {

    private String name;
    private String type;
    private String category;
    private Map<String, List<String>> requirements;
    private Map<String, String> properties;
    private List<Container> containers;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
 
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Map<String, List<String>> getRequirements() {
        return requirements;
    }

    public void setRequirements(Map<String, List<String>> requirements) {
        this.requirements = requirements;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public List<Container> getContainers() {
        return containers;
    }

    public void setContainers(List<Container> containers) {
        this.containers = containers;
    }
}
