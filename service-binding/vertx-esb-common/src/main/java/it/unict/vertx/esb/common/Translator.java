package it.unict.vertx.esb.common;

//import com.fasterxml.jackson.databind.ObjectMapper;
import it.unict.vertx.esb.common.translator.utils.DeploymentUnit;

import java.lang.reflect.Method;

public class Translator {

    public static String translate(String duJson, Class<? extends TranslatorPlugin> plugin) throws Exception {   
//    	ObjectMapper mapper = new ObjectMapper();
//        DeploymentUnit du = mapper.readValue(duJson, DeploymentUnit.class);
    	
    	// JSON string to Java Object
    	DeploymentUnit du = Parser.parse(duJson);
        
        Method translateDuMethod = plugin.getMethod("translateDu", DeploymentUnit.class);
        Object translatorPlugin = plugin.getDeclaredConstructor(null).newInstance(null);
        String duDescriptionFile = (String) translateDuMethod.invoke(translatorPlugin, du);
        return duDescriptionFile;
    }
}
