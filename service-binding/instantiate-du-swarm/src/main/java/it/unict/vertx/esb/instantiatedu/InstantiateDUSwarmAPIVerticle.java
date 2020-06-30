package it.unict.vertx.esb.instantiatedu;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.*;

import com.spotify.docker.client.messages.swarm.*;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.common.Parser;
import it.unict.vertx.esb.common.translator.utils.Container;
import it.unict.vertx.esb.common.translator.utils.DeploymentUnit;
import it.unict.vertx.esb.du.InstantiateDU;

public class InstantiateDUSwarmAPIVerticle extends AbstractVerticle implements InstantiateDU {
	
	boolean sockProxy;
	
	@Override
	public void start(Future<Void> future) throws Exception {
		super.start();

		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());

		// API per creare DU, controllarne lo stato e configurarle
		router.post("/dus/create").handler(this::createDU);
		router.post("/dus/check").handler(this::checkDU);
		router.post("/dus/configure").handler(this::configureDU);
		
		vertx.createHttpServer().requestHandler(router::accept)
				.listen(config().getInteger("http.port"), ar -> {
					if (ar.succeeded()) {
						System.out.println("Server started");
					} else {
						System.out.println("Cannot start the server: " + ar.cause());
					}
				});

		sockProxy = config().getBoolean("socks.proxy");		
		if (sockProxy) {
			// Proxy settings
			System.getProperties().put("proxySet", config().getString("socks.proxy.set"));
	        System.getProperties().put("socksProxyHost", config().getString("socks.proxy.host"));
	        System.getProperties().put("socksProxyPort", config().getString("socks.proxy.port"));
		}
		
	}

	@Override
	public void createDU(RoutingContext routingContext) {

		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();

		String clusterEndpoint = (String) properties.get("cluster.endpoint");
		String clusterCert = (String) properties.get("cluster.cert");
		String clusterRsa = (String) properties.get("cluster.rsa");
		String clusterPem = (String) properties.get("cluster.pem");
		
		// Boolean isRetry = (Boolean) properties.get("isRetry");
		
		String deploymentId = "";
		int responseCode = 202;
		InstantiateDU.Status mappedStatus;
		JsonObject responseBody = new JsonObject();
		
		try {
			String network = config().getString("network.name");
			String duJson = requestBody.toString();
			DeploymentUnit deploymentUnit = Parser.parse(duJson);

			final DockerClient docker = DefaultDockerClient.builder()
					.readTimeoutMillis(300000)
					.connectTimeoutMillis(300000)
					.connectionPoolSize(1000)
					.useProxy(sockProxy)
					.uri(URI.create(clusterEndpoint))
					.build();
			try {
				// TODO: add parameter to the node to name the network (same can be done with Kubernetes, adding a namespace)
				// NOTE: the paramemeter maybe the name of the app in the dashboard
				docker.inspectNetwork(network);
			} catch (Exception e) {
				NetworkConfig networkConfig = NetworkConfig.builder()
						.checkDuplicate(true)
						.attachable(true)
						.name(network)
						.driver("overlay")
						.ipam(Ipam.builder().driver("default").build())
						.build();
				docker.createNetwork(networkConfig);
			}

			// TODO: Verify if the library allows to deal with multiple containers per service
			Container c = deploymentUnit.getContainers().get(0);

			List<String> envs = new ArrayList<>();
			EndpointSpec.Builder portSpec = EndpointSpec.builder();
			for (Map.Entry<String, String> envVar : c.getProperties().entrySet()) {
				if (envVar.getKey().equals("port") && c.getCategory().equals("wa"))
				{
					String[] ports = envVar.getValue().split(":");
					Integer servicePort = Integer.parseInt(ports[0]);
					Integer targetPort = Integer.parseInt(ports[1]);

					portSpec.addPort(PortConfig.builder()
							.publishedPort(servicePort)
							.targetPort(targetPort)
							.build());
					continue;
				}
				envs.add(envVar.getKey() + "=" + envVar.getValue());
			}

			final TaskSpec taskSpec = TaskSpec.builder()
					.containerSpec(ContainerSpec.builder().image(c.getImage()).env(envs).build())
					.build();

			final ServiceSpec spec = ServiceSpec.builder().name(deploymentUnit.getName())
					.taskTemplate(taskSpec).mode(ServiceMode.withReplicas(1L))
					.endpointSpec(portSpec.build())
					.networks(NetworkAttachmentConfig.builder().target(network).build())
					.build();

			final ServiceCreateResponse response = docker.createService(spec);

			//if (response.warnings() == null)
			//{
			deploymentId = response.id();
			mappedStatus = InstantiateDU.Status.OK;
			responseBody.put("id", deploymentId);
			responseBody.put("status", mappedStatus.value());
			//}
			//else
			//{
			//	mappedStatus = InstantiateDU.Status.ERROR;
			//	responseBody.put("status", mappedStatus);
			//	responseBody.put("message", "Errore nella creazione della DU");
			//	responseBody.put("details", response.warnings().toString());
			//	responseCode = 500;
			//}

		} catch(Exception e)
		{
			mappedStatus = InstantiateDU.Status.ERROR;
			responseBody.put("status", mappedStatus);
			responseBody.put("message", "Errore nella creazione della DU");
			responseBody.put("details", e.getMessage());
			responseCode = 500;
		}

		routingContext.response()
				.setStatusCode(responseCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(responseBody.encode());

	}

	@Override
	public void checkDU(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		// String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();

		String id = (String) properties.get("id");
		String clusterEndpoint = (String) properties.get("cluster.endpoint");
		String clusterCert = (String) properties.get("cluster.cert");
		String clusterRsa = (String) properties.get("cluster.rsa");
		String clusterPem = (String) properties.get("cluster.pem");

		InstantiateDU.Status mappedStatus = InstantiateDU.Status.WIP;
		// DEFAULT is WIP because the service may not show because of network slowness for the first checks
		JsonObject responseBody = new JsonObject();
		responseBody.put("status", mappedStatus.value());
		int responseCode = 200;

		try {

			final DockerClient docker = DefaultDockerClient.builder()
					.readTimeoutMillis(10000)
					.connectTimeoutMillis(10000)
					.connectionPoolSize(1000)
					.useProxy(sockProxy)
					.uri(URI.create(clusterEndpoint))
					.build();

			String duName = docker.inspectService(id).spec().name();

			// created, restarting, running, removing, paused, exited, or dead
			List<Task> taskList = docker.listTasks();
			for (Task t: taskList)
			{
				if (t.serviceId().equals(id))
				{
					if(t.status().state().equals("running") || t.status().state().equals("complete"))
					{
						// SUCCESS
						mappedStatus = InstantiateDU.Status.OK;
						responseBody.put("status", mappedStatus.value());
						break;
					}
					else if (t.status().state().equals("failed") || t.status().state().equals("orphaned"))
					{
						// FAILED
						mappedStatus = InstantiateDU.Status.ERROR;
						responseBody.put("status", mappedStatus.value());
						responseBody.put("message", "Error creating the DU");
						responseBody.put("details", "Swarm: " + t.status().state());
						responseCode = 500;
						break;
					}
					else{
						// IN PROGRESS
						mappedStatus = InstantiateDU.Status.WIP;
						responseBody.put("status", mappedStatus.value());
						break;
					}
				}
			}
		} catch (Exception e)
		{
			mappedStatus = InstantiateDU.Status.ERROR;
			responseBody.put("status", mappedStatus);
			responseBody.put("message", "Error while checking DU status");
			responseBody.put("details", e.getMessage());
			responseCode = 500;
		}

		routingContext.response()
				.setStatusCode(responseCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(responseBody.encode());
	}

	@Override
	public void configureDU(RoutingContext routingContext) {
		
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		String id = requestBody.getString("id");

		String status = "OK";
		JsonObject responseBody = new JsonObject();
		int responseCode = 202;

		if(status.equals("ERROR")) {
			responseBody.put("status", InstantiateDU.Status.ERROR.value());
			responseBody.put("message", "Errore nella configurazione della DU");
			responseBody.put("details", "bla bla bla");
			responseCode = 500;
		} else {
			responseBody.put("status", InstantiateDU.Status.OK.value());
			responseBody.put("message", "Configurazione della DU effettuata con successo");
		}

		routingContext.response()
				.setStatusCode(responseCode)
				.putHeader("content-type", "application/json; charset=utf-8")
				.end(responseBody.encode());
		
	}

}
