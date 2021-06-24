package it.unict.vertx.esb.instantiateswarmcluster;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.Address;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.magnum.Carequest;
import org.openstack4j.model.magnum.Cluster;
import org.openstack4j.model.magnum.Clustertemplate;
import org.openstack4j.openstack.OSFactory;
import org.openstack4j.openstack.magnum.MagnumCarequest;
import org.openstack4j.openstack.magnum.MagnumCluster;
import org.openstack4j.openstack.magnum.MagnumClustertemplate;

//IMPORTS FOR CERTIFICATES
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import it.unict.vertx.esb.resource.InstantiateCluster;

public class InstantiateSwarmClusterOpenstackAPIVerticle extends AbstractVerticle implements InstantiateCluster {

	private String identityUri;
	private String projectId, domainId, username, password;
	private String imageName, keyName, networkName;
	private String templateName, dns, masterFlavor, minionFlavor, coe, networkDriver;
	private String clusterName, dockerStorageDriver, volumeDriver;
	private int masterCount, nodeCount, dockerVolumeSize;
	private boolean tlsDisabled;
	
	@Override
	  public void start(Future<Void> future) throws Exception {
		super.start();
		
		// Inizializzazione
		identityUri = config().getString("identity.uri");
		domainId = config().getString("domain.id");
		projectId = config().getString("project.id");
		username = config().getString("username");
		password = config().getString("password");
		
		// Parametri per cluster template
		imageName = config().getString("image.name");
		keyName = config().getString("key.name");
		networkName = config().getString("network.name");
		templateName = config().getString("template.name");
		dns = config().getString("dns");
		masterFlavor = config().getString("master.flavor");
		minionFlavor = config().getString("minion.flavor");
		coe = config().getString("coe");
		networkDriver = config().getString("network.driver");
		dockerVolumeSize = config().getInteger("docker.volume.size");
		dockerStorageDriver = config().getString("docker.storage.driver");
		volumeDriver = config().getString("volume.driver");
		tlsDisabled = config().getBoolean("tls.disabled");
		clusterName = config().getString("cluster.name");
		masterCount = config().getInteger("master.count");
		nodeCount = config().getInteger("node.count");
		
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());	 
		 
		// API per creare istanze e controllarne lo stato
		router.post("/clusters").handler(this::createCluster);
		router.get("/clusters/:id").handler(this::checkCluster);
		 
