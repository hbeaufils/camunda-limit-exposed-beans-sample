package org.camunda.bpm.spring.boot.example.autodeployment;

import org.springframework.stereotype.Component;

@Component
public class SomeBean {

    public void printMessage() {
        System.out.println("Expression bean: SomeBean");
    }
}
