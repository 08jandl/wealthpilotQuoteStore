# Coding Conventions

Always think before following rules too strictly! 
Readability and simplicity is the highest goal for the source code.

## Business Logic

Should only be used in ```Service``` classes, never in ```Resource``` classes.

## Entity Saving

Always save newly created entities explicitly using the responsible service 
method to allow the business logic to apply security checks.

## Nullability

* Avoid returning ```null``` values in methods - return ```Optional``` instead!
* if you have to return ```null```, annotate method with ```@Nullable```
* until a ```@NonNullApi``` is reached 100%, use ```@NonNull``` annotations to 
indicate that a methods will never return ```null``` values. 

Repositories/Services packages should be ```@NonNullApi```.
