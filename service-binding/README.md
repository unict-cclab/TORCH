# service-binding

This folder contains all the components of the Service Binding layer. The code is written in Java and is structured using a Maven project. 

We provide both the `src` folder, containing the Java implementation code, and the `target` folder, containing the JAR executable files. 

## Requirements

The following dependencies are required:

* Java
* Maven (to recompile the project)

## Configuration

All the Service Connectors can be configured by editing the `conf.json` files available in their respective `src/conf` folders.

## Instructions

You can start each Service Connector by running the following command:

```bash
nohup java -Dvertx.options.blockedThreadCheckInterval=20000000000 -jar $SC/target/$SC-0.0.1-SNAPSHOT-fat.jar &
```

where $SC represents the name of the Service Connector to start.

After you have started all the necessary Connectors you can then start the Service Broker by running:

```bash
nohup java -Dvertx.options.blockedThreadCheckInterval=20000000000 -jar service-broker/target/service-broker-0.0.1-SNAPSHOT-fat.jar &
```