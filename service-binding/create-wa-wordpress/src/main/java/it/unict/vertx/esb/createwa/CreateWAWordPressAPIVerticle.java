package it.unict.vertx.esb.createwa;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.packet.create.CreateWA;

public class CreateWAWordPressAPIVerticle extends AbstractVerticle implements CreateWA {
	
	private String keyName, scriptName;

	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		keyName = System.getProperty("user.dir") + config().getString("key.name");
		scriptName = System.getProperty("user.dir") + config().getString("script.name");
				
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per installare WordPress
		router.post("/wa/create").handler(this::create);
		 
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
	public void create(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();
		
		Set<String> usr = properties.keySet().stream()
				.filter(s -> s.matches("^appserver\\d?\\.username"))
				.collect(Collectors.toSet());
		
		Set<String> addr = properties.keySet().stream()
				.filter(s -> s.matches("^appserver\\d?\\.address"))
				.collect(Collectors.toSet());
		
		String appUsername = (String) properties.get(usr.toArray()[0]);
		String appAddress = (String) properties.get(addr.toArray()[0]);
		
		//String appUsername = (String) properties.get("appserver.username");
		//String appAddress = (String) properties.get("appserver.address");
		String zipUrl = (String) properties.get("zip.url");
		
		System.out.println("appUsername: " + appUsername + "\tappAddress: " + appAddress + "\tzipUrl: " + zipUrl);
		
		String command = "ssh -o 'UserKnownHostsFile=/dev/null' -o 'StrictHostKeyChecking=no' -i " 
				+ keyName + " " + appUsername + "@" + appAddress + " 'bash -s' < " 
				+ scriptName + " '" + zipUrl + "' | tail -n 1";
		
		vertx.executeBlocking(future -> {
			String result = executeCommand(command);
			future.complete(result);
		}, res -> {
			if (res.succeeded()) {
				JsonObject output = new JsonObject((String) res.result());
				String code = output.getString("code");
				String message = output.getString("message");
				
				CreateWA.Status mappedStatus = CreateWA.Status.OK; 
				int responseCode = 200;
				
				if (!code.equals("0")) {
					responseCode = 500;
					mappedStatus = CreateWA.Status.ERROR;
				}
			
				JsonObject responseBody = new JsonObject();
				responseBody.put("status", mappedStatus.value());
				responseBody.put("message", message);
				
				routingContext.response()
					.setStatusCode(responseCode)
				    .putHeader("content-type", "application/json; charset=utf-8")
				    .end(responseBody.encode());
			}
		});

	}
	
	private String executeCommand(String command) {
		StringBuffer output = new StringBuffer();
		
		try {
			Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});			
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line);
			}
			
			process.waitFor();
			reader.close();
			System.out.println("output: " + output.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
//		JsonObject response = new JsonObject();
//		response.put("code", "0");
//		response.put("message", "{\"zip.url\":\"https://wordpress.org/latest.zip\"}");
		
		return output.toString();
//		return response.encode();
		
	}

}
