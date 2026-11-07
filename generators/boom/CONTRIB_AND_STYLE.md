Contributions welcome!
If you'd like to help but don't know where to start, check out the TODO file or the GitHub issues.

**Bug Reports**

Good bug reports are invaluable!
Please try to provide reproducible code snippets and try to verify the behavior differs from the Spike RISC-V ISA simulator.

**Feature Contributions**

We recommend that you consider contacting us before implementing your ideas.
This will give you early feedback on the likelihood that we will be able to accept your changes, that you will not conflict with existing efforts, and will give you guidance that will help save you time on your own implementation.

We also recommend that you try to cut up contributions into smaller, digestable pieces that are easier-to-review and easier-to-maintain.

**Style Guide**

You can invoke `make checkstyle` to verify that some of your changes respect the style guide.
However, checkstyle does not cover all cases of the code.

We have the following coding guidelines:

* Scala variables should be in lowercase camel case
    * E.g. `localScalaVariableName`
* Chisel variables should be in lowercase snake case
    * E.g. `local_chisel_variable_name`
* Classes should be in camel case with the first letter capitalized
    * E.g. `RegisterFile`
* If the variable is in a particular stage, please put the stage cycle it is associated with
    * E.g. `val f2_valid = Wire(...)`
* Braces for control structures (`for`, `while`, `if`, `when`, etc...) and functions should be on the same line as the condition
    * E.g. `for (i <- something) {`
    * E.g. `def foo(): Type = {`
* Braces for classes and objects should be on the next line
    * ```
      class SomeModule extends Module
      {
      object SomeObject
      {
      ```
* Things such as `else`, `.otherwise`, etc should be on the same line as the closing bracket of the previous scope
    * ```
      if (...) {
        ...
      } else {
      ```
* Braces following conditions should have a space separating them
    * E.g. `if (...) {`
* Indentation should be two spaces (NO TABS!)...
    * ```
      if (...) {
        if (...) {
          if (...) {
            ...
          }
        }
      }
      class SomeModule extends Module
      {
        if (...) {
      ```
* Multiline comments within the code should be indented on the second line associated with it
    * ```
      // this is a start of a comment that
      //   overflows to the next line that
      //   overflows for the final time
      ```
* To denote a new section of the code that needs to be separated with a comment, use the following format
    * ```
      //----------------------------
      // new section doing something
      //----------------------------
      ```
