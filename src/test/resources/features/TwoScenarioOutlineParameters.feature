Feature: Test with two parameters

  Scenario Outline: Test with few parameter in method
    Given I have <number> <item> in my pocket
    When I eat one
    Then I have <result> in my pocket

    Examples:
      | number | item      | result |
      | 100    | apples    | 99     |
      | 3      | cucumbers | 2      |
      | 1      | cake      | 0      |
