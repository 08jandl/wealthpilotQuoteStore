# Architecture wealthpilot

## Layer Overview

The application follows the three tier architecture:

* Presentation (web application)
* Business Logic (Java Spring Boot application)
* Data Storage (relational database) 

### Presentation Layer

The presentation layer is built as an AngularJS application, which can be found at `src/main/webapp`. 
It follows the Model-View-Controller pattern:

* Model: holding data to be visualized in the view
    * mostly data retrieved from RESTful endpoints
    * may also be data directly modified in the frontend 
* Controller: client side business/application logic
    * calls `Services` to retrieve data and assigns it to the model (mostly `vm.variableName`)
    * performs business logic calculations and assigns result to model
* View: html page
    * a template file that displays data assigned by the controller

Additional layers on the client side are

* Service: mostly responsible for reading and writing from/to the server backend using `$resource` (HTTP). There are also some services (and util classes)
  which add some business logic in the front end - where applicable.
* State: Angular component to map different URL states (e.g. create/delete/display objects of a specific type) 
  to their corresponding view and controller. Furthermore state transitions are defined.
  
#### Controllers
A controller is responsible to assigning data to its (view-)model so it can be displayed in the view later on. The (view-)model is a reference
to the controller itself, therefore every controller contains a line `const vm = this`. All variable assignments must be performed at the beginning
of the controller (e.g. `vm.stamps = []`). 

The assigned data can be 
* retrieved data from the server
* generated data within the controller 
* functions 

By setting `controllerAs: 'vm'` in the corresponding State, these assigned properties of `vm` can be then retrieved in the view. Therefore, the view
can call controller functions like `vm.save()` or use variables like `vm.stams`. The controller functions then may apply State transitions (so one is
redirected from the save dialog to the overview list for example).

#### Views
Angular views (also called templates) are defined in HTML files and are used in the Controllers. A view is kind of an enhanced HTML file. So you may use a special syntax to "inject"
information from the controller into the HTML: `{{ vm.userName }}` This displays the property `userName` of the `vm` object (which was previously assigned by the controller).

Furthermore there are special HTML tags and attributes (called Directives). They normally start with `ng-`; for example the attribute `ng-show` leads to a DOM element only be
shown, when the condition is true.
  
#### State
A state defines a logical combination of an URL (pattern), and which controller and HTML view should be called.
Here is an example:
```javascript
function stateConfig($stateProvider) {
        $stateProvider.state('stamp', {
            parent: 'entity',
            url: '/stamp',
            data: {
                authorities: ['ROLE_USER', 'ROLE_ADMIN'],
                pageTitle: 'pwpApp.stamp.home.title'
            },
            views: {
                "content@root": {
                    template: require('./stamps.html'),
                    controller: 'StampController',
                    controllerAs: 'vm'
                }
            }
        });
    }
```

This means, that the URL pattern /stamp leads to a `stamp.html` view being loaded and the `StampController` being run.
Furthermore, it is possible to load entities directly using states; they are added in a `resolve` block in the state definition. Then they are
loaded before the controller is instantiated which then assigns data to be displayed by the view.

