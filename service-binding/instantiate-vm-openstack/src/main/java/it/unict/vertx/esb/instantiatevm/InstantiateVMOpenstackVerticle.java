package it.unict.vertx.esb.instantiatevm;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import it.unict.vertx.esb.instantiatevm.InstantiateVMOpenstackAPIVerticle;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class InstantiateVMOpenstackVerticle extends MicroServiceVerticle {

	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(InstantiateVMOpenstackAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
	    publishHttpEndpoint("instantiate-vm-openstack", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Instantiate VM Openstack (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }

}