		vertx.createHttpServer().requestHandler(router::accept)
			.listen(config().getInteger("http.port"), ar -> {
	         if (ar.succeeded()) {
	        	 System.out.println("Cluster started");
	         } else {
	        	 System.out.println("Cannot start the cluster: " + ar.cause());
	         }
	      });
		
	}
	
	@Override
	public void createCluster(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		JsonObject requestBody = routingContext.getBodyAsJson();
		String name = requestBody.getString("name");
		Map<String, Object> properties = requestBody.getJsonObject("properties").getMap();

		if (properties.get("platform").equals("kubernetes")) return;

		// API per l'autenticazione
		OSClientV3 os = OSFactory.builderV3()
                .endpoint(identityUri)
                .credentials(username, password, Identifier.byId(domainId))
                .scopeToProject(Identifier.byId(projectId))
                .authenticate();
		
		Clustertemplate clusterTemplateBuild = MagnumClustertemplate.builder()
				.name(templateName)
				.imageId(imageName)
				.externalNetworkId(networkName)
				.dnsNameserver(dns)
				.masterFlavorId(masterFlavor)
				.flavorId(minionFlavor)
				.coe(coe)
				.networkDriver(networkDriver)
				.dockerVolumeSize(dockerVolumeSize)
				.dockerStorageDriver(dockerStorageDriver)
				.volumeDriver(volumeDriver)
				.keypairId(keyName)
				.tlsDisabled(tlsDisabled)
				.build();

		// Creazione del template
		Clustertemplate clusterTemplate = os.magnum().createClustertemplate(clusterTemplateBuild);

		Cluster clusterBuild = MagnumCluster.builder()
				.name(clusterName)
				.clusterTemplateId(clusterTemplate.getUuid())
				.masterCount(masterCount)
				.nodeCount(nodeCount)
				.build();

		Cluster cluster = os.magnum().createCluster(clusterBuild);

		// Recupero dell'id
		String id = cluster.getUuid();
		
		// Recupero dello stato
		cluster = os.magnum().listClusters().stream()
									.filter(c -> c.getUuid().equals(id))
									.collect(Collectors.toList()).get(0);
		String status = cluster.getStatus();
		
		JsonObject responseBody = new JsonObject();
		int responseCode = 202;
		
		if(status.equals("CREATE_FAILED")) {
//			Fault fault = cluster.get.getFault();
			
			responseBody.put("message", "Failed to create the cluster");// fault.getMessage());
			responseBody.put("details", "For further details ask the OpenStack manager");// fault.getDetails());
			responseCode =  500; // TODO: Maybe 500? There is no cluster.getFault() -> Then there is no fault.getCode();
		} else {
			responseBody.put("id", id);
		}
		
		routingContext.response()
	      .setStatusCode(responseCode)
	      .putHeader("content-type", "application/json; charset=utf-8")
	      .end(responseBody.encode());
		
	}

	@Override
	public void checkCluster(RoutingContext routingContext) {
		// Recupero dei parametri della richiesta
		String id = routingContext.request().getParam("id");
		
		// API per l'autenticazione
		OSClientV3 os = OSFactory.builderV3()
				.endpoint(identityUri)
				.credentials(username, password, Identifier.byId(domainId))
				.scopeToProject(Identifier.byId(projectId))
				.authenticate();
				
		// Recupero del cluster
		Cluster cluster = os.magnum().listClusters().stream()
									.filter(c -> c.getUuid().equals(id))
									.collect(Collectors.toList()).get(0);
		String status = cluster.getStatus();
		
		InstantiateCluster.Status mappedStatus = InstantiateCluster.Status.UNRECOGNIZED;
		JsonObject responseBody = new JsonObject();
		int responseCode = 200;
				
		switch(status) {
		case "CREATE_COMPLETE":
			mappedStatus = InstantiateCluster.Status.OK;
			responseBody.put("status", mappedStatus.value());

			// TODO: get address in a cleaner way (if possible)
			List<Server> servers = os.compute().servers().list().stream()
					.filter(server -> server.getName().contains(cluster.getName()) && server.getName().contains("master"))
					.collect(Collectors.toList());
					    
			Address masterAddress = servers.get(0).getAddresses().getAddresses().get("").stream()
			.filter(address -> address.getType().equalsIgnoreCase("floating")).collect(Collectors.toList()).get(0);
			
			responseBody.put("endpoint", "http://" + masterAddress.getAddr() + ":2375");
			if (!tlsDisabled) {
				try {
					Map<String, String> certificates = generateCertificates(os, id);

					// CERTIFICATES
					responseBody.put("ca", certificates.get("ca"));
					responseBody.put("cert", certificates.get("cert"));
					responseBody.put("key", certificates.get("key"));
				} catch(Exception e)
				{
					mappedStatus = InstantiateCluster.Status.ERROR;
					responseBody.put("status", mappedStatus.value());
					responseBody.put("message", "Failed to complete cluster creation");
					responseBody.put("details", "Error in certificates generation");
					responseCode =  500;
				}	
			}
			break;
		case "CREATE_IN_PROGRESS":
			mappedStatus = InstantiateCluster.Status.WIP;
			responseBody.put("status", mappedStatus.value());
			break;
		case "CREATE_FAILED":
			// Fault fault = server.getFault();
			mappedStatus = InstantiateCluster.Status.ERROR;
			
			responseBody.put("status", mappedStatus.value());
			responseBody.put("message", "Failed to create the cluster");// fault.getMessage());
			responseBody.put("details", "For further details ask the OpenStack manager");// fault.getDetails());
			responseCode =  500; // TODO: Maybe 500? There is no cluster.getFault() -> Then there is no fault.getCode();
			break;
		default:
			// NOTE: Added this. Maybe worth add in other connectors as well
			responseBody.put("status", mappedStatus.value());
			break;
		}
		
		routingContext.response()
	      .setStatusCode(responseCode)
	      .putHeader("content-type", "application/json; charset=utf-8")
	      .end(responseBody.encode());	
		
	}
	
	private Map<String, String> generateCertificates (OSClientV3 os, String clusterID) throws IOException, OperatorCreationException
	{
		// DO NOT TOUCH
		RSAKeyPairGenerator kpg = new RSAKeyPairGenerator();
		kpg.init(new RSAKeyGenerationParameters(BigInteger.valueOf(0x11), new SecureRandom(), 512, 25));
		AsymmetricCipherKeyPair kp = kpg.generateKeyPair();
		RSAKeyParameters pub = (RSAKeyParameters) kp.getPublic();
		RSAPrivateCrtKeyParameters pvt = (RSAPrivateCrtKeyParameters) kp.getPrivate();

		RSAPrivateKey rsa_pvt = new RSAPrivateKey(pvt.getModulus(), pvt.getPublicExponent(), pvt.getExponent(),pvt.getP(), pvt.getQ(), pvt.getDP(), pvt.getDQ(), pvt.getQInv());
		RSAPublicKey rsa_pub = new RSAPublicKey(pub.getModulus(), pub.getExponent());
		/////////////////

		StringWriter sw = new StringWriter();
		PemWriter pemWriter = new PemWriter(sw);
		try {
			pemWriter.writeObject( new PemObject("RSA PRIVATE KEY", rsa_pvt.getEncoded()));
		} finally {
			pemWriter.close();
		}
		// RSA
		String key = sw.toString();

		////// DO NOT TOUCH /////////////// [create the certificate - version 3 - without extensions]
		AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256WithRSAEncryption");
		AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);

		ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(pvt);
		SubjectPublicKeyInfo pubInfo = new SubjectPublicKeyInfo(new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE), rsa_pub); // NEEDS RSA_PUB

		X500NameBuilder x500NameBld = new X500NameBuilder(BCStyle.INSTANCE);
		x500NameBld.addRDN(BCStyle.CN, "admin");
		x500NameBld.addRDN(BCStyle.O, "system:masters");
		x500NameBld.addRDN(BCStyle.OU, "OpenStack/Magnum");
		x500NameBld.addRDN(BCStyle.C, "US");
		x500NameBld.addRDN(BCStyle.ST, "TX");
		x500NameBld.addRDN(BCStyle.L, "Austin");
		X500Name subject = x500NameBld.build();
		PKCS10CertificationRequestBuilder requestBuilder = new PKCS10CertificationRequestBuilder(subject, pubInfo);
		PKCS10CertificationRequest csr = requestBuilder.build(sigGen);
		PKCS10CertificationRequest req2 = new PKCS10CertificationRequest(csr.getEncoded());
		///////////////////////////////////////

		StringWriter sw2 = new StringWriter();
		PemWriter pemWriter2 = new PemWriter(sw2);
		try {
			pemWriter2.writeObject( new PemObject("CERTIFICATE REQUEST", req2.getEncoded()));
		} finally {
			pemWriter2.close();
		}
		// CSR
		String certificateSigningRequest = sw2.toString();

		Carequest car = MagnumCarequest.builder().bayUuid(clusterID).csr(certificateSigningRequest).build();
		String cert = os.magnum().signCertificate(car).getPem();
		String ca =  os.magnum().getCertificate(clusterID).getPem();

		Map<String, String> certificates = new HashMap<String, String>();
		certificates.put("ca", ca);
		certificates.put("cert", cert);
		certificates.put("key", key);
		
		return certificates;
	}

}
