Feature: Basic test with a docstring parameter

  Scenario: Test with a docstring parameter
    Given It is test with parameters
    When I have a docstring parameter:
    """
    My very long parameter
    With some new lines
    """
