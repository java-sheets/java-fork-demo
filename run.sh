#!/usr/bin/env sh

javac ./src/Box.java ./src/Controller.java
jar --create --file ./src/box.jar --main-class Box -C src Box.class
java -cp src Controller "$@"