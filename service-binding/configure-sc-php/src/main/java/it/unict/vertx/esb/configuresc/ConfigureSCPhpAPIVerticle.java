package it.unict.vertx.esb.configuresc;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.packet.configure.ConfigureSC;

public class ConfigureSCPhpAPIVerticle extends AbstractVerticle implements ConfigureSC {
	
	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
						
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per configurare il Service Component
		router.post("/sc/configure").handler(this::configure);
		 
		vertx.createHttpServer().requestHandler(router::accept)
			.listen(config().getInteger("http.port"), ar -> {
	         if (ar.succeeded()) {
	        	 System.out.println("Server started");
	         } else {
	        	 System.out.println("Cannot start the server: " + ar.cause());
	         }
	      });
		
	}	

	@Override
	public void configure(RoutingContext routingContext) {
		
		ConfigureSC.Status mappedStatus = ConfigureSC.Status.OK;
		int responseCode = 200;
		
		JsonObject responseBody = new JsonObject();
		responseBody.put("status", mappedStatus.value());
		responseBody.put("message", "");
		
		routingContext.response()
			.setStatusCode(responseCode)
		    .putHeader("content-type", "application/json; charset=utf-8")
		    .end(responseBody.encode());		
	}
	

}
