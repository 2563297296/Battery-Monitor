#!/bin/sh
DIR=`dirname "$0"`
exec "$DIR"/gradle/wrapper/gradle-wrapper.jar "$@"