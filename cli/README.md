# Ion Schema CLI

```
Usage: ion-schema-cli [OPTIONS] COMMAND [ARGS]...

Options:
  --version   Show the version and exit
  -h, --help  Show this message and exit

Commands:
  no-op
```


# Building the CLI

The CLI is built during the main Gradle build.  To build it separately execute:

```
./gradlew :cli:build
```

After building, distributable archive files are located in the `cli/build/distributions` directory (relative to the
project root).

# Using the CLI

The following command will build any dependencies before starting the CLI.

```
./gradlew :cli:run -q --args="<command line arguments>"
```

# Commands

TODO—this section will contain details about the ion-schema-cli subcommands once they have been added.
