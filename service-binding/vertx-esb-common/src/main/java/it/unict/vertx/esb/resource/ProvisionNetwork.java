package it.unict.vertx.esb.resource;

import io.vertx.ext.web.RoutingContext;

public interface ProvisionNetwork {
	
	public void createNetwork(RoutingContext routingContext);
	
	public void checkNetwork(RoutingContext routingContext);

}
