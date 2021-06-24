package it.unict.vertx.esb.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unict.vertx.esb.common.translator.utils.DeploymentUnit;

public class Parser {

    public static DeploymentUnit parse(String duJson) throws Exception {
        // JSON string to Java Object
        ObjectMapper mapper = new ObjectMapper();
        DeploymentUnit du = mapper.readValue(duJson, DeploymentUnit.class);
        return du;
    }
}
