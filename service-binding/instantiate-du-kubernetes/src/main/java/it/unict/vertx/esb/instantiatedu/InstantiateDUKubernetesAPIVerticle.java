package it.unict.vertx.esb.instantiatedu;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Yaml;
import io.kubernetes.client.util.credentials.ClientCertificateAuthentication;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import it.unict.vertx.esb.common.Translator;
import it.unict.vertx.esb.du.InstantiateDU;

public class InstantiateDUKubernetesAPIVerticle extends AbstractVerticle implements InstantiateDU {

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
		
		boolean sockProxy = config().getBoolean("socks.proxy");
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
		String clusterKey = (String) properties.get("cluster.key");
		String clusterCa = (String) properties.get("cluster.ca");

		Boolean isRetry = (Boolean) properties.get("isRetry");
		
		String deploymentId = "";
		int responseCode;
		JsonObject responseBody = new JsonObject();

		// Creazione della DU, recupero dell'id e dello stato
		try {
			String duJson = requestBody.toString();
			String duYaml = Translator.translate(duJson, K8sTranslatorPlugin.class);

			ApiClient client = ClientBuilder.standard()
					.setBasePath(clusterEndpoint)
					.setAuthentication(new ClientCertificateAuthentication(clusterCert.getBytes("UTF-8"), clusterKey.getBytes("UTF-8")))
					.setVerifyingSsl(true)
					.setCertificateAuthority(clusterCa.getBytes("UTF-8"))
					.build();

			Configuration.setDefaultApiClient(client);

			// If it is a retry delete the DU first
			if (isRetry != null && isRetry == true) deleteDu(duYaml);

			deploymentId = deployDu(duYaml);
			responseBody.put("id", deploymentId);
			responseCode = 202;
		} catch(Exception e)
		{
			e.printStackTrace();
			
			responseBody.put("message", "Errore nella creazione della DU");
			responseBody.put("details", "Couldn't create DU.");
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
		String clusterKey = (String) properties.get("cluster.key");
		String clusterCa = (String) properties.get("cluster.ca");

//		String status; // Pending, Failed, Unknown
		InstantiateDU.Status mappedStatus; //= InstantiateDU.Status.UNRECOGNIZED;
		JsonObject responseBody = new JsonObject();
		int responseCode = 200;

		try {
			ApiClient client = ClientBuilder.standard()
					.setBasePath(clusterEndpoint)
					.setAuthentication(new ClientCertificateAuthentication(clusterCert.getBytes("UTF-8"), clusterKey.getBytes("UTF-8")))
					.setVerifyingSsl(true)
					.setCertificateAuthority(clusterCa.getBytes("UTF-8"))
					.build();

			Configuration.setDefaultApiClient(client);

			AppsV1Api appsApi = new AppsV1Api();

			V1Deployment deployment = appsApi.listNamespacedDeployment("default", null, null, null, null, null, null, null, null)
					.getItems().stream().filter(d -> d.getMetadata().getUid().equals(id)).collect(Collectors.toList()).get(0);

			if (deployment.getStatus() == null || deployment.getStatus().getConditions() == null || deployment.getStatus().getConditions().isEmpty())
			{
				// Kubernetes needs time: WORK IN PROGRESS
				mappedStatus = InstantiateDU.Status.WIP;
				responseBody.put("status", mappedStatus.value());
			}
			else
				for (V1DeploymentCondition dc : deployment.getStatus().getConditions())
				{
					if(dc.getType().equals("Available") && dc.getStatus().equals("True"))
					{
						// SUCCESS
						mappedStatus = InstantiateDU.Status.OK;
						responseBody.put("status", mappedStatus.value());
						break;
					}
					else if(dc.getType().equals("Progressing") && dc.getStatus().equals("False"))
					{
						// FAILED
						mappedStatus = InstantiateDU.Status.ERROR;
						responseBody.put("status", mappedStatus.value());
						responseBody.put("message", "Error creating the DU");
						responseBody.put("details", "Kubernetes: " + dc.getReason());
						responseCode = 500;
						break;
					}
					else
					{
						// WORK IN PROGRESS
						mappedStatus = InstantiateDU.Status.WIP;
						responseBody.put("status", mappedStatus.value());
					}
				}
		} catch (Exception e)
		{
			mappedStatus = InstantiateDU.Status.ERROR;
			responseBody.put("status", mappedStatus);
			responseBody.put("message", "Error while checking DU status");
			responseBody.put("details", e.getStackTrace().toString());
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

	private String deployDu(String duYaml) throws Exception
	{
		String deploymentId = "";

		List<Object> ls = Yaml.loadAll(duYaml);

		AppsV1Api appsApi = new AppsV1Api();
		CoreV1Api coreApi = new CoreV1Api();

		for (Object o: ls)
		{
			if (o.getClass() == V1Deployment.class)
			{
				V1Deployment newDeployment = appsApi.createNamespacedDeployment("default", (V1Deployment)o,null,null,null);
				deploymentId = newDeployment.getMetadata().getUid();
			}

			if (o.getClass() == V1Service.class)
				//V1Service newService =
				coreApi.createNamespacedService("default",(V1Service)o, null ,null,null);
		}
		return deploymentId;
	}

	private void deleteDu(String duYaml) throws Exception
	{
		List<Object> ls = Yaml.loadAll(duYaml);

		AppsV1Api appsApi = new AppsV1Api();
		CoreV1Api coreApi = new CoreV1Api();

		for (Object o: ls)
		{
			if (o.getClass() == V1Deployment.class)
			{
				V1Status status = appsApi.deleteNamespacedDeployment(((V1Deployment) o).getMetadata().getName(), "default",
						null,null,null, null, null, null);
				if (!status.getStatus().equals("Success")) throw new Exception();
			}

			if (o.getClass() == V1Service.class) {
				//V1Service newService =
				V1Status status = coreApi.deleteNamespacedService(((V1Service) o).getMetadata().getName(), "default",
						null, null, null, null, null, null);
				if (!status.getStatus().equals("Success")) throw new Exception();
			}
		}
	}

}
