# Release process

These are the tasks to do when performing a release:

 * Update the `JTPCCVERSION` constant about the version at
 `com.github.pgsqlio.benchmarksql.jtpcc.jTPCCConfig` class.
 * Update the `BUILDING.md` file, to update the version.
 * Update the `CHANGE-LOG.md` file with the modifications made in this release.
 * Format the Java code, according to Google Java code style.
For more details, please check the [Contributing section](CONTRIBUTING).
 * Execute the `release` plugin in Maven:
 
```
mvn release:prepare
mvn release:perform
```
 * Verify the new version in `pom.xml` file.
 * Generate the jar file, via `mvn`.
 * Publish the `BenchmarkSQL.jar` in GitHub releases.
 The file can be obtained from `target` directory.
 