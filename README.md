# pattern-blacklist

## Introduction

The **pattern-blacklist** decides for a given phone number if 'spam' or 'ham', depending on a blacklist.
The text-based blacklist supports patterns. The software is addressed via HTTP REST call.

## Quick start

Create a pattern file (the 'blacklist'):

```shell script
mkdir pattern-blacklist
cat >> pattern-blacklist/patterns.txt << EOF
[spam]
555-9*
[ham]
555-1234
EOF
```

and then start the container with:

```
$ docker run --rm \
    -p 8080:8080 \
    -v ${PWD}/pattern-blacklist:/opt/pattern-blacklist \
    ghcr.io/nwaldispuehl/pattern-blacklist:latest
```

You can then query the service for numbers. The result is returned as plain text:

    $ curl http://localhost:8080/check/+555-9876
    SPAM

    $ curl http://localhost:8080/check/+555-1234
    HAM

## How to use

### Functionality

Upon querying the `check/` endpoint of the server with a phone number, for example like this:

    $ curl http://localhost:8080/check/+555-9876
    SPAM

the software returns a plain text result depending on its findings:

- `HAM`: the number matched with a pattern from the `[ham]` section.
- `SPAM`: the number matched with a pattern from the `[spam]` section, or with any pattern if no sections are present.
- `UNKNOWN`: the number did not match any pattern of the blacklist.

ℹ️ All special characters of the query (like `+` or `-`) are ignored; `+555-9876` is thus equivalent to `5559876`.

### Configuration

The server needs a list of patterns which is by default provided by the `/opt/pattern-blacklist/patterns.txt` file.
This can be customized with the `pattern.file.path` argument, as used as this:

    $ ./pattern-blacklist -Dpattern.file.path=...

or with the `PATTERN_FILE_PATH` environment variable, for example like this:

    $ PATTERN_FILE_PATH="..." ./pattern-blacklist

With containers, provide own patterns by creating a volume for this file.

ℹ️ The pattern file is monitored and reloaded upon change.

### Pattern file

The pattern file is a text file containing a list of patterns subdivided in two sections, spam (undesired) and ham (desired). 
Every line holds a section marker (e.g. `[spam]`), a pattern (e.g. `555-9*`), or a comment (line starts with a `#`).

ℹ️ All characters which are not numbers or pattern placeholders are ignored.

Example:

```
[spam]

# That range where all used car dealerships have their numbers
555-9*

[ham]

# My good friend, John
555-1234
```

#### Sections

These two sections are supported. All entries below a section marker belong to the respective category, spam or ham. 
The order is irrelevant.

- `[spam]`
- `[ham]`

ℹ️ If the pattern file has no section markers, all patterns are considered spam.

#### Supported patterns

- Numbers verbatim, e.g. `5551234` matches the number `+555-1234` (which is equivalent to `5551234`)
- `*`-Wildcard represents zero, one or more numbers, e.g. `555*` matches the number `+555-1234`, and also `+555-12` and `+555`
- `N`-Wildcard represents exactly one number, e.g. `55512NN` matches the number `+555-1234` but not `+555-12`

ℹ️ All characters which are not numbers or pattern placeholders are ignored.

### Run with Docker

To run a docker container of the project you can use this statement:

```
$ docker run --rm \
    -p 8080:8080 \
    -v /path/to/pattern-blacklist-directory:/opt/pattern-blacklist \
    ghcr.io/nwaldispuehl/pattern-blacklist:latest
```

⚠️ Note that some editors (most notably Vim) replace a file upon saving and thus change the file inode. The container volume loses track of the file this way. Mitigate by using the whole directory as volume.

### Run with Docker Compose

To run the project with Docker compose, you can use this sample docker-compose.yaml file:
```
services:
  pattern-blacklist:
    image: ghcr.io/nwaldispuehl/pattern-blacklist:latest
    ports:
      - 8080:8080
    volumes:
      - /path/to/pattern-blacklist-directory:/opt/pattern-blacklist
    restart: unless-stopped
```

⚠️ Note that some editors (most notably Vim) replace a file upon saving and thus change the file inode. The `pattern-blacklist` might lose track of the file this way.

## How to build

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

### Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew quarkusDev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

### Packaging and running the application

The application can be packaged to a native executable using:

```shell script
./gradlew quarkusBuild -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false
```

You can then execute your native executable with e.g.: `./build/pattern-blacklist-0.0.3-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.

### Run test

Run unit and integration tests with:

```shell script
./gradlew check
```

### Create docker image

See [Dockerfile.native-micro](src/main/docker/Dockerfile.native-micro) for directions. 
It boils down to calling the following line after having built and packaged the application (see above):

```
docker build -f src/main/docker/Dockerfile.native-micro -t pattern-blacklist .
```
