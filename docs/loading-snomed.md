# Loading SNOMED CT

## Before starting

- Make sure that the data directory (`<elasticsearch home>/data`) has been emptied from any previous imports or starts as this will throw an exception (at the moment anyway).
- [Download SNOMED CT](http://www.snomed.org/), using the most recent International Edition (_Snowstorm has not yet tested with other editions_)

**Top Tip**

Unless you want to do a full import of SNOMED CT, which incorporates all previous versions and takes a significant number of hours, you should work with the snapshot of the curreent edition.

To do this, open the downloaded release zip file, remove all folders **apart from** the SNAPSHOT folder, and rebuild the zip file.

## Load SNOMED CT

Run the following command from the Snowstorm directory to import the data:

`java -Xms5g -Xmx5g -jar target/elastic-snomed-<version>.jar --clean-import=<SNOMED CT release zip file>`

This will take between 30-60 minutes depending on the performance of your machine/server.