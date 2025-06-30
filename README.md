# jahrbuch-generator

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/jahrbuch-generator-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplify your persistence code for Hibernate ORM via the active record or the repository pattern
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC

## Provided Code

### Hibernate ORM

Create your first JPA entity

[Related guide section...](https://quarkus.io/guides/hibernate-orm)

[Related Hibernate with Panache section...](https://quarkus.io/guides/hibernate-orm-panache)


### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

Here's your content formatted in **Markdown (`.md`)**:

````markdown
## Avoid gphoto2 USB Automount

To avoid the automatic mounting of USB devices, including cameras, when using `gphoto2`, you can disable the automount feature in GNOME. Run `dconf-editor` and navigate to the section `org.gnome.desktop.media-handling` to disable the `automount` option. This will prevent all devices from being automatically mounted, including USB flash drives and other media devices.

Alternatively, you can use `gsettings` to disable `automount` and `automount-open` as follows:

```bash
gsettings set org.gnome.desktop.media-handling automount "false"
gsettings set org.gnome.desktop.media-handling automount-open "false"
````

To reverse these settings and enable automounting again, you can use:

```bash
gsettings set org.gnome.desktop.media-handling automount "true"
gsettings set org.gnome.desktop.media-handling automount-open "true"
```

This method provides a way to control the automount behavior without affecting other devices.

If you specifically want to target a camera or a specific USB device, you might need to use `udev` rules, but this can be complex and may affect other devices as well.

> **Note:** KI-generierte Antwort. Verifizieren Sie die Fakten.

```

Let me know if you need anything else in Markdown or have any other questions!
```

Natürlich! Hier ist die Information als **Markdown** formatiert:

# Einstellen der Idle-Zeit – Canon EOS M50 Mark II

Um den Zeitpunkt einzustellen, zu dem der **Bildschirm**, die **Kamera** und der **Sucher** der Canon EOS M50 Mark II automatisch ausgeschaltet werden, nachdem die Kamera im Ruhezustand war, gehen Sie wie folgt vor:

## Schritte zur Einstellung der automatischen Abschaltung

1. Wählen Sie im Menü die Option `[: Stromsparmodus]` aus.
2. Wählen Sie eine der verfügbaren Optionen, um den Zeitpunkt der automatischen Abschaltung festzulegen.

> **Hinweis:**  
> Die Einstellungen `["Display Aus"]` und `["Autom. Absch."]` haben **keine Auswirkung**, wenn `["Eco-Modus"]` auf `["Ein"]` eingestellt ist.

---

## Energiesparmodus deaktivieren oder anpassen

1. Öffnen Sie im Menü die Option `[: Stromsparmodus]`.
2. Deaktivieren Sie die Option `["Autom. Absch."]`, wenn Sie möchten, dass die Kamera **nicht automatisch abschaltet**.
3. Alternativ können Sie eine andere Zeitoption für `["Autom. Absch."]` wählen, um die automatische Abschaltung zeitlich anzupassen.

> **Achtung:**  
> Die Funktionen `["Display Aus"]` und `["Autom. Absch."]` **wirken auch dann**, wenn `["Eco-Modus"]` **aktiviert** ist.

---

*KI-generierte Antwort. Verifizieren Sie die Fakten vor Anwendung.*

Wenn du möchtest, kann ich dir das auch in HTML oder als PDF vorbereiten.

