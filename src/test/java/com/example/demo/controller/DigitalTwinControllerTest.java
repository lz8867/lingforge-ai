package com.example.demo.controller;

import com.example.demo.service.DigitalTwinControllerFixture;
import com.example.demo.service.DigitalTwinOverview;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DigitalTwinControllerTest {

    @Test
    void overviewEndpointShouldReturnDigitalTwinOverview() {
        DigitalTwinController controller = new DigitalTwinController(DigitalTwinControllerFixture.service());

        DigitalTwinOverview overview = controller.overview();

        assertTrue(overview.healthScore() > 0);
        assertTrue(overview.nodes().stream().anyMatch(node -> "service-runtime".equals(node.id())));
        assertTrue(overview.relations().stream().anyMatch(relation -> relation.label().contains("依赖")));
    }

    @Test
    void controllerShouldBeCreatedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DigitalTwinController.class, () -> new DigitalTwinController(DigitalTwinControllerFixture.service()));

            context.refresh();

            assertTrue(context.getBean(DigitalTwinController.class).overview().totalCapabilities() > 0);
        }
    }
}
