package it.unict.vertx.esb.addstorage;

import java.util.Map;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.openstack.OSFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.resource.AddStorage;

public class AddStorageOpenstackAPIVerticle extends AbstractVerticle implements AddStorage {
	
	private String identityUri, storageUri;
	private String domainId, username, password;

	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		storageUri = config().getString("storage.uri");
		identityUri = config().getString("identity.uri");
		
		domainId = config().getString("domain.id");
		username = config().getString("username");
		password = config().getString("password");
		
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per creare volumi e controllarne lo stato
		router.post("/volumes").handler(this::createStorage);
		router.get("/volumes/:id").handler(this::checkStorage);
		 
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
	public void createStorage(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();
		String description = (String) properties.get("description");
		int size = Integer.parseInt(((String)properties.get("size")).split(" ")[0]);
		
		// API per l'autenticazione
		OSClientV3 os = OSFactory.builderV3()
                .endpoint(identityUri)
                .credentials(username, password, Identifier.byId(domainId))
                .authenticate();
		
		/* Creazione del volume:
		 * 
		 * - name
		 * - description
		 * - size (GiB)
		 */
				
		Volume volume = os.blockStorage().volumes()
				.create(Builders.volume()
					.name(name)
					.description(description)
					.size(size)	
					.build()
				);

		// Recupero dell'id e dello stato (e.g. AVAILABLE, CREATING, ERROR)
		String id = volume.getId();
		Volume.Status status = volume.getStatus();
		
		int responseCode = 202;
		JsonObject responseBody = new JsonObject();
		responseBody.put("id", id);
		
		if(status == Volume.Status.ERROR)
			responseCode = 500;
		
		routingContext.response()
	      .setStatusCode(responseCode)
	      .putHeader("content-type", "application/json; charset=utf-8")
	      .end(responseBody.encode());

	}

	@Override
	public void checkStorage(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		String id = routingContext.request().getParam("id");
		
		// API per l'autenticazione
		OSClientV3 os = OSFactory.builderV3()
                .endpoint(identityUri)
                .credentials(username, password, Identifier.byId(domainId))
                .authenticate();
		
		// Recupero del volume
		Volume volume = os.blockStorage().volumes().get(id);
		Volume.Status status = volume.getStatus();
		
		AddStorage.Status mappedStatus = AddStorage.Status.UNRECOGNIZED;
		JsonObject responseBody = new JsonObject();
		int responseCode = 200;
		
		switch(status) {
		case AVAILABLE:
			mappedStatus = AddStorage.Status.OK;
			break;
		case CREATING:
			mappedStatus = AddStorage.Status.WIP;
			break;
		case ERROR:
			mappedStatus = AddStorage.Status.ERROR;
			responseCode = 500;
			break;
		}
		
		responseBody.put("status", mappedStatus.value());
		
		routingContext.response()
	      .setStatusCode(responseCode)
	      .putHeader("content-type", "application/json; charset=utf-8")
	      .end(responseBody.encode());
		
	}

}
