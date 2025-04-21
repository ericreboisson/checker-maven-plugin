
# 🧩 checker-maven-plugin

> Plugin Maven extensible pour l’audit automatisé de projets multi-modules

Ce plugin Maven analyse les projets Java multi-modules selon des règles personnalisées (checkers) et génère un rapport synthétique en Markdown, HTML ou texte brut.

---

## ✨ Fonctionnalités

- 🔍 Vérification automatique de bonnes pratiques dans les `pom.xml`
- 🧱 Chargement dynamique des règles d’analyse via le SPI Java
- 📄 Génération de rapports multi-formats (Markdown, HTML, texte)
- 🧪 Extensible : ajoutez vos propres checkers personnalisés
- ✅ Filtrage des règles exécutées via CLI ou `pom.xml`

---

## 🚀 Installation

Ajoutez dans le `pom.xml` parent de votre projet :

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.elitost.maven.plugins</groupId>
      <artifactId>checker-maven-plugin</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

---

## ▶️ Exécution

```bash
mvn org.elitost.maven.plugins:checker-maven-plugin:check
```

Ou directement via une configuration dans le plugin management.

---

## ⚙️ Paramètres

| Paramètre              | Description                                                                   | Exemple                                           |
|------------------------|-------------------------------------------------------------------------------|---------------------------------------------------|
| `-Dformat`             | Format du rapport généré : `html`, `markdown`, `text`                         | `-Dformat=html`                                   |
| `-DcheckersToRun`      | Liste des identifiants des checkers à exécuter (séparés par `,`)              | `-DcheckersToRun=expectedModules,hardcodedVersion` |
| `-DpropertiesToCheck`  | Propriétés spécifiques à vérifier dans les `pom.xml`                          | `-DpropertiesToCheck=java.version,encoding`       |

---

## ✅ Liste des checkers intégrés

| ID                         | Description                                                               |
|----------------------------|---------------------------------------------------------------------------|
| `expectedModules`          | Vérifie la présence et la déclaration des modules conventionnels (`-api`, `-impl`) |
| `propertyPresence`         | Vérifie que certaines propriétés clés sont bien définies (`java.version`, etc.) |
| `hardcodedVersion`         | Détecte les dépendances avec versions codées en dur                        |
| `outdatedDependencies`     | Vérifie si certaines dépendances sont obsolètes                            |
| `commentedTags`            | Signale les balises XML Maven importantes commentées (`<dependencies>`, `<build>`, ...) |
| `redundantProperties`      | Liste les propriétés déclarées mais jamais utilisées dans aucun `pom.xml` |
| `unusedDependencies`       | Analyse le code Java pour détecter les dépendances non utilisées          |
| `url`                      | Vérifie la balise `<url>` du `pom.xml`, sa présence, sa sécurité (HTTPS) et sa disponibilité |
| `redefinedDependencyVersion` | Signale les dépendances dont la version redéfinit inutilement celle du `dependencyManagement` |
| `interfaceConformity`      | Vérifie que toutes les interfaces publiques d’un module `-api` sont testées via `ClassInspector.logClassName(...)` |

---

## 🧩 Ajouter vos propres checkers

Vous pouvez enrichir ce plugin avec vos propres règles (checkers) :

1. Implémentez l’interface `CustomChecker`
2. Implémentez si nécessaire `BasicInitializableChecker` ou `InitializableChecker`
3. Déclarez votre classe dans `META-INF/services/org.elitost.maven.plugins.checkers.CustomChecker`
4. Ajoutez votre module checker dans le classpath du plugin
