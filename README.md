# Camunda Webapps - Sample to demonstrate the ability to limit Spring beans exposure in Groovy scripts

This example demonstrates how Spring beans are exposed in Groovy scripts, and that it's not possible to prevent their 
exposure in scripts or expressions using the official documentation method.

See [Limit the Exposing Spring Beans in Expressions](https://docs.camunda.org/manual/latest/user-guide/spring-framework-integration/expressions/#limit-the-exposing-spring-beans-in-expressions).

## Run the application and use Camunda Webapps

You can build the application with `mvn clean install` and then run it with `java -jar` command.

Then you can access Camunda Webapps in browser: `http://localhost:8080` (provide login/password from `application.yaml`, default: demo/demo)

## Bug demonstration

### Start a process instance

1. Go to the TaskList app at [http://localhost:8280/camunda/app/tasklist/default/](http://localhost:8280/camunda/app/tasklist/default/)
2. Click the "Start Process" button
3. Select the "Sample" process definition
4. Click the "Start" button
5. Check the application logs

### Expected result

Since `MyCustomBeanPlugin` configures a specific map of beans in the `bean` property, we expect the Spring beans to be 
completely unavailable from scripts / expressions, to stay consistent with the way this feature works in expressions,
as stated by the [documentation](https://docs.camunda.org/manual/latest/user-guide/spring-framework-integration/expressions/#limit-the-exposing-spring-beans-in-expressions).

We also expect expressions to use `MyCustomScriptBean` when referring to `someBean`, instead of the contextual `SomeBean` Spring component.

We should see the following lines:

```
Bean class: org.camunda.bpm.spring.boot.example.autodeployment.MyCustomScriptBean
Bean class (when using a different name): org.camunda.bpm.spring.boot.example.autodeployment.MyCustomScriptBean
Expression bean: MyCustomScriptBean
```

### Result

We can see the following lines:

```
Bean class: org.camunda.bpm.spring.boot.example.autodeployment.SomeBean
Bean class (when using a different name): org.camunda.bpm.spring.boot.example.autodeployment.MyCustomScriptBean
Expression bean: SomeBean
```

It indicates that the bean `someBean` which have been accessed through the Groovy script is actually the Spring Component, instead
of the custom `MyCustomScriptBean`.

The other one, which had a different name, was also accessible through the `someBeanWithADifferentName` variable, and we
can see that it has the correct class.

And finally, despite what written in the documentation, the expression doesn't use our custom bean either!

### Problem analyze

It seems that, from Camunda 7.12, the `SpringProcessEngineConfiguration` adds a `SpringBeansResolverFactory` which is used
by the script engines to setup the bindings:

```java
package org.camunda.bpm.engine.spring;

// ...

public class SpringProcessEngineConfiguration extends SpringTransactionsProcessEngineConfiguration implements ApplicationContextAware {
    
    // ...

    protected void initScripting() {
        super.initScripting();
        this.getResolverFactories().add(new SpringBeansResolverFactory(this.applicationContext));
    }
}
```

This resolver factory is added at the end of the list of factories, and thus, in the `ScriptBindings#get(...)` method,
it overrides all custom beans:

```java
package org.camunda.bpm.engine.impl.scripting.engine;

// ...

public class ScriptBindings implements Bindings {

  // ...

  public Object get(Object key) {
    Object result = null;

    if(wrappedBindings.containsKey(key)) {
      result = wrappedBindings.get(key);

    } else {
      for (Resolver scriptResolver: scriptResolvers) {
        if (scriptResolver.containsKey(key)) {
          result = scriptResolver.get(key);
        }
      }
    }

    return result;
  }
  
  // ...
}
```

Other resolver factories are the following:

* `MocksResolverFactory`
* `VariableScopeResolverFactory`
* `BeansResolverFactory`

They are initialized in the `ProcessEngineConfigurationImpl#initScripting()` method:

```java
package org.camunda.bpm.engine.impl.cfg;

// ...

public abstract class ProcessEngineConfigurationImpl extends ProcessEngineConfiguration {

  // ...

  protected void initScripting() {
    if (resolverFactories == null) {
      resolverFactories = new ArrayList<>();
      resolverFactories.add(new MocksResolverFactory());
      resolverFactories.add(new VariableScopeResolverFactory());
      resolverFactories.add(new BeansResolverFactory());
    }
    if (scriptingEngines == null) {
      scriptingEngines = new ScriptingEngines(new ScriptBindingsFactory(resolverFactories));
      scriptingEngines.setEnableScriptEngineCaching(enableScriptEngineCaching);
    }
    if (scriptFactory == null) {
      scriptFactory = new ScriptFactory();
    }
    if (scriptEnvResolvers == null) {
      scriptEnvResolvers = new ArrayList<>();
    }
    if (scriptingEnvironment == null) {
      scriptingEnvironment = new ScriptingEnvironment(scriptFactory, scriptEnvResolvers, scriptingEngines);
    }
  }

  // ...
}
```

### Hack to bypass this problem

It is still possible to completely remove the `SpringBeansResolverFactory` which has been introduced in 7.12.
You can do so by declaring a process engine plugin with a `#postInit(...)` method like this:

```java
package com.quicksign.service.camunda.plugin.bean;

// ...

public class MyCustomBeanPlugin extends AbstractProcessEnginePlugin {

    @Override
    public void postInit(ProcessEngineConfigurationImpl conf) {

        conf.getBeans().put("someBean", new MyCustomScriptBean());
        conf.getBeans().put("someBeanWithADifferentName", new MyCustomScriptBean());

        List<ResolverFactory> resolverFactories = conf
                .getScriptingEngines()
                .getScriptBindingsFactory()
                .getResolverFactories();

        resolverFactories.removeIf(factory -> factory instanceof SpringBeansResolverFactory);
    }
}
```

## Conclusion

It seems there's a problem in the way beans resolution is done, since we should at least be able to override contextual
beans which can be accessed by scripts and expressions. Also, being able to completely prevent Spring beans access from
scripts and expressions seems to be an essential feature to keep, as it might be unsafe to expose all the application beans.
