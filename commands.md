HELP
```
mvn org.elitost.maven.plugins:checker-maven-plugin:help -Ddetail=true
```

expectedModules
```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=expectedModules
```

parentVersion
```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=parentVersion
```

propertyPresence
```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=propertyPresence -DpropertiesToCheck=toto,bouzey,component.name
```

redefinedDependencyVersion
```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=redefinedDependencyVersion -X
```

hardcodedVersion
```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=hardcodedVersion
```

outdatedDependencies

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=outdatedDependencies
```

commentedTags

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=commentedTags
```

redundantProperties

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=redundantProperties
```

unusedDependencies

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=unusedDependencies
```

urls

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=urls
```

interfaceConformity

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DcheckersToRun=interfaceConformity
```


ALL

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DpropertiesToCheck=toto -Dformat=html


mvn org.elitost.maven.plugins:checker-maven-plugin:check -DpropertiesToCheck=toto -Dformat=markdown

mvn org.elitost.maven.plugins:checker-maven-plugin:check -DpropertiesToCheck=toto -Dformat=text

mvn org.elitost.maven.plugins:checker-maven-plugin:check -DpropertiesToCheck=component.name,component.id -Dformat=html,markdown,text -Dverbose=true
```

ALL

```
mvn org.elitost.maven.plugins:checker-maven-plugin:check -DpropertiesToCheck=toto -DoutputPath=target/rapports/custom-report.html

```



