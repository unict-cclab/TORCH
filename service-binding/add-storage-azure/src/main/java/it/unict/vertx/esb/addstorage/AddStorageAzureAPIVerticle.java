package it.unict.vertx.esb.addstorage;

import java.io.File;
import java.util.Map;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.Disk;
import com.microsoft.rest.LogLevel;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.resource.AddStorage;

public class AddStorageAzureAPIVerticle extends AbstractVerticle implements AddStorage {
	
	private String region, resourceGroup;
	private String credentialsFilePath;
	
	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione		
		credentialsFilePath = System.getProperty("user.dir") + config().getString("credentials.file");
		
		region = config().getString("region");
		resourceGroup = config().getString("resource.group");
		
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
		
        File credentialsFile = new File(credentialsFilePath);
        JsonObject responseBody = new JsonObject();
        int responseCode = 202;
        
		try {
	        AzureTokenCredentials credentials = ApplicationTokenCredentials.fromFile(credentialsFile);

	        //Autenticazione
			Azure azure = Azure
			        .configure()
			        .withLogLevel(LogLevel.NONE)
			        .authenticate(credentials)
			        .withDefaultSubscription();
			
            Disk disk = azure.disks()
                    .define(name)
                    .withRegion(region)
                    .withExistingResourceGroup(resourceGroup)
                    .withData()
                    .withSizeInGB(size)
                    .create();

            String id = disk.id().substring(1).replace("/", ";");//.replace("/","%2F");
            responseBody.put("id", id);

            routingContext.response()
                    .setStatusCode(responseCode)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(responseBody.encode());			
			
		} catch (Exception e) {
			e.printStackTrace();
			responseCode = 500;
			
            routingContext.response()
            .setStatusCode(responseCode)
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(responseBody.encode());
		}		
			
	}

	@Override
	public void checkStorage(RoutingContext routingContext) {
		
		// Recupero dei parametri della richiesta
		String id = routingContext.request().getParam("id").replace(";", "/");		

        File credentialsFile = new File(credentialsFilePath);
		AddStorage.Status mappedStatus = AddStorage.Status.UNRECOGNIZED;        
        JsonObject responseBody = new JsonObject();
        int responseCode = 200;		
		
		try {
  	        AzureTokenCredentials credentials = ApplicationTokenCredentials.fromFile(credentialsFile);

			//Autenticazione
  			Azure azure = Azure
  			        .configure()
  			        .withLogLevel(LogLevel.NONE)
  			        .authenticate(credentials)
  			        .withDefaultSubscription();
  			
            Disk checkDisk = azure.disks().getById(id);
            String provisioningState= checkDisk.inner().provisioningState();
            String diskState = checkDisk.inner().diskState().toString();
            
            switch (provisioningState){
                case "Succeeded":
                    switch (diskState){
                        case "Attached":
                        case "Unattached":
                            mappedStatus = AddStorage.Status.OK;
                            break;
                    }
                    break;
                case "failed/InternalOperationError":
                    mappedStatus = AddStorage.Status.ERROR;
                    responseCode = 500;
                    break;
                case "creating":
                    mappedStatus = AddStorage.Status.WIP;
                    break;
            }  			
  			
  			responseBody.put("status", mappedStatus.value());
  			
  			routingContext.response()
  		      .setStatusCode(responseCode)
  		      .putHeader("content-type", "application/json; charset=utf-8")
  		      .end(responseBody.encode());  			
  			
		} catch (Exception e) {
			e.printStackTrace();
			responseCode = 500;
			
            routingContext.response()
            .setStatusCode(responseCode)
            .putHeader("content-type", "application/json; charset=utf-8")
            .end(responseBody.encode());				
		}
	}

}
