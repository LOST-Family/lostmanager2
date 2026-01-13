VS Code Setup für `lostmanager`

Kurz: Diese Einstellungen zielen darauf ab, das Projekt mit JDK 23 und Maven zu bauen.

1) JDK 23 installieren
- Lade und installiere JDK 23 (z.B. Adoptium/Eclipse Temurin). Installationspfad-Beispiel: `C:\Program Files\Java\jdk-23`.
- Setze `JAVA_HOME` auf den Installationspfad und füge `%JAVA_HOME%\\bin` zum `PATH` hinzu.

PowerShell-Beispiel (als Administrator anpassen):
```powershell
setx -m JAVA_HOME "C:\\Program Files\\Java\\jdk-23"
$env:Path = [System.Environment]::GetEnvironmentVariable('Path','Machine') + ";$env:JAVA_HOME\\bin"
```

2) Maven installieren
- Installiere Apache Maven oder nutze die System-Maven-Distribution. `mvn -v` sollte die Installation zeigen.

3) VS Code Einstellungen
- Die Datei `.vscode/settings.json` verwendet aktuell den Platzhalter-Pfad `C:\\Program Files\\Java\\jdk-23`.
- Passe den Pfad an falls dein JDK an einem anderen Ort liegt.

4) Build & Compile
- Projekt bauen (im Projekt-Ordner):
```powershell
mvn -f pom.xml clean package
```
- Nur kompilieren (ohne package):
```powershell
mvn -f pom.xml -DskipTests=true compile
```

5) Falls VS Code die JDK-Version nicht nutzt
- Öffne die Command Palette → `Java: Configure Java Runtime` und wähle die JDK-23-Installation aus oder passe `java.configuration.runtimes` in `.vscode/settings.json` an.

6) Erweiterungen
- Empfohlen: Java Extension Pack, Maven for Java, Debugger for Java. Siehe `.vscode/extensions.json`.
