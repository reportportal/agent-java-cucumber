# Changelog

## [Unreleased]
### Added
- Common Stack Trace frames skip in description and logs, by @HardNorth
- Reporting of Last Error Log in Item description, by @HardNorth and @ArtemOAS
### Changed
- Client version updated on [5.2.21](https://github.com/reportportal/client-java/releases/tag/5.2.21), by @HardNorth

## [5.2.3]
### Changed
- Client version updated on [5.2.13](https://github.com/reportportal/client-java/releases/tag/5.2.13), by @HardNorth

## [5.2.2]
### Changed
- Client version updated on [5.2.11](https://github.com/reportportal/client-java/releases/tag/5.2.11), by @HardNorth
### Removed
- `OkHttp` dependency, by @HardNorth

## [5.2.1]
### Changed
- Client version updated on [5.2.4](https://github.com/reportportal/client-java/releases/tag/5.2.4), by @HardNorth
### Removed
- `commons-model` dependency to rely on `clinet-java` exclusions in security fixes, by @HardNorth

## [5.2.0]
### Changed
- Client version updated on [5.2.0](https://github.com/reportportal/client-java/releases/tag/5.2.0), by @HardNorth

## [5.1.4]
### Changed
- Client version updated on [5.1.22](https://github.com/reportportal/client-java/releases/tag/5.1.22), by @HardNorth

## [5.1.3]
### Changed
- Client version updated on [5.1.16](https://github.com/reportportal/client-java/releases/tag/5.1.16), by @HardNorth

## [5.1.2]
### Added
- Test Case ID templating, by @HardNorth
### Changed
- Client version updated on [5.1.9](https://github.com/reportportal/client-java/releases/tag/5.1.9), by @HardNorth
- Slf4j version updated on 1.7.36, by @HardNorth

## [5.1.1]
### Fixed
- Invalid Data Tables format for some Report Portal versions
### Changed
- Client version updated on [5.1.4](https://github.com/reportportal/client-java/releases/tag/5.1.4)
- Slf4j version updated on 1.7.32 to support newer versions of Logback with security fixes

## [5.1.0]
### Added
- `startHook` method
### Changed
- Version promoted to stable release
- Client version updated on [5.1.0](https://github.com/reportportal/client-java/releases/tag/5.1.0)

## [5.1.0-RC-5]
### Added
- Feature / Scenario / Step start methods which are overridable
- JSR-305 annotations
- `buildFinishTestItemRequest` overridable method

## [5.1.0-RC-4]
### Changed
- Client version updated on [5.1.0-RC-12](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-12)

## [5.1.0-RC-3]
### Fixed
- Cucumber-groovy crash

## [5.1.0-RC-2]
### Changed
- Client version updated on [5.1.0-RC-6](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-6)

## [5.1.0-RC-1]
### Changed
- Client version updated on [5.1.0-RC-4](https://github.com/reportportal/client-java/releases/tag/5.1.0-RC-4)
- Version changed on 5.1.0

## [5.0.2]
### Changed
- Client version updated on [5.0.21](https://github.com/reportportal/client-java/releases/tag/5.0.21)
### Fixed
- Empty interrupted suite in case of duplicate step
- Table parameter handling for different reporters

## [5.0.1]
### Changed
- Some code refactoring to unify style between all cucumber agents
- Client version updated on [5.0.15](https://github.com/reportportal/client-java/releases/tag/5.0.15)
### Fixed
- Double error message reporting

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
