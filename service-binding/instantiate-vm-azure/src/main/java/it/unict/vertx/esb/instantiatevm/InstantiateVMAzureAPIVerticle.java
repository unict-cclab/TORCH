package it.unict.vertx.esb.instantiatevm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.microsoft.rest.LogLevel;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.arm.utils.SdkContext;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.compute.KnownLinuxVirtualMachineImage;
import com.microsoft.azure.management.compute.PowerState;
import com.microsoft.azure.management.compute.VirtualMachine;
import com.microsoft.azure.management.compute.VirtualMachineSizeTypes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.resource.InstantiateVM;

public class InstantiateVMAzureAPIVerticle extends AbstractVerticle implements InstantiateVM {
	
	private String region, resourceGroup, networkId, subnet;
	private String username, password, image, size;
	private String keyName, credentialsFilePath;
	
	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione		
		credentialsFilePath = System.getProperty("user.dir") + config().getString("credentials.file");
        keyName = config().getString("key.name");
		
		region = config().getString("region");
		resourceGroup = config().getString("resource.group");
		networkId = config().getString("network.id");
		subnet = config().getString("subnet");
		
		image = config().getString("image");
		size = config().getString("size");
        username = config().getString("username");
        password = config().getString("password");
        		
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per creare istanze e controllarne lo stato
		router.post("/vms").handler(this::createVM);
		router.get("/vms/:id").handler(this::checkVM);
		 
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
	public void createVM(RoutingContext routingContext) {
		
		// Recupero i parametri della richiesta
        JsonObject requestBody = routingContext.getBodyAsJson();
        String name = requestBody.getString("name");
        
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
			
			SSHShell.SshPublicPrivateKey sshKeys = SSHShell.generateSSHKeys("", name + "-" + keyName);
			String key = sshKeys.getSshPrivateKey();
			
            VirtualMachine linuxVM = azure.virtualMachines().define(name)
                    .withRegion(region)
                    .withNewResourceGroup(resourceGroup)
                    .withExistingPrimaryNetwork(azure.networks().getById(networkId))
                    .withSubnet(subnet)
                    .withPrimaryPrivateIPAddressDynamic()
                    .withNewPrimaryPublicIPAddress(SdkContext.randomResourceName(name, 20))
                    .withPopularLinuxImage(KnownLinuxVirtualMachineImage.valueOf(image))
                    .withRootUsername(username)
                    .withRootPassword(password)
                    .withSsh(sshKeys.getSshPublicKey())
//                    .withUnmanagedDisks()
                    .withSize(VirtualMachineSizeTypes.fromString(size))
                    .create();
            
            // Escaping slashes
            String id = linuxVM.id().substring(1).replace("/", ";");//.replace("/","%2F");

            String provisioningState = linuxVM.provisioningState();
            // PowerState powerState = linuxVM.powerState();

            if (provisioningState.equals("failed/InternalOperationError")){
                responseBody.put("message", "Failed to create the VM");
                responseBody.put("details", "");
                responseCode = 500;

            } else {
                responseBody.put("id", id);
    			responseBody.put("username", username);
    			responseBody.put("key", key);                
            }
            
			routingContext
				.response()
				.setStatusCode(responseCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(responseBody.encode());
			
		} catch (Exception e) {
			e.printStackTrace();
			
            responseBody.put("message", "Failed to create the VM");
            responseBody.put("details", e.getMessage());
			responseCode = 500;
			
			routingContext
			.response()
			.setStatusCode(responseCode)
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(responseBody.encode());			
		}
		
	}

