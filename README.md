# springboot-runnable-jar

Simple Spring Boot application that reads a table and writes to a file.

---

## Does it produce a runnable jar?

**Yes.** The `spring-boot-maven-plugin` is declared in `pom.xml` and repackages the project into a self-contained executable ("fat") jar during `mvn package`. No extra configuration is needed.

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| Java | 17             |
| Maven | 3.6+          |

---

## Build

```bash
mvn package -DskipTests
```

The executable jar is written to `target/springboot-runnable-jar-0.0.1-SNAPSHOT.jar`.

To also run the test suite:

```bash
mvn package
```

---

## Run

### Syntax

```bash
java -jar target/springboot-runnable-jar-0.0.1-SNAPSHOT.jar <YYYYMMDD> [options]
```

### Required argument

| Form | Description |
|------|-------------|
| `20240115` (positional) | Date parameter in `YYYYMMDD` format |
| `--export.param=20240115` (named) | Same parameter as a named option |

### Optional arguments

| Option | Description | Default |
|--------|-------------|---------|
| `--export.outputDir=<path>` | Directory where the output file is written | `app.output.directory` from `application.properties` (`/tmp/export-output`) |
| `--app.output.filename=<template>` | Filename template; `{param}` is replaced at runtime | `export_{param}.dat` |
| `--app.error.directory=<path>` | Directory where failed partial files are moved | `/tmp/export-error` |
| `--app.filter.status=<value>` | Status value used to filter the `person` table | `ACTIVE` |

Any Spring Boot property can be overridden on the command line with the `--key=value` syntax.

### Examples

```bash
# Minimal – positional date argument
java -jar target/springboot-runnable-jar-0.0.1-SNAPSHOT.jar 20240115

# Named date argument
java -jar target/springboot-runnable-jar-0.0.1-SNAPSHOT.jar --export.param=20240115

# Override output directory
java -jar target/springboot-runnable-jar-0.0.1-SNAPSHOT.jar --export.param=20240115 \
     --export.outputDir=/data/out

# Override multiple properties
java -jar target/springboot-runnable-jar-0.0.1-SNAPSHOT.jar --export.param=20240115 \
     --export.outputDir=/data/out \
     --app.filter.status=INACTIVE \
     --app.output.filename=export_{param}_inactive.dat

# Point to an external properties file instead of the bundled one
java -jar target/springboot-runnable-jar-0.0.1-SNAPSHOT.jar --export.param=20240115 \
     --spring.config.location=/etc/myapp/application.properties
```

### Exit codes

| Code | Meaning |
|------|---------|
| `0`  | Export completed successfully |
| `1`  | Export failed (exception logged; partial file moved to error directory) |

---

## Configuration reference

All properties live in `src/main/resources/application.properties` and can be overridden at runtime.

| Property | Default | Description |
|----------|---------|-------------|
| `app.output.directory` | `/tmp/export-output` | Output directory (created if absent) |
| `app.output.filename` | `export_{param}.dat` | Output filename template |
| `app.error.directory` | `/tmp/export-error` | Error directory for failed files |
| `app.filter.status` | `ACTIVE` | Status value used to filter the `person` table |
| `spring.datasource.url` | H2 file `./data/exportdb` | JDBC URL – swap for a real DB in production |
