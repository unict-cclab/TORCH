package it.unict.vertx.esb.common.translator.utils;

import java.util.List;
import java.util.Map;

public class Container implements Entity {

    private String name;
    private String image;
    private List<Volume> volumes;
    private String configuration_script;
    private Service ext_requirements;
    private Map<String, String> properties;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    private String category;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Volume> volumes) {
        this.volumes = volumes;
    }

    public String getConfiguration_script() {
        return configuration_script;
    }

    public void setConfiguration_script(String configuration_script) {
        this.configuration_script = configuration_script;
    }

    public Service getExt_requirements() {
        return ext_requirements;
    }

    public void setExt_requirements(Service ext_requirements) {
        this.ext_requirements = ext_requirements;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