#### Services
AngularJS services are mostly used to fetch data from the RESTful backend using [AngularJS resource](https://docs.angularjs.org/api/ngResource/service/$resource).
```javascript
function Stamp($resource) {
    const resourceUrl = 'api/stamps/:id';
    
    return $resource(resourceUrl, {}, {
        'get': { method: 'GET' },
        'save': { method: 'POST' },
        'query': { method: 'GET', isArray: true },
        'remove': { method: 'DELETE' },
        'delete': { method: 'DELETE' }
    });
}
```

Here you see, that service "classes" for backend interactions are defined using "string" method names. Actually every service defined using `$resource` always
contains predefined REST methods; the methods which are shown in the code example are all predefined and need not to be added in real code.
As a service can contain more information, ie. non predefined methods or transformations, it may still make sense to define such methods.

In addition to providing interfaces to communicate with the backend via http, services are also used to create util tools:

```javascript
function ArrayUtils () {
    return {
        flattenArray: flattenArray
    };

    function flattenArray(arrayWithArrays) {
        let flatArray = [];
        for(let i = 0; i < arrayWithArrays.length; i++) {
            flatArray = flatArray.concat(arrayWithArrays[i]);
        }
        return flatArray;
    }
}
```

#### Unit Tests
Client unit tests can be found under `src/test/javascript/spec` and are executed with `gulp test`.

### Business Logic

The business logic is a Spring Boot Application. For the moment, the application is a single monolith - this 
might change in the (near) future.

The layers in the business logic are:

* Resource: handle REST-API calls and check for user roles (e.g. USER, ADMIN, COUNSELOR, ...)
* Services: business logic and row level security (is user A allowed to read the wanted instance of e.g. bank connection.
* Repository: [JPA repository](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/) - read and write java objects to the database
  * Hibernate/JPA Entities: finally maps Java objects to SQL statements
  * [Liquibase](https://www.liquibase.org/): deploys changes in database tables; adds (test) data to database 

#### Resource

The resource layer is responsible for accepting REST calls from a client, forwarding the calls to the appropriate 
business logic and return the requested objects.

Communication between the Java backend and the AngularJS frontend is done via a REST-API. Only data transfer objects (DTOs)
must be serialized from Java to Javascript! No Java entities must be sent across the network. This allows a very fine
selection of entity properties to be sent to the frontend. Sometimes multiple variations of DTOs exist for one
entity class - depending on the use case the data is used (e.g. UserDTO, ManagedUserDTO).

Serialization from Java objects (DTOs) to JSON is done automatically by Spring/Jackson library using the `ObjectMapper` class, see also `com.wealthpilot.configuration.JacksonConfiguration`.

A simple example of a resource method to deliver a StampDTO with a given id (only usable for users with the role USER):

```java
@RequestMapping(value = "/stamps/{id}", method = RequestMethod.GET)
@RolesAllowed({ USER })
public ResponseEntity<StampDTO> getStamp(@PathVariable Long id) {
    log.debug("REST request to get Stamp: {}", id);
    return ResponseEntity.of(stampService.findOne(id));
}
```

Most of the magic of the REST api is configured by using annotations. 
See [Baeldung - Spring RequestMapping](https://www.baeldung.com/spring-requestmapping) for details.

Please note that spring will be unwilling to find the correct Java method for a given REST call when 
using e.g. the wrong HTTP method (GET instead of POST) or the wrong number of parameters!

#### Service

The service layer handles the business logic and some row level security (if needed). The following example
checks if the current user is an admin, otherwise only Stamp objects that belong to the current user 
will be returned. Actually, not the entity but the DTO is returned.

```java
@Transactional(readOnly = true)
public Optional<StampDTO> findOne(Long id) {
    if (userService.isCurrentUserAdmin()) {
        return stampRepository.findById(id).map(stampMapper::stampToStampDTO);
    }
    return stampRepository.findByIdAndOwner(id, userService.getCurrentUser()).map(stampMapper::stampToStampDTO);
}
```

We have many services already defined for basic use cases. Thus we must not "reinvent the wheel" but combine the existing services together.
Therefore Spring can 'inject' one service into another one.

##### Mapping Entities to Data Transfer Objects (DTOs)

The conversion from entities to DTOs is done by a [MapStruct Mapper](https://mapstruct.org/). Mapstruct creates
the source code needed for conversion (mostly) automatically - only the interface of the mapping methods and some 
hints in the form of annotations need to be given by the developer:

```java 
/**
 * Mapper for the entity Stamp and its DTO StampDTO
 */
@Mapper(componentModel = "spring", uses = { UserMapper.class })
public interface StampMapper {
    @Mapping(source = "owner.id", target = "ownerId")
    @Mapping(target = "customerName", ignore = true)
    StampDTO stampToStampDTO(Stamp stamp);

    @Mapping(source = "ownerId", target = "owner")
    Stamp stampDTOToStamp(StampDTO stampDTO);

    List<StampDTO> stampsToStampDTOs(List<Stamp> stamps);
}
``` 

#### Repository

The JPA repository implementation is created by Spring automatically. Basic CRUD operations are inherited 
from a base class (like ```findOne(id)``` or ```save(entity)```). More advanced (query) methods are created 
by the developer only by using a naming scheme for methods (like ```findAllByOwnerSortByIdAsc()```) and spring / Hibernate
will create the database query logic in the background at runtime. 

```java
public interface StampRepository extends JpaRepository<Stamp, Long> {
    List<Stamp> findAllByOwner(User owner);

    Optional<Stamp> findByIdAndOwner(Long id, User owner);
}
```

### Data Storage

wealthpilot application uses a relational database to persist the data.
 
PostgreSQL is used in production, H2 as a small and fast database for development.
No product specific code like SQL must be used to allow database independent deployment!

Business logic uses JDBC to communicate with the database.
