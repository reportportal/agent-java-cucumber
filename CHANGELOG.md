# Changelog

## [Unreleased]
### Changed
- Some code refactoring to unify style between all cucumber agents
- Client version updated on [5.0.15](https://github.com/reportportal/client-java/releases/tag/5.0.15)

## [5.0.0]
### Changed
- Many static methods from Util class were moved to AbstractReporter class and made protected to ease extension 

## [5.0.0-RC-1]
### Added
- Callback reporting
### Changed
- Test step parameters handling
- Mime type processing for data embedding was improved
### Fixed
- Manually-reported nested steps now correctly fail all parents
### Removed
- Scenario Outline iteration number in item names, to not break re-runs

## [5.0.0-BETA-13]
### Fixed
- A bug whe ambiguous item cases a Null Pointer Exception
- Incorrect item type settings
### Added
- Nested steps support

## [5.0.0-BETA-12]
### Added
- multi-thread execution support
- Test Case ID support
### Fixed
- codeRef reporting was added for every item in an item tree

## [3.0.0]
### New Features 
- Migrate to 3x client generation
- Asynchronous reporting

## [2.6.1]
### Changed 
- access modifier to 'protected' for rootSuiteId in ScenarioReporter to allow inherit this reporter without unnecessary code duplication.

## [2.6.0]
### Released: 20 November 2016
### New Features
- Initial release to Public Maven Repositories
