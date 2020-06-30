package it.unict.vertx.esb.configuredbms;

import io.vertx.core.DeploymentOptions;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class ConfigureDBMSMySqlVerticle extends MicroServiceVerticle {
	
	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(ConfigureDBMSMySqlAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
	    publishHttpEndpoint("configure-dbms-mysql", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Configure DBMS MySql (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }

}
