# dashboard

The Dashboard is a web application running a Vue.js frontend on top of a PHP Laravel backend. 

## Description

The Dashboard incorporates two components of TORCH:

* The **TOSCA Modeller**, whch is a Vue.js component available [here](resources/js/components/ToscaGraphModeler.vue).

* The **TOSCA Processor**, which is a Python-based component available [here](public/json4tosca-parser).

## Requirements

You need the following dependencies to run the dashboard:

* PHP 7.2
* [git](https://git-scm.com/)
* [composer](https://getcomposer.org/)
* [node](https://nodejs.org/) and [npm](https://www.npmjs.com/)

Also check that `php-sqlite PDO` is installed and edit the `php.ini` file to uncomment the line: 

```;extension=pdo_sqlite.so``` 

by removing the semicolon.

## Configuration

The following parameters need to be configured in the `.env` file:

* `BPMN_ENGINE` : with the Flowable endpoint 
* `SERVICE_BROKER_URI` : with the Service Broker endpoint

For simplicity, the database herein adopted is an SQLite instance, but feel free to change it through the `DB_CONNECTION` parameter.

## Instructions

Git clone the repo.

Open the console and `cd` to the dashboard directory.

Run the following snippet:

```bash
composer install
npm install
php artisan key:generate
php artisan migrate:install
php artisan migrate
php artisan storage:link
npm run development
```

## Run

In order to start the Dashboard, `cd` to the dashboard directory and run the following command:

```bash
php artisan serve
```


