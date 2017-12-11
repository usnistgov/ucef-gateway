#!/bin/bash
rootdir=`pwd`

cd $rootdir/GatewayTest_generated
mvn clean install

cd $rootdir/GatewayTest_deployment
mvn clean install

cd $rootdir/ExampleGateway
mvn clean install

