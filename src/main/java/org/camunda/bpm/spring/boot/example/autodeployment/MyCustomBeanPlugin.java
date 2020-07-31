package org.camunda.bpm.spring.boot.example.autodeployment;

import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

public class MyCustomBeanPlugin extends AbstractProcessEnginePlugin {

    @Override
    public void postInit(ProcessEngineConfigurationImpl conf) {

        conf.getBeans().put("someBean", new MyCustomScriptBean());
        conf.getBeans().put("someBeanWithADifferentName", new MyCustomScriptBean());
    }
}
