Feature: Basic test with parameters

  Scenario: The scenario
    Given I have empty step
    Then I have another empty step

  Scenario Outline: Test with different parameters
    Given It is test with parameters
    When I have parameter <str>
    Then I emit number <parameters> on level info

    Examples:
      | str    | parameters |
      | first  | 123        |