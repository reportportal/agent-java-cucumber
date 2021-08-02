Feature: Pdf embedding feature

  Scenario: Embed a correct mime type pdf
    Given I have a dummy step to attach a pdf correct mime type

  Scenario: Embed an incorrect mime type pdf
    Given I have a dummy step to attach a pdf with incorrect mime type

  Scenario: Embed a partially correct mime type pdf
    Given I have a dummy step to attach a pdf with partially correct mime type