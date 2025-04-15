# 🔍 ModuleChecker Maven Plugin

`ModuleChecker` est un plugin Maven conçu pour auditer les projets multi-modules en s'assurant que tous les modules attendus sont bien présents dans le système de fichiers **et** déclarés dans le `pom.xml` parent.

Il permet aussi d’intégrer d’autres checkers comme la détection de dépendances non utilisées (`UnusedDependenciesChecker`), les balises XML commentées, ou encore les propriétés redondantes dans les projets Maven complexes.

---

## ✨ Fonctionnalités principales

- ✅ Vérifie la présence des modules attendus (ex. `monprojet-api`, `monprojet-impl`, `monprojet-local`).
- 🧼 Détecte les dépendances potentiellement non utilisées dans le code Java.
- 🗒️ Génère des rapports formatés (Markdown, HTML, etc.) via un système de `renderer`.
- 🧩 Contribue à la standardisation des projets dans un contexte d'entreprise.

---

## 📦 Installation

Ajoute ce plugin dans ton `pom.xml` parent ou exécute-le via la CLI Maven :

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.example</groupId>
      <artifactId>modulechecker</artifactId>
      <version>1.0.0</version>
      <executions>
        <execution>
          <goals>
            <goal>check-modules</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>