# BlockSY guestbook server

This is the source code repository of BlockSY Guestbook demonstrator server.
You can test a live version of the Guestbook here: https://blocksy-demo.symag.com/symag/

You can get more informations about BlockSY here : https://blocksy-wiki.symag.com

## install

In order to build the source you need to install:
- Java 8 SE SDK

## Change guestbook settings
First you need to specify guestbook settings here :
```
server\grails-app\conf\application.yml
```

## build and launch server locally
```
gradlew server:bootRun
```

## Once started,test by opening server root from browser :
```
http://localhost:8080
```

## build the production final .jar archive
```
gradlew server:jar
```

## start the server in production
```
java -jar build/server-x.jar
```
