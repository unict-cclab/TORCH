package it.unict.vertx.esb.instantiateswarmcluster;

import io.vertx.core.DeploymentOptions;
import it.unict.vertx.esb.common.MicroServiceVerticle;

public class InstantiateSwarmClusterOpenstackVerticle extends MicroServiceVerticle
{
	@Override
	  public void start() {
	    super.start();
	    
	    // Deploy the verticle with a configuration.
	    vertx.deployVerticle(InstantiateSwarmClusterOpenstackAPIVerticle.class.getName(), new DeploymentOptions().setConfig(config()));

	    // Publish the services in the discovery infrastructure.
//	    publishHttpEndpoint("instantiate-swarm-cluster-openstack", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {
	    publishHttpEndpoint("instantiate-cluster-openstack-swarm", config().getString("host", "localhost"), config().getInteger("http.port", 8080), ar -> {	    
	      if (ar.failed()) {
	        ar.cause().printStackTrace();
	      } else {
	        System.out.println("Instantiate Swarm Cluster Openstack (Rest endpoint) published : " + ar.succeeded());
	      }
	    });
	  }
}
