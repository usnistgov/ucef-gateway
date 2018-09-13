#!/bin/bash
rootdir=`pwd`

# compile gateway
mvn clean install

# generate javadocs
cd $rootdir/gateway-federate
mvn javadoc:javadoc

