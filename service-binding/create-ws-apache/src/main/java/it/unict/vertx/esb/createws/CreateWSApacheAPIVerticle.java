package it.unict.vertx.esb.createws;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.packet.create.CreateWS;

public class CreateWSApacheAPIVerticle extends AbstractVerticle implements CreateWS {
	
	private String keyName, keyPath, scriptName;

	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
//		keyName = System.getProperty("user.dir") + config().getString("key.name");
		keyName = config().getString("key.name");
		keyPath = System.getProperty("user.dir");
		scriptName = System.getProperty("user.dir") + config().getString("script.name");
				
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	
		 
		// API per installare il Web Server
		router.post("/ws/create").handler(this::create);
		 
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
		
		Set<String> key = properties.keySet().stream()
				.filter(s -> s.matches("^appserver\\d?\\.key"))
				.collect(Collectors.toSet());		
		
		String appUsername = (String) properties.get(usr.toArray()[0]);
		String appAddress = (String) properties.get(addr.toArray()[0]);
		String appKey = (String) properties.get(key.toArray()[0]);
		
//		String appUsername = (String) properties.get("appserver.username");
//		String appAddress = (String) properties.get("appserver.address");
				
		vertx.executeBlocking(future -> {			
			String command = "nc -z -w 2 " + appAddress + " 22 > /dev/null";
			int exitValue = executeCommand(command, true);
			
			while(exitValue != 0) {
				try {
					Thread.sleep(5000);
					exitValue = executeCommand(command, true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			File keyFile = null;
			BufferedWriter keyBufferedWriter = null;
			
			 try {
				keyFile = File.createTempFile(keyName, ".pem", new File(keyPath));
//				keyFile.setReadable(true, true);
//				keyFile.setWritable(true, true);
				
				Set<PosixFilePermission> perms = new HashSet<>();
				perms.add(PosixFilePermission.OWNER_READ);
				perms.add(PosixFilePermission.OWNER_WRITE);	
				
				Files.setPosixFilePermissions(keyFile.toPath(), perms);					
				
				keyBufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(keyFile), StandardCharsets.UTF_8));
				keyBufferedWriter.write(appKey);
				keyBufferedWriter.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}				
			
			command = "ssh -o 'UserKnownHostsFile=/dev/null' -o 'StrictHostKeyChecking=no' -i " 
					+ keyFile.getAbsolutePath() + " " + appUsername + "@" + appAddress
					+ " 'bash -s' < " + scriptName + " | tail -n 1";		
	
//			command = "ssh -o 'UserKnownHostsFile=/dev/null' -o 'StrictHostKeyChecking=no' -i " 
//					+ keyName + " " + appUsername + "@" + appAddress
//					+ " 'bash -s' < " + scriptName + " | tail -n 1";
						
			String result = executeCommand(command);
			keyFile.delete();
			future.complete(result);
		}, res -> {
			if (res.succeeded()) {
				JsonObject output = new JsonObject((String) res.result());
				String code = output.getString("code");
				String message = output.getString("message");
				
				CreateWS.Status mappedStatus = CreateWS.Status.OK; 
				int responseCode = 200;
				
				if (!code.equals("0")) {
					responseCode = 500;
					mappedStatus = CreateWS.Status.ERROR;
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
			System.out.println("'executeCommand': after 'process.waitFor()'... output = " + output.toString());
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
		return output.toString();
//		return "{\"code\":\"0\",\"message\":\"Apache installation succeeded\"}";
		
	}
	
	private int executeCommand(String command, boolean exit) {
		int exitValue = -1;
		try {
			Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});
			process.waitFor();

			exitValue = process.exitValue();
			System.out.println("'executeCommand': exitValue --> " + exitValue);
		} catch (Exception e) {
			e.printStackTrace();
		}	
		
		return exitValue;
	}

}
