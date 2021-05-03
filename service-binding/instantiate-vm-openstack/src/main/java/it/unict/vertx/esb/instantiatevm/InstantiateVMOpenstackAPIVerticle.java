package it.unict.vertx.esb.instantiatevm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.openstack.OSFactory;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.Address;
import org.openstack4j.model.compute.Addresses;
import org.openstack4j.model.compute.Fault;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.Image;
import org.openstack4j.model.compute.Keypair;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.Server.Status;
import org.openstack4j.model.compute.ServerCreate;
import org.openstack4j.model.network.Network;

import it.unict.vertx.esb.resource.InstantiateVM;

public class InstantiateVMOpenstackAPIVerticle extends AbstractVerticle implements InstantiateVM {
		
	private String computeUri, identityUri, imageUri, networkUri;
	private String domainId, username, password;
	private String flavorName, imageName, securityGroup, keyName, networkName, adminUsername, adminPassword;
	
	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		computeUri = config().getString("compute.uri");
		identityUri = config().getString("identity.uri");
		imageUri = config().getString("image.uri");
		networkUri = config().getString("network.uri");
		
		domainId = config().getString("domain.id");
		username = config().getString("username");
		password = config().getString("password");
		flavorName = config().getString("flavor.name");
		imageName = config().getString("image.name");
		securityGroup = config().getString("security.group");
		keyName = config().getString("key.name");
		networkName = config().getString("network.name");
		adminUsername = config().getString("admin.username");
		adminPassword = config().getString("admin.password");
		
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
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();
		
		// API per l'autenticazione
		OSClientV3 os = OSFactory.builderV3()
                .endpoint(identityUri)
                .credentials(username, password, Identifier.byId(domainId))
//                .scopeToDomain(Identifier.byId(domainId))
//                .scopeToProject(Identifier.byName("demo"))
                .authenticate();
						
		/* In futuro bisognerà recuperare Flavor ("host") e Image ("os") in base alle properties */				
		List<Flavor> flavors = os.compute().flavors().list().stream()
				.filter(flavor -> flavor.getName().equals(flavorName))
				.collect(Collectors.toList());
			
		List<Image> images = os.compute().images().list().stream()
				.filter(image -> image.getName().equals(imageName))
				.collect(Collectors.toList());

		List<Network> nets = os.networking().network().list().stream()
				.filter(network -> network.getName().equals(networkName))
				.collect(Collectors.toList());
		List<String> networks = Arrays.asList(nets.get(0).getId());
		
		Keypair keyPair = os.compute().keypairs().create(name + "-" + keyName, null);
		String key = keyPair.getPrivateKey();
		
		/* Creazione dell'istanza:
		 * 
		 * - name			
		 * - flavor			
		 * - image			
		 * - network			
		 * - security group	
		 * - key pair	
		 */
						
		ServerCreate serverCreate = Builders.server()
				.name(name)
				.flavor(flavors.get(0))
				.image(images.get(0))
				.networks(networks)
				.addSecurityGroup(securityGroup)
				.keypairName(keyPair.getName())
				.addAdminPass(adminPassword)
				.build();
		
		// Lancio dell'istanza
		Server server = os.compute().servers().boot(serverCreate);
		
		// Recupero dell'id e dello stato (e.g. ACTIVE, BUILD, ERROR)
		String id = server.getId();
		Server.Status status = server.getStatus();
		
		JsonObject responseBody = new JsonObject();
		int responseCode = 202;
		
		if(status == Server.Status.ERROR) {
			Fault fault = server.getFault();
			
			responseBody.put("message", fault.getMessage());
			responseBody.put("details", fault.getDetails());
//			responseCode = fault.getCode();
			responseCode = 500;
		} else {
			responseBody.put("id", id);
			responseBody.put("username", adminUsername);
			responseBody.put("key", key);
		}
		
		routingContext.response()
	      .setStatusCode(responseCode)
	      .putHeader("content-type", "application/json; charset=utf-8")
	      .end(responseBody.encode());
		
	}

	@Override
	public void checkVM(RoutingContext routingContext) {		
		// Recupero dei parametri della richiesta
		String id = routingContext.request().getParam("id");
		
		// API per l'autenticazione
		OSClientV3 os = OSFactory.builderV3()
                .endpoint(identityUri)
                .credentials(username, password, Identifier.byId(domainId))
//                .scopeToDomain(Identifier.byId(domainId))
                .authenticate();
				
		// Recupero del server
		Server server = os.compute().servers().get(id);
		Server.Status status = server.getStatus();
		
		InstantiateVM.Status mappedStatus = InstantiateVM.Status.UNRECOGNIZED; 
		JsonObject responseBody = new JsonObject();
		int responseCode = 200;
				
		switch(status) {
		case ACTIVE:
			mappedStatus = InstantiateVM.Status.OK;
			responseBody.put("status", mappedStatus.value());
//			responseBody.put("username", adminUsername);
			
//			Keypair keyPair = os.compute().keypairs().get(keyName);
//			if(keyPair != null)
//				responseBody.put("key", keyPair.getPrivateKey());
			
			Addresses serverAddresses = server.getAddresses();
		    Map<String, List<? extends Address>> networkAddresses = serverAddresses.getAddresses();
		    
		    if (networkAddresses != null) {
		    	JsonArray addrs = new JsonArray();
		    	for (Entry<String, List<? extends Address>> networkIps : networkAddresses.entrySet()) {
		    		JsonObject addr = new JsonObject();
		    		addr.put("network", networkIps.getKey());
		    		List<? extends Address> ips = networkIps.getValue();
		    		JsonArray ipAddrs = new JsonArray();
		    		for (Address ip: ips) {
		    			ipAddrs.add(ip.getAddr());
		    		}
		    		addr.put("ips", ipAddrs);
		    		addrs.add(addr);
		    	}
		    	
		    	responseBody.put("addresses", addrs);
		    }
			break;
		case BUILD:
			mappedStatus = InstantiateVM.Status.WIP;
			responseBody.put("status", mappedStatus.value());
			break;
		case ERROR:
			Fault fault = server.getFault();
			mappedStatus = InstantiateVM.Status.ERROR;
			
			responseBody.put("status", mappedStatus.value());
			responseBody.put("message", fault.getMessage());
			responseBody.put("details", fault.getDetails());
//			responseCode = fault.getCode();
			responseCode = 500;
			break;
		}
		
		routingContext.response()
	      .setStatusCode(responseCode)
	      .putHeader("content-type", "application/json; charset=utf-8")
	      .end(responseBody.encode());
				
	}

}
