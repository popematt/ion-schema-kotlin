
To generate the code from the sample schema (and to get the tests to compile), run this command from the project root directory:
```shell
./ion-schema-cli generate kotlin -a "./cli/src/test/resources/SampleSchemaForCodeGeneration/isl/" -o cli/build/generated-sources/test/kotlin -p com.amazon.kitchen
```