	@Override
	public void checkVM(RoutingContext routingContext) {		
		// Recupero i parametri della richiesta
		String id = routingContext.request().getParam("id").replace(";", "/");//.replace("%2F", "/");
				
	    File credentialsFile = new File(credentialsFilePath);
		InstantiateVM.Status mappedStatus = InstantiateVM.Status.UNRECOGNIZED;             
        JsonObject responseBody = new JsonObject();
        int responseCode = 200;	    
        
//        vertx.executeBlocking(future -> {
//			try {
//				System.out.println("Inizio attesa...");
//				Thread.sleep(60000);
//				System.out.println("Fine attesa...");
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
//			future.complete();
//        }, res -> {
//			if (res.succeeded()) {
				
//				InstantiateVM.Status mappedStatus = InstantiateVM.Status.UNRECOGNIZED;
//		        int responseCode = 200;				
//				
//				try {
//		  	        AzureTokenCredentials credentials = ApplicationTokenCredentials.fromFile(credentialsFile);
//
//					//Autenticazione
//		  			Azure azure = Azure
//		  			        .configure()
//		  			        .withLogLevel(LogLevel.NONE)
//		  			        .authenticate(credentials)
//		  			        .withDefaultSubscription();
//		  			
//		            VirtualMachine checkVM = azure.virtualMachines().getById(id);
//		            PowerState powerState= checkVM.powerState();
//		            String provisioningState = checkVM.provisioningState();
//
//		            switch (provisioningState) {
//		                case "Succeeded":
//		                    switch (powerState.toString()){
//		                        case "PowerState/starting":
//		                            mappedStatus = InstantiateVM.Status.WIP;
//		                            responseBody.put("status", mappedStatus.value());
//		                            break;
//		                        case "PowerState/running":
//		                            mappedStatus = InstantiateVM.Status.OK;
//		                            responseBody.put("status", mappedStatus.value());
//		                            
//		                            JsonArray addrs = new JsonArray();                         
//		                            JsonObject addr = new JsonObject();
//		                            addr.put("network", checkVM.primaryNetworkInterfaceId());
//		                            
//		                            JsonArray ipAddrs = new JsonArray();
//		                            ipAddrs.add(checkVM.getPrimaryNetworkInterface().primaryIPConfiguration().privateIPAddress());
//		                            ipAddrs.add(checkVM.getPrimaryNetworkInterface().primaryIPConfiguration().getPublicIPAddress().ipAddress());
//		                            
//		                            addr.put("ips", ipAddrs);
//		                            addrs.add(addr);
//		                            
//		                            responseBody.put("addresses", addrs);                       
//		                            break;
//		                    }
//		                    break;
//		                case "failed/InternalOperationError":
//		                    mappedStatus = InstantiateVM.Status.ERROR;
//		                    
//		                    responseBody.put("status", mappedStatus.value());
//		                    responseBody.put("message", "Failed to create the VM");
//		        			responseBody.put("details", "");
//		        			responseCode = 500;
//		                    break;
//		                case "creating":
//		                    mappedStatus = InstantiateVM.Status.WIP;
//		                    responseBody.put("status", mappedStatus.value());
//		                    break;
//		            }
//		            
//					routingContext
//						.response()
//						.setStatusCode(responseCode)
//						.putHeader("content-type", "application/json; charset=utf-8")
//						.end(responseBody.encode());      
//		  			  			
//				} catch (Exception e) {
//					e.printStackTrace();
//					
//					mappedStatus = InstantiateVM.Status.ERROR;
//		            
//		            responseBody.put("status", mappedStatus.value());
//		            responseBody.put("message", "Failed to create the VM");
//					responseBody.put("details", e.getMessage());
//					responseCode = 500;			
//								
//					routingContext
//					.response()
//					.setStatusCode(responseCode)
//					.putHeader("content-type", "application/json; charset=utf-8")
//					.end(responseBody.encode());			
//				}			
//			
//			}
//		});
        
		try {
  	        AzureTokenCredentials credentials = ApplicationTokenCredentials.fromFile(credentialsFile);

			//Autenticazione
  			Azure azure = Azure
  			        .configure()
  			        .withLogLevel(LogLevel.NONE)
  			        .authenticate(credentials)
  			        .withDefaultSubscription();
  			
            VirtualMachine checkVM = azure.virtualMachines().getById(id);
            PowerState powerState= checkVM.powerState();
            String provisioningState = checkVM.provisioningState();

            switch (provisioningState) {
                case "Succeeded":
                    switch (powerState.toString()){
                        case "PowerState/starting":
                            mappedStatus = InstantiateVM.Status.WIP;
                            responseBody.put("status", mappedStatus.value());
                            break;
                        case "PowerState/running":
                            mappedStatus = InstantiateVM.Status.OK;
                            responseBody.put("status", mappedStatus.value());
                            
                            JsonArray addrs = new JsonArray();                         
                            JsonObject addr = new JsonObject();
                            addr.put("network", checkVM.primaryNetworkInterfaceId());
                            
                            JsonArray ipAddrs = new JsonArray();
                            ipAddrs.add(checkVM.getPrimaryNetworkInterface().primaryIPConfiguration().getPublicIPAddress().ipAddress());
                            ipAddrs.add(checkVM.getPrimaryNetworkInterface().primaryIPConfiguration().privateIPAddress());                            
                            
                            addr.put("ips", ipAddrs);
                            addrs.add(addr);
                            
                            responseBody.put("addresses", addrs);                       
                            break;
                    }
                    break;
                case "failed/InternalOperationError":
                    mappedStatus = InstantiateVM.Status.ERROR;
                    
                    responseBody.put("status", mappedStatus.value());
                    responseBody.put("message", "Failed to create the VM");
        			responseBody.put("details", "");
        			responseCode = 500;
                    break;
                case "creating":
                    mappedStatus = InstantiateVM.Status.WIP;
                    responseBody.put("status", mappedStatus.value());
                    break;
            }
            
			routingContext
				.response()
				.setStatusCode(responseCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(responseBody.encode());      
  			  			
		} catch (Exception e) {
			e.printStackTrace();
			
			mappedStatus = InstantiateVM.Status.ERROR;
            
            responseBody.put("status", mappedStatus.value());
            responseBody.put("message", "Failed to create the VM");
			responseBody.put("details", e.getMessage());
			responseCode = 500;			
						
			routingContext
			.response()
			.setStatusCode(responseCode)
			.putHeader("content-type", "application/json; charset=utf-8")
			.end(responseBody.encode());			
		}  			
	}

}
